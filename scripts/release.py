#!/usr/bin/env python3
import os
import sys

# Ensure UTF-8 output on Windows console
if sys.stdout.encoding != 'utf-8':
    try:
        sys.stdout.reconfigure(encoding='utf-8')
        sys.stderr.reconfigure(encoding='utf-8')
    except Exception:
        pass

import argparse
import json
import re
import shutil
import subprocess
import urllib.request
import urllib.error
from pathlib import Path

# Auto-detect ROOT path (whether run from root or from scripts/)
CURRENT_DIR = Path(__file__).resolve().parent
ROOT = CURRENT_DIR.parent if (CURRENT_DIR.parent / "app").exists() else CURRENT_DIR

BUILD_GRADLE = ROOT / "app" / "build.gradle.kts"
LOCAL_PROPS = ROOT / "local.properties"
APK_DIR = ROOT / "app" / "build" / "outputs" / "apk" / "full" / "release"
RELEASE_NOTES_FILE = ROOT / "RELEASE_NOTES.md"

def read_config_property(key, default=""):
    val = os.environ.get(key)
    if val:
        return val.strip()
    if LOCAL_PROPS.exists():
        with open(LOCAL_PROPS, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line.startswith(f"{key}="):
                    return line.split("=", 1)[1].strip()
    return default

def get_git_branch():
    try:
        res = subprocess.run(
            ["git", "rev-parse", "--abbrev-ref", "HEAD"],
            cwd=ROOT,
            capture_output=True,
            text=True,
            check=True
        )
        branch = res.stdout.strip()
        if branch and branch != "HEAD":
            return branch
    except Exception:
        pass
    return "main"

def get_git_repo_info():
    try:
        res = subprocess.run(
            ["git", "config", "--get", "remote.origin.url"],
            cwd=ROOT,
            capture_output=True,
            text=True
        )
        url = res.stdout.strip()
        m = re.search(r"github\.com[/:]([^/]+)/([^/\.]+)(?:\.git)?", url)
        if m:
            return m.group(1), m.group(2)
    except Exception:
        pass
    return None, None

auto_owner, auto_repo = get_git_repo_info()
GITHUB_OWNER = read_config_property("GITHUB_OWNER", auto_owner or "9000000")
configured_repo = read_config_property("GITHUB_REPO", "")
if configured_repo in ("", "NuvioTV") and auto_repo:
    GITHUB_REPO = auto_repo
else:
    GITHUB_REPO = configured_repo or auto_repo or "NuvioTV-Fast"
TARGET_BRANCH = get_git_branch()

def read_version():
    if not BUILD_GRADLE.exists():
        return "0.9.14-beta"
    try:
        with open(BUILD_GRADLE, "r", encoding="utf-8") as f:
            content = f.read()
            m = re.search(r'versionName\s*=\s*"([^"]+)"', content)
            if m:
                return m.group(1).strip()
    except Exception as e:
        print(f"[WARN] Khong doc duoc phien ban tu build.gradle.kts: {e}")
    return "0.9.14-beta"

def read_version_code():
    if not BUILD_GRADLE.exists():
        return None
    try:
        with open(BUILD_GRADLE, "r", encoding="utf-8") as f:
            content = f.read()
            m = re.search(r'versionCode\s*=\s*(\d+)', content)
            if m:
                return int(m.group(1).strip())
    except Exception:
        pass
    return None

def update_gradle_version(version_name: str, version_code: int = None) -> bool:
    if not BUILD_GRADLE.exists():
        return False
    try:
        content = BUILD_GRADLE.read_text(encoding="utf-8")
        new_content = re.sub(r'(versionName\s*=\s*")[^"]+(")', rf'\g<1>{version_name}\g<2>', content)
        if version_code is not None:
            new_content = re.sub(r'(versionCode\s*=\s*)\d+', rf'\g<1>{version_code}', new_content)
        if new_content != content:
            BUILD_GRADLE.write_text(new_content, encoding="utf-8")
            details = [f'versionName = "{version_name}"']
            if version_code is not None:
                details.append(f'versionCode = {version_code}')
            print(f"[OK] Da cap nhat app/build.gradle.kts: {', '.join(details)}")
            return True
    except Exception as e:
        print(f"[WARN] Khong the cap nhat phien ban vao app/build.gradle.kts: {e}")
    return False

def is_prerelease_version(version: str) -> bool:
    v_lower = version.lower()
    return any(keyword in v_lower for keyword in ["beta", "alpha", "rc", "preview", "dev"])

def read_token():
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    if token:
        return token.strip()
    token = read_config_property("GITHUB_TOKEN") or read_config_property("GH_TOKEN")
    return token if token else None

def build_apk():
    print("\n[1/3] Dang dong goi ban Release APK...")
    gradle_cmd = "gradlew.bat" if os.name == "nt" else "./gradlew"
    cmd = [str(ROOT / gradle_cmd), ":app:assembleFullRelease"]
    res = subprocess.run(cmd, cwd=ROOT)
    if res.returncode != 0:
        print("[ERROR] Loi khi bien dich APK Release!")
        sys.exit(res.returncode)
    print("[OK] Dong goi APK Release thanh cong!")

def get_release_apks(version):
    """
    Lay danh sach cac file APK Release cho dung phien ban hien tai.
    Tranh gom nham cac ban APK cu chua don dep hoac cac flavor khac.
    """
    search_dirs = []
    if APK_DIR.exists():
        search_dirs.append(APK_DIR)
    else:
        fallback_dir = ROOT / "app" / "build" / "outputs" / "apk"
        if fallback_dir.exists():
            search_dirs.append(fallback_dir)

    apks = []
    seen = set()
    prefix = f"NuvioTV-{version}-"
    generic_prefix = "NuvioTV-"

    for d in search_dirs:
        for p in d.rglob("*.apk"):
            if "unaligned" in p.name.lower():
                continue
            name = p.name

            # 1. Truong hop dung file NuvioTV-{version}-{abi}.apk da duoc gradle dat ten chuan
            if name.startswith(prefix) and name.endswith(".apk"):
                if p not in seen:
                    apks.append(p)
                    seen.add(p)
                continue

            # 2. Neu truoc do la ten mac dinh Gradle kieu app-full-...-release.apk, doi ten sang NuvioTV-{version}-{abi}.apk
            if "release" in name.lower() and not name.startswith(generic_prefix):
                abi = "universal"
                for target_abi in ["arm64-v8a", "armeabi-v7a", "x86_64", "x86", "universal"]:
                    if target_abi in name.lower():
                        abi = target_abi
                        break
                new_name = f"NuvioTV-{version}-{abi}.apk"
                new_path = p.parent / new_name
                try:
                    if p != new_path:
                        if new_path.exists():
                            new_path.unlink()
                        p.rename(new_path)
                        print(f"   [RENAME] {p.name} -> {new_name}")
                        p = new_path
                except Exception as e:
                    print(f"   [WARN] Khong the doi ten {p.name}: {e}")
                if p not in seen:
                    apks.append(p)
                    seen.add(p)

    # 3. Neu van chua thay APK nao (vi du nguoi dung dung --skip-build sau khi doi version)
    if not apks:
        for d in search_dirs:
            for p in d.rglob("*.apk"):
                if "unaligned" in p.name.lower():
                    continue
                m = re.search(r"^NuvioTV-.+?-(arm64-v8a|armeabi-v7a|x86_64|x86|universal)\.apk$", p.name)
                if m:
                    abi = m.group(1)
                    new_name = f"NuvioTV-{version}-{abi}.apk"
                    new_path = p.parent / new_name
                    try:
                        if p != new_path:
                            if new_path.exists():
                                new_path.unlink()
                            p.rename(new_path)
                            print(f"   [RENAME] {p.name} -> {new_name}")
                            p = new_path
                    except Exception as e:
                        print(f"   [WARN] Khong the doi ten {p.name}: {e}")
                    if p not in seen:
                        apks.append(p)
                        seen.add(p)

    return sorted(apks, key=lambda x: x.name)

def get_default_release_notes(version):
    if RELEASE_NOTES_FILE.exists():
        try:
            with open(RELEASE_NOTES_FILE, "r", encoding="utf-8") as f:
                content = f.read().strip()
                if content:
                    return content
        except Exception:
            pass

    return f"""## Nuvio TV {version}

### Cập nhật & Tối ưu hóa:
- Cấu hình & tối ưu kết nối P2P tự động, nâng cao độ ổn định và tốc độ truyền tải.
- Tối ưu giao diện Android TV: Điều hướng remote siêu mượt, cải thiện độ trễ và giữ focus ổn định.
- TorrServer Remote Engine: Hỗ trợ stream torrent qua máy chủ TorrServer từ xa (PC, Docker, NAS, Box).
- Tinh gọn bộ nhớ: Loại bỏ tập tin nhị phân cục bộ giúp giảm dung lượng cài đặt.
- Trình phát đa phương tiện nâng cao: Hỗ trợ lựa chọn audio, phụ đề và tùy biến tốc độ phát.
"""

def upload_via_gh(tag, title, notes, apks, is_prerelease=False, is_draft=False):
    print(f"\n[3/3] Dang tai len GitHub qua GitHub CLI (gh) vao {GITHUB_OWNER}/{GITHUB_REPO}...")
    cmd = [
        "gh", "release", "create", tag,
        *[str(apk) for apk in apks],
        "--repo", f"{GITHUB_OWNER}/{GITHUB_REPO}",
        "--target", TARGET_BRANCH,
        "--title", title,
        "--notes", notes,
    ]
    if is_prerelease:
        cmd.append("--prerelease")
    if is_draft:
        cmd.append("--draft")

    res = subprocess.run(cmd, cwd=ROOT)
    if res.returncode == 0:
        print(f"\n[SUCCESS] Da phat hanh Release {tag} tai:")
        print(f"-> https://github.com/{GITHUB_OWNER}/{GITHUB_REPO}/releases/tag/{tag}\n")
        return True
    return False

def upload_via_api(token, tag, title, notes, apks, is_prerelease=False, is_draft=False):
    print(f"\n[3/3] Dang tao Release va tai len APK qua GitHub API vao {GITHUB_OWNER}/{GITHUB_REPO}...")
    api_base = f"https://api.github.com/repos/{GITHUB_OWNER}/{GITHUB_REPO}"
    create_url = f"{api_base}/releases"

    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "NuvioTV-Release-Script",
    }

    payload = json.dumps({
        "tag_name": tag,
        "target_commitish": TARGET_BRANCH,
        "name": title,
        "body": notes,
        "draft": is_draft,
        "prerelease": is_prerelease
    }).encode("utf-8")

    req_headers = {**headers, "Content-Type": "application/json"}
    req = urllib.request.Request(create_url, data=payload, headers=req_headers, method="POST")

    release_data = None
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            release_data = json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        err_msg = e.read().decode("utf-8", errors="replace")
        if e.code == 422:
            print(f"[WARN] Release {tag} da ton tai tren GitHub, dang lay thong tin de bo sung/cap nhat APK...")
            try:
                get_url = f"{api_base}/releases/tags/{tag}"
                get_req = urllib.request.Request(get_url, headers=headers, method="GET")
                with urllib.request.urlopen(get_req, timeout=60) as get_resp:
                    release_data = json.loads(get_resp.read().decode("utf-8"))
            except Exception as ex:
                print(f"[ERROR] Khong the lay thong tin release ton tai: {ex}")
                return False
        else:
            print(f"[ERROR] Loi tao release qua GitHub API: HTTP {e.code} - {err_msg}")
            return False
    except Exception as e:
        print(f"[ERROR] Loi ket noi GitHub API: {e}")
        return False

    if not release_data:
        print("[ERROR] Khong nhan duoc du lieu Release hop le tu GitHub.")
        return False

    upload_url_template = release_data.get("upload_url", "")
    html_url = release_data.get("html_url", "")
    upload_url_base = upload_url_template.split("{")[0]
    release_id = release_data.get("id")

    # Lay danh sach assets hien co de xoa file trung truoc khi upload moi (tranh loi 422 already_exists)
    existing_assets = {}
    if release_id:
        try:
            assets_url = f"{api_base}/releases/{release_id}/assets"
            assets_req = urllib.request.Request(assets_url, headers=headers, method="GET")
            with urllib.request.urlopen(assets_req, timeout=60) as a_resp:
                assets_list = json.loads(a_resp.read().decode("utf-8"))
                for a in assets_list:
                    existing_assets[a["name"]] = a["id"]
        except Exception as e:
            print(f"[WARN] Khong lay duoc danh sach asset cu: {e}")

    for apk in apks:
        size_mb = apk.stat().st_size / (1024 * 1024)

        # Neu asset cu da ton tai, goi DELETE de xoa truoc khi tai len ban moi
        if apk.name in existing_assets:
            old_id = existing_assets[apk.name]
            print(f"   [OVERWRITE] Phat hien {apk.name} da co san tren Release. Dang xoa asset cu (ID: {old_id})...")
            try:
                del_url = f"{api_base}/releases/assets/{old_id}"
                del_req = urllib.request.Request(del_url, headers=headers, method="DELETE")
                with urllib.request.urlopen(del_req, timeout=60) as del_resp:
                    pass
            except Exception as e:
                print(f"   [WARN] Khong the xoa asset cu {apk.name}: {e}")

        print(f"   [UPLOAD] Dang tai len {apk.name} ({size_mb:.2f} MB)...")
        with open(apk, "rb") as f:
            apk_bytes = f.read()

        target_url = f"{upload_url_base}?name={apk.name}"
        up_headers = {
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "NuvioTV-Release-Script",
            "Content-Type": "application/vnd.android.package-archive",
            "Content-Length": str(len(apk_bytes)),
        }
        up_req = urllib.request.Request(target_url, data=apk_bytes, headers=up_headers, method="POST")
        try:
            with urllib.request.urlopen(up_req, timeout=300) as up_resp:
                print(f"   [DONE] Da tai xong: {apk.name}")
        except urllib.error.HTTPError as e:
            print(f"   [FAIL] Loi tai len {apk.name}: HTTP {e.code} - {e.read().decode('utf-8', errors='replace')}")
            return False
        except Exception as e:
            print(f"   [FAIL] Loi truyen file {apk.name}: {e}")
            return False

    print(f"\n[SUCCESS] Da phat hanh Release {tag} thanh cong tai:")
    print(f"-> {html_url}\n")
    return True

def parse_arguments():
    parser = argparse.ArgumentParser(
        description="Script tự động phát hành bản Release APK của Nuvio TV lên GitHub Releases."
    )
    parser.add_argument(
        "version_pos",
        nargs="?",
        default=None,
        metavar="VERSION",
        help="Số phiên bản release tùy chọn (ví dụ: 0.9.14 hoặc 0.9.14-beta)."
    )
    parser.add_argument(
        "-v", "--version",
        type=str,
        default=None,
        dest="version_flag",
        metavar="VERSION",
        help="Tùy chỉnh số phiên bản release (ví dụ: 0.9.14)."
    )
    parser.add_argument(
        "--version-code",
        type=int,
        default=None,
        help="Tùy chỉnh versionCode trong build.gradle.kts (ví dụ: 1070)."
    )
    parser.add_argument(
        "--publish",
        action="store_true",
        default=True,
        help="Đóng gói APK Release và phát hành lên GitHub Releases (mặc định luôn bật khi chạy lệnh)."
    )
    parser.add_argument(
        "-i", "--interactive",
        action="store_true",
        default=False,
        help="Bật chế độ nhập tương tác số phiên bản từ bàn phím."
    )
    parser.add_argument(
        "-y", "--yes",
        action="store_true",
        default=False,
        help="Tự động xác nhận và tiến hành (bỏ qua các câu hỏi tương tác)."
    )
    parser.add_argument(
        "--skip-build",
        action="store_true",
        help="Bỏ qua bước biên dịch Gradle (:app:assembleFullRelease), sử dụng các file APK có sẵn."
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Chạy thử nghiệm kiểm tra phiên bản, nhánh và danh sách APKs mà không thực hiện upload lên GitHub."
    )
    parser.add_argument(
        "--tag",
        type=str,
        default=None,
        help="Tùy chỉnh Release Tag (mặc định lấy theo versionName: vX.Y.Z)."
    )
    parser.add_argument(
        "--title",
        type=str,
        default=None,
        help="Tùy chỉnh tiêu đề Release (mặc định: 'Nuvio TV <version>')."
    )
    parser.add_argument(
        "--notes",
        type=str,
        default=None,
        help="Nội dung Release Notes dạng text/markdown."
    )
    parser.add_argument(
        "--notes-file",
        type=Path,
        default=None,
        help="Đường dẫn tới file markdown chứa nội dung Release Notes."
    )
    parser.add_argument(
        "--prerelease",
        action="store_true",
        default=None,
        help="Đánh dấu release này là Pre-release (tự động bật nếu versionName có beta/alpha/rc)."
    )
    parser.add_argument(
        "--draft",
        action="store_true",
        default=False,
        help="Tạo release ở chế độ nháp (Draft)."
    )
    return parser.parse_args()

def main():
    args = parse_arguments()

    current_gradle_version = read_version()
    current_gradle_code = read_version_code()

    # Xác định phiên bản release:
    # 1. Nếu có truyền tham số phiên bản (ví dụ: ./release.py 0.9.15 hoặc -v 0.9.15) -> lấy tham số đó
    # 2. Nếu có cờ -i / --interactive -> hỏi người dùng nhập qua bàn phím
    # 3. Mặc định: lấy phiên bản hiện tại từ app/build.gradle.kts và phát hành trực tiếp
    specified_version = args.version_flag or args.version_pos
    if specified_version:
        version = specified_version.strip().lstrip("v").lstrip("V")
    elif args.interactive and sys.stdin.isatty():
        print("==================================================")
        print("Nuvio TV - Trình phát hành Release APK")
        print("==================================================")
        code_info = f" (versionCode: {current_gradle_code})" if current_gradle_code else ""
        print(f"Phiên bản hiện tại : {current_gradle_version}{code_info}")
        try:
            prompt_text = f"Nhập số phiên bản mới [Enter để giữ '{current_gradle_version}']: "
            user_input = input(prompt_text).strip()
            if user_input:
                version = user_input.lstrip("v").lstrip("V")
            else:
                version = current_gradle_version
        except (KeyboardInterrupt, EOFError):
            print("\n[INFO] Đã hủy thao tác.")
            sys.exit(0)
    else:
        version = current_gradle_version

    # Tự động nâng versionCode khi versionName thay đổi (nếu người dùng không chỉ định cụ thể)
    is_version_changed = (version != current_gradle_version)
    if args.version_code is not None:
        target_code = args.version_code
    elif is_version_changed and current_gradle_code is not None:
        target_code = current_gradle_code + 1
    else:
        target_code = current_gradle_code

    # Cập nhật app/build.gradle.kts nếu có thay đổi phiên bản hoặc versionCode
    if not args.dry_run:
        if is_version_changed or (target_code != current_gradle_code):
            update_gradle_version(version, target_code)
    else:
        if is_version_changed:
            print(f"[DRY-RUN] Sẽ cập nhật app/build.gradle.kts -> versionName = \"{version}\"")
        if target_code != current_gradle_code:
            print(f"[DRY-RUN] Sẽ tự động nâng app/build.gradle.kts -> versionCode = {target_code} (từ {current_gradle_code})")

    tag = args.tag if args.tag else f"v{version}"
    title = args.title if args.title else f"Nuvio TV {tag.lstrip('v')}"

    # Xác định Release Notes
    if args.notes:
        notes = args.notes
    elif args.notes_file and args.notes_file.exists():
        notes = args.notes_file.read_text(encoding="utf-8")
    else:
        notes = get_default_release_notes(version)

    # Xác định prerelease flag
    if args.prerelease is not None:
        is_prerelease = args.prerelease
    else:
        is_prerelease = is_prerelease_version(version)

    print(f"\n==================================================")
    print(f"Nuvio TV - Tu dong phat hanh ban Release")
    print(f"Phien ban: {tag} ({title})")
    if target_code:
        print(f"Version Code: {target_code}")
    print(f"Pre-release: {'Co' if is_prerelease else 'Khong'} | Draft: {'Co' if args.draft else 'Khong'}")
    print(f"Nhanh nguon (Target Branch): {TARGET_BRANCH}")
    print(f"Kho chua (Repository): {GITHUB_OWNER}/{GITHUB_REPO}")
    if args.dry_run:
        print(f"Che do: DRY-RUN (Kiem tra thu nghiem, khong upload)")
    print(f"==================================================")

    # 1. Build
    if args.skip_build or args.dry_run:
        print("\n[1/3] Bo qua buoc dong goi APK. Su dung cac file APK da co san.")
    else:
        build_apk()

    # 2. Collect APKs
    apks = get_release_apks(version)
    if not apks:
        print(f"[ERROR] Khong tim thay file APK nao cho phien ban {version} trong build output!")
        print(f"Vui long build truoc: ./gradlew :app:assembleFullRelease")
        sys.exit(1)

    print(f"\n[2/3] Danh sach file APK Release ({len(apks)} tep):")
    total_size = 0
    for a in apks:
        size_mb = a.stat().st_size / (1024 * 1024)
        total_size += size_mb
        print(f"   - {a.name} ({size_mb:.2f} MB)")
    print(f"   => Tong dung luong: {total_size:.2f} MB")

    # Dry-run check
    if args.dry_run:
        print(f"\n[3/3] DRY-RUN thanh cong! Tat ca thong tin da san sang.")
        print(f"\n--- NOI DUNG RELEASE NOTES DU KIEN ---")
        print(notes.strip())
        print(f"--------------------------------------\n")
        return

    # 3. Upload
    gh_available = shutil.which("gh") is not None
    if gh_available:
        if upload_via_gh(tag, title, notes, apks, is_prerelease=is_prerelease, is_draft=args.draft):
            return

    token = read_token()
    if token:
        if upload_via_api(token, tag, title, notes, apks, is_prerelease=is_prerelease, is_draft=args.draft):
            return
    else:
        print("\n[WARN] Chua cau hinh xac thuc GitHub!")
        print("Them Token vao file local.properties:")
        print("   GITHUB_TOKEN=ghp_your_personal_access_token")
        sys.exit(1)

if __name__ == "__main__":
    main()
