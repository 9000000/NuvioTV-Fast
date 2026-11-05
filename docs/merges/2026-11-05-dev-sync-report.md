# Báo cáo kết quả đồng bộ `dev` → `tet2` (2026-11-05)

> Tài liệu này được tạo **sau khi merge, xử lý xung đột, compile và chạy test**.
> Mọi số liệu lấy trực tiếp từ repository `F:\MyGithub\NuvioTV-Fast`.

---

## 1. Tóm tắt điều hành

Đã merge thành công **47 commit** mới nhất của `dev` vào `tet2` (33 commit thường + 14 merge commit, tác giả 2026-10-31 → 2026-11-05).

- Merge commit: **`b13a4d35a`**
- Không còn xung đột, working tree sạch.
- `dev` hiện là tổ tiên của `tet2` (`git rev-list --count HEAD..dev` = **0**).
- Giữ nguyên bản sắc fork: **versionCode 1174 / versionName 1.3.2**.
- Giữ toàn bộ tùy chỉnh TorrServer, audio, Live TV, stream search, UI, phụ đề và tiếng Việt.
- Áp dụng đầy đủ tính năng mới của upstream: AutoSync ASS/SSA qua libass, Trakt episode remap, binge back navigation, background decoder opt-in, cập nhật dịch thuật.
- `:app:compileFullDebugKotlin` và các unit test trọng tâm đều **thành công**.
- **Chưa push** lên remote.

---

## 2. Thông tin merge

| Hạng mục | Giá trị |
|---|---|
| Nhánh đích | `tet2` |
| HEAD trước merge | `32bac7217` - chore: preserve local changes before dev sync (v1.3.2, reference cleanup) |
| Nguồn đồng bộ | `origin/dev` = `upstream/dev` = `dev` = `1206736fd` - bump version (2026-11-05) |
| Merge base | `a102f6bf5` - Merge pull request #4443 (2026-11-03) |
| Merge commit | `b13a4d35ab0bbee70c0ec149ba2e0a9d9d8cc9aa` |
| Parent 1 | `32bac72175fd72bbd8d076921260d37cdaae3bc0` (`tet2`) |
| Parent 2 | `1206736fd4322e1e8d4bd962188cf7e91ec9c1d5` (`dev`) |
| Số commit upstream | 47 (33 thường + 14 merge) |
| Chiến lược | `git merge --no-ff dev` |
| Backup branch | `backup-tet2-before-merge-20261105` → `2888dc7f4` |
| Patch dự phòng | `C:\Users\NongTuan\AppData\Local\Temp\opencode\tet2-pre-merge-20261105.patch` |

---

## 3. Kết quả xác minh sau merge

| Kiểm tra | Kết quả |
|---|---|
| `dev` là tổ tiên của `HEAD` | ✅ `git rev-list --count HEAD..dev` = 0 |
| Merge commit có 2 parent đúng | ✅ `32bac7217` + `1206736fd` |
| Working tree sạch | ✅ `git status --porcelain` không có output |
| Không còn conflict marker | ✅ Đã quét toàn bộ source |
| Version fork | ✅ `versionCode = 1174`, `versionName = "1.3.2"` |
| Custom `tunnelFrameReportDecoders` | ✅ Còn nguyên |
| Upstream `releaseDecoderOnBackground` | ✅ Có đủ key, flow, setter |
| `app_name` pt-BR | ✅ `Nuvio TV` |
| Key P2P trong pt-BR | ✅ Không còn |
| `strings_party.xml`, `strings_servers.xml` pt-BR | ✅ Đã thêm từ upstream |
| `ShuffleButton` | ✅ Vẫn bị xóa theo quyết định của `tet2` |

---

## 4. Xung đột và cách xử lý

Dự báo trước merge là **3 file xung đột**, thực tế đúng **3 file**. Chi tiết đầy đủ nằm trong `2026-11-05-dev-sync-conflict-report.md`.

### 4.1 `app/build.gradle.kts`

| | `tet2` | `dev` | Quyết định |
|---|---|---|---|
| `versionCode` | `1174` | `1074` | Giữ `tet2` |
| `versionName` | `1.3.2` | `1.1.0-beta.11` | Giữ `tet2` |

**Lý do:** hai scheme version độc lập. Lấy upstream sẽ làm giảm versionCode và lùi bản release của fork về beta. Toàn bộ thay đổi khác của upstream trong file vẫn được áp dụng.

### 4.2 `app/src/main/java/com/nuvio/tv/data/local/DeviceLocalPlayerPreferences.kt`

Xung đột chỉ ở vị trí chèn key. Đã giữ **cả hai tính năng độc lập**:

- Custom của `tet2`:
  - `tunnelFrameReportDecodersKey`
  - `tunnelFrameReportDecoders: Flow<Set<String>>`
  - `addTunnelFrameReportDecoder(key: String)`
- Upstream `dev`:
  - `releaseDecoderOnBackgroundKey`
  - `releaseDecoderOnBackground: Flow<Boolean>`
  - `setReleaseDecoderOnBackground(enabled: Boolean)`

### 4.3 `app/src/main/res/values-pt-rBR/strings.xml`

| Hunk | Quyết định |
|---|---|
| `collections_editor_tmdb_watch_providers_helper` | Lấy upstream (escape dấu nháy mới) |
| Nhóm `dv7_libdovi_mode_*` | Lấy upstream (thêm `translatable="false"`, nội dung dịch mới) |

Đồng thời giữ các tùy chỉnh của `tet2`:

- `app_name` = `Nuvio TV` (không lấy `Nuvio`).
- Không khôi phục 7 key P2P đã xóa:
  - `essential_p2p_streams`
  - `essential_p2p_streams_subtitle`
  - `p2p_consent_title`
  - `p2p_consent_body`
  - `p2p_consent_enable`
  - `p2p_consent_cancel`
  - `settings_p2p_title`
- Vẫn thêm 2 file dịch mới của upstream: `strings_party.xml`, `strings_servers.xml`.

---

## 5. Tùy chỉnh của `tet2` được bảo toàn

| Khu vực | Trạng thái |
|---|---|
| App identity, endpoint, updater, signing (`app/build.gradle.kts`) | ✅ Giữ nguyên; chỉ chốt version fork |
| TorrServer / Torrent (`TorrServer*`, `Torrent*`, `PlayerRuntimeControllerTorrent*`) | ✅ Auto-merge, symbol còn đủ |
| Modern Leanback Home / sidebar / neon glow | ✅ Auto-merge |
| Audio passthrough / IEC / surround | ✅ Auto-merge |
| Live TV / IPTV | ✅ Auto-merge |
| Stream search bar + stream ordering/badges | ✅ Auto-merge |
| Custom player runtime, tunnel AV sync, startup watchdog, buffer memory keeper | ✅ Auto-merge |
| Custom subtitle outline/style (`applyOutlineWidth`) | ✅ Còn nguyên |
| Vietnamese localization + custom fonts | ✅ Còn nguyên |
| Version 1.3.2 | ✅ `1174` / `1.3.2` |
| Hai tài liệu tham khảo đã xóa (`tai_lieu_tham_khao/*`) | ✅ Giữ trạng thái xóa |

**Kiểm tra tĩnh bổ sung:**

- Parse **64 file string resource** trong **38 thư mục `values*`**.
- Không có lỗi XML, không có key trùng, không có conflict marker.
- Các symbol custom và upstream đều hiện diện.
- `ShuffleButton` đã được xóa hoàn toàn (không còn tham chiếu).

---

## 6. Thay đổi nổi bật từ upstream

### 6.1 AutoSync / libass / phụ đề

- Đồng bộ phụ đề ASS/SSA add-on được libass render.
- Chỉ ứng viên ASS/SSA mới cạnh tranh trên đường libass.
- So khớp phụ đề ASS/SSA theo dialogue.
- Nâng `nuvio-libass` lên **v0.0.13**, chuyển track bằng `replaceTrack`.
- Fallback sang SRT/VTT khi không có ASS/SSA phù hợp.
- Chỉ xác nhận "no-fit" từ reference đầy đủ.
- Giữ phụ đề add-on người dùng đã chọn cho series.
- Reapply kết quả sync sau khi player rebuild.
- Tối ưu: compile regex ASS override một lần.
- Thêm mới:
  - `AutoSyncAssDialogueFilter.kt`
  - `AutoSyncLibassSubtitles.kt`
  - Các test tương ứng.

### 6.2 Trakt / tiến độ xem

- Giữ số tập add-on khi add-on có tập đó.
- Remap episode Trakt cho show dài (PR #4470).
- Bỏ ngưỡng start threshold của continue watching, khớp mobile.
- Sửa độ trễ chọn tập trong player.
- Sửa loading khi autoplay tập (PR #4406).

### 6.3 Điều hướng / profile

- Bỏ màn hình streams cũ khi Back sau binge skip.
- Cho phép Back thoát màn hình PIN profile.

### 6.4 Cài đặt player

- Thêm setting opt-in **giải phóng decoder khi app xuống background** (`releaseDecoderOnBackground`), mặc định tắt.

### 6.5 UI / dịch thuật

- Cập nhật hàng loạt chuỗi: `values`, `es-419`, `pt-BR`, `bg`, `el`, `es`, `fr`, `hu`, `it`, `iw`, `pl`, `sk`, `tr`, `vi`.
- Thêm chuỗi Watch party, server, autosync.
- Rút ngắn tab divider và đồng bộ style nút shuffle (file `ShuffleButton.kt` vẫn bị xóa ở `tet2`).

---

## 7. Thống kê thay đổi

### 7.1 Delta upstream áp dụng vào `tet2`

```text
57 files changed, 1544 insertions(+), 350 deletions(-)
```

| Loại | Số file |
|---|---|
| Modified | 49 |
| Added | 7 |
| Deleted | 1 (`ShuffleButton.kt`, giữ quyết định xóa của `tet2`) |

### 7.2 So với dự báo trước merge

| Chỉ số | Dự báo | Thực tế | Giải thích |
|---|---|---|---|
| File | 58 | 57 | `ShuffleButton.kt` bị `tet2` xóa; upstream sửa file này nhưng merge giữ trạng thái xóa nên không phát sinh diff |
| Insertions | +1.546 | +1.544 | Chênh lệch 2 dòng từ chính file `ShuffleButton.kt` |
| Deletions | −352 | −350 | Chênh lệch 2 dòng từ chính file `ShuffleButton.kt` |

### 7.3 File binary

- `app/libs/nuvio-libass-release.aar`: `5.245.316` → `5.245.885` bytes (libass v0.0.13).

---

## 8. Build và test

### 8.1 Compile

```text
.\gradlew.bat :app:compileFullDebugKotlin --console=plain
BUILD SUCCESSFUL in 11m 9s
```

- Chỉ có warning deprecation/opt-in vốn có.
- Không có lỗi compile.

### 8.2 Unit test trọng tâm

```text
.\gradlew.bat :app:testFullDebugUnitTest ^
  --tests "com.nuvio.tv.data.repository.TraktEpisodeMappingServiceTest" ^
  --tests "com.nuvio.tv.ui.screens.player.autosync.*" ^
  --tests "com.nuvio.tv.data.mdblist.MdbListProgressProjectionTest" ^
  --console=plain
BUILD SUCCESSFUL in 5m 37s
```

Các nhóm test đã chạy:

- `TraktEpisodeMappingServiceTest`
- `com.nuvio.tv.ui.screens.player.autosync.*`
  - `AutoSyncAssDialogueFilterTest`
  - `AutoSyncNoFitTrackerTest`
  - `AutoSyncSubtitleTextRetimerTest`
  - `RetimedAssSubtitleParserTest`
- `MdbListProgressProjectionTest`

### 8.3 Chưa chạy

- `:app:assembleFullDebug` (bước tùy chọn trong kế hoạch). Resource linking và unit test đã chạy thành công; nên chạy assemble hoặc cài thử lên thiết bị trước khi phát hành.

---

## 9. Trạng thái Git và rollback

### 9.1 Trạng thái hiện tại

- `tet2` đang ở `b13a4d35a`.
- `tet2` vẫn chưa được push; `origin/tet2` chưa thay đổi.
- Working tree sạch.

### 9.2 Rollback

Quay về trạng thái trước merge (mất cả commit bảo toàn `32bac7217`):

```bash
git reset --hard backup-tet2-before-merge-20261105
```

Hoặc revert merge commit nhưng giữ lịch sử:

```bash
git revert -m 1 b13a4d35a
```

Patch dự phòng cho các thay đổi cục bộ trước merge:

```text
C:\Users\NongTuan\AppData\Local\Temp\opencode\tet2-pre-merge-20261105.patch
```

---

## 10. Ghi chú

- `docs/` nằm trong `.gitignore` (dòng 70). Ba tài liệu merge được `git add -f` để lưu trữ cùng lịch sử nếu cần.
- Không có thay đổi nào bị mất: mọi tùy chỉnh đã được kiểm tra bằng symbol, XML parse và compile.
- Không push theo yêu cầu.

---

## 11. Checklist hoàn thành

- [x] 47 commit của `dev` đã nằm trong `tet2`.
- [x] Không còn conflict marker.
- [x] 3 xung đột đã xử lý đúng hướng dẫn.
- [x] `:app:compileFullDebugKotlin` thành công.
- [x] Test trọng tâm pass.
- [x] Custom TorrServer/UI/Audio/Live TV/Stream search/Vietnamese strings còn nguyên.
- [x] Version 1.3.2 được giữ.
- [x] Không push.

---

## Phụ lục A - Danh sách 47 commit đã đồng bộ

| # | Hash | Ngày | Nội dung |
|---|---|---|---|
| 1 | `f4c482e64` | 2026-10-31 | Fix player episode selection delay |
| 2 | `9e311d710` | 2026-10-31 | fix(autosync): sync ASS/SSA add-on subtitles drawn by libass |
| 3 | `42cfca0ec` | 2026-11-01 | shorten tab dividers |
| 4 | `2ba905a8d` | 2026-11-01 | match shuffle button to other actions |
| 5 | `dc9a9fb66` | 2026-11-03 | Update strings.xml |
| 6 | `cd7a3c839` | 2026-11-03 | Merge pull request #4447 from viiktr/dev |
| 7 | `3c33a5891` | 2026-11-03 | Add files via upload |
| 8 | `aeb3ed629` | 2026-11-03 | Delete app/src/main/res/values-pt-rBR/strings.xml |
| 9 | `b4b6cb2b7` | 2026-11-03 | Add files via upload |
| 10 | `49d83006a` | 2026-11-03 | Add files via upload |
| 11 | `6500920a2` | 2026-11-03 | Merge pull request #4451 from danilopagotto82/pt-br-translate-1.1.0(10) |
| 12 | `31400cf86` | 2026-11-04 | Merge branch 'dev' of https://github.com/NuvioMedia/NuvioTV into dev |
| 13 | `99fbd65d8` | 2026-11-04 | bump version |
| 14 | `af4aa4625` | 2026-11-04 | Merge branch 'fixes-2' into pr/4410 |
| 15 | `6194be925` | 2026-11-04 | fix(autosync): match ASS/SSA subtitles by their dialogue |
| 16 | `17707bad9` | 2026-11-04 | fix(autosync): keep the add-on subtitle the user chose for the series |
| 17 | `8da207853` | 2026-11-04 | fix(autosync): stay silent unless the subtitle does not fit the video |
| 18 | `3d65d94d6` | 2026-11-04 | fix(autosync): shorten the Polish synced label |
| 19 | `23c229109` | 2026-11-04 | Update Slovak subtitles strings in strings.xml |
| 20 | `f860daeb4` | 2026-11-04 | fix(autosync): confirm a no-fit only from full references |
| 21 | `00754edfb` | 2026-11-04 | fix(autosync): let only ASS/SSA candidates compete on the libass path |
| 22 | `995f57ab0` | 2026-11-04 | refactor(autosync): compile the ASS override block regex once |
| 23 | `747f7610c` | 2026-11-04 | Merge branch 'NuvioMedia:dev' into dev |
| 24 | `fd95a7caf` | 2026-11-04 | fix(autosync): bump nuvio-libass to v0.0.13 and swap tracks with replaceTrack |
| 25 | `19886da73` | 2026-11-04 | Update strings.xml |
| 26 | `38f820e46` | 2026-11-04 | fix(autosync): fall back to SRT/VTT when no ASS/SSA subtitle fits |
| 27 | `1cb85edbc` | 2026-11-04 | fix(autosync): reapply the synced result after a player rebuild |
| 28 | `212940d5d` | 2026-11-04 | fix(profiles): let Back leave the profile PIN screen |
| 29 | `c1c3ca78f` | 2026-11-04 | Merge pull request #4458 from mmsw91/dev |
| 30 | `e0d4adcc1` | 2026-11-04 | Merge pull request #4410 from DavidVamaiotu/claude/project-thread-g61t4v |
| 31 | `6c858a102` | 2026-11-04 | Merge pull request #4460 from skoruppa/fix/profile-pin-back |
| 32 | `e985563f5` | 2026-11-04 | Translate missing Latin American Spanish strings |
| 33 | `a4d4f5054` | 2026-11-04 | Translate new Watch party chat note |
| 34 | `4b74d724a` | 2026-11-04 | Preserve resource formatting in Spanish strings |
| 35 | `1b6acb8ee` | 2026-11-04 | Translate new Watch party chat note |
| 36 | `14a6d759f` | 2026-11-04 | Merge pull request #4467 from omavel/codex/es-419-resource-updates-2026-11 |
| 37 | `f07acf603` | 2026-11-04 | fix(trakt): keep addon episode numbers when the addon has the episode |
| 38 | `8f3112655` | 2026-11-05 | Merge pull request #4406 from oathzed/fix/episode-autoplay-loading |
| 39 | `bc4dfde92` | 2026-11-05 | update latest missing strings |
| 40 | `bae406453` | 2026-11-05 | Merge pull request #4476 from blueocean2308/dev |
| 41 | `3412e2803` | 2026-11-05 | feat(player): make background decoder release opt-in |
| 42 | `eee447a99` | 2026-11-05 | Merge pull request #4479 from skoruppa/feat/release-decoder-on-background-setting |
| 43 | `bbf2d4388` | 2026-11-05 | fix(nav): drop stale streams screen on back after binge skip |
| 44 | `ca8bdce45` | 2026-11-05 | fix(progress): remove start threshold, match mobile |
| 45 | `a6a826687` | 2026-11-05 | Merge pull request #4486 from skoruppa/fix/binge-back-and-cw-progress |
| 46 | `4b7da3744` | 2026-11-05 | Merge pull request #4470 from jblaker/fix/trakt-episode-remap-long-shows |
| 47 | `1206736fd` | 2026-11-05 | bump version |
