# Kế hoạch đồng bộ nhánh `dev` → `tet2` (2026-11-14)

> **Trạng thái**: Đã thực thi & Đã kiểm chứng thành công trên nhánh kiểm thử dryrun (`BUILD SUCCESSFUL`).
> Mọi số liệu, danh sách commit, file thay đổi và xung đột dưới đây đã được xác minh trực tiếp bằng Git trên repository `F:\MyGithub\NuvioTV-Fast`.

---

## 1. Tóm tắt điều hành (Executive Summary)

| Hạng mục | Giá trị |
|---|---|
| **Nhánh đích** | `tet2` |
| **HEAD ban đầu của `tet2`** | `3979358bb` — `docs: record 2026-11-10 dev sync completed status in active rule` |
| **Nhánh sao lưu an toàn** | `backup-tet2-before-merge-20261114` (trỏ tại `3979358bb`) |
| **Nguồn đồng bộ** | `upstream/dev` = `origin/dev` = `a3532b819` (`Merge pull request #4582 from kernexshadow/fix/watch-progress-multi-window`, 2026-11-14) |
| **Merge base (`tet2`, `upstream/dev`)** | `65538103f` — `Merge pull request #4546 from scheperr/dev` (2026-11-09, mốc đồng bộ trước ngày 2026-11-10) |
| **Phạm vi yêu cầu** | Tất cả commit mới nhất từ nhánh `dev` từ ngày `2026-11-10` đến hôm nay (`2026-11-14`) |
| **Tổng số commit đồng bộ** | **101 commit** sau mốc `65538103f` (**67 commit thường** + **34 merge commit**) |
| **Số file thay đổi từ `upstream/dev`** | **94 file** |
| **Xung đột văn bản trực tiếp (`CONFLICT`)** | **9 file**: `app/build.gradle.kts`, `MainActivity.kt`, `Screen.kt`, `MetaDetailsScreen.kt`, `HomeViewModelContinueWatching.kt`, `SubtitleSelectionOverlay.kt`, `SearchScreen.kt`, `PlaybackSettingsViewModel.kt`, `PlaybackSubtitleSettings.kt` |
| **Lỗi biên dịch & Tài nguyên phát sinh** | **4 điểm**: AAPT2 trùng key string (`sub_font`), `OnSetSubtitleFont` conflicting declaration, nhúng slider `outline_width` vào `SubtitleAppearanceEditor.kt`, và mock credentials trong `TraktAuthServiceTest.kt` |
| **Chiến lược thực thi** | Tạo nhánh backup `backup-tet2-before-merge-20261114` → Thực hiện merge trên nhánh kiểm thử độc lập `test-dev-sync-dryrun` → Giải quyết triệt để 9 file conflict → Sửa 4 điểm phát sinh → Biên dịch `compileFullDebugKotlin` & chạy `testFullDebugUnitTest` kiểm chứng đạt 100% → Áp dụng commit merge vào `tet2` → Fast-forward `dev` lên `upstream/dev` → Xuất báo cáo đầy đủ |

---

## 2. Xác minh Ref, Nhánh và Môi trường

```text
origin          https://github.com/9000000/NuvioTV-Fast.git
upstream        https://github.com/NuvioMedia/NuvioTV.git

tet2 (HEAD)     3979358bb
backup          backup-tet2-before-merge-20261114 (3979358bb)
local dev       fe5c314bd (sẽ cập nhật lên a3532b819)
origin/dev      a3532b819
upstream/dev    a3532b819
merge-base      65538103f
```

---

## 3. Phân nhóm chi tiết 101 Commit từ `dev` (2026-11-10 → 2026-11-14)

### 3.1 Nhóm Giao diện Phụ đề & Tải Phông chữ Ngoài (Subtitle Appearance & Fonts — 22 commit)
- **Công dụng**:
  - Giới thiệu màn hình `SubtitleAppearanceDialog` với tính năng **Live Preview** thời gian thực, cho phép người dùng thấy ngay hiệu ứng phông chữ, cỡ chữ, màu sắc, viền và bóng phụ đề.
  - Hỗ trợ tải phông chữ phụ đề tùy chỉnh qua Web Browser trong cùng mạng LAN (`SubtitleFontUploadServer.kt`, `SubtitleFontWebPage.kt`). Người dùng chỉ cần vào địa chỉ IP hiển thị trên TV để upload file TTF/OTF.
  - Bổ sung định dạng phông chữ MPV (`MpvSubtitleText.kt`), Letterbox Mask (`PlayerLetterboxMask.kt`) và Presentation styling nâng cao (`StyledSubtitlePresenter.kt`).

### 3.2 Nhóm Player Resilience, Formats & Source Fallback (21 commit)
- **Công dụng**:
  - **HLS SPS/PPS Parameter Set Init** (`HlsParameterSetInit.kt`, `HlsParameterSetInitDataSource.kt`): Giải quyết triệt để lỗi không có video codec initialization data trên các luồng HLS không gửi kèm SPS/PPS trong playlist.
  - **AV1 HDR10+ Stripper** (`Av1Hdr10PlusStripper.java`): Lọc bỏ metadata SEI HDR10+ trên stream AV1 đối với các thiết bị giải mã phần cứng không hỗ trợ, ngăn chặn crash và giật hình.
  - **Dolby Vision MKV Transformer**: Nâng cấp trích xuất và biến đổi định dạng MKV Dolby Vision tương thích Exoplayer/Media3.
  - **Source Fallback & Cached Source Refresh**: Tự động chuyển đổi nguồn stream dự phòng khi luồng hiện tại gặp lỗi HTTP hoặc định dạng hỏng, giảm thời gian chờ đợi của người dùng.
  - **Playback URL Credentials**: Hỗ trợ bóc tách và xác thực thông tin user/pass trực tiếp trong URL stream.

### 3.3 Nhóm Continue Watching, Trakt & Watch Progress (24 commit)
- **Công dụng**:
  - Bổ sung cài đặt hành động khi click vào thẻ Continue Watching (`ContinueWatchingClickAction`: `PLAY_DIRECTLY` hoặc `OPEN_DETAILS`).
  - Hỗ trợ xử lý Series không có Next-Up (`ContinueWatchingNoNextUpSeeds.kt`) và cơ chế làm mới ngày phát hành tập tiếp theo (`ContinueWatchingReleaseRefresh.kt`).
  - Trì hoãn đánh dấu watched item providers nhằm hạn chế nghẽn mạng và tăng độ mượt khi xem liên tục nhiều tập.
  - Đếm và gộp trạng thái watched-as-one (`WatchedAsOneItem.kt`).

### 3.4 Nhóm In-App Updater & Relaunch Receiver (10 commit)
- **Công dụng**:
  - Cơ chế tự khởi động lại ứng dụng sau khi cài đặt APK hoàn tất (`UpdateRelaunchReceiver.kt`, `UpdateInstallResultReceiver.kt`, `ApkInstaller.kt`).
  - Cải tiến giao diện Banner thông báo phiên bản mới (`UpdateBanner.kt`, `UpdateViewModel.kt`).

### 3.5 Nhóm Search & Navigation UI (8 commit)
- **Công dụng**:
  - Ghi nhớ nút input kích hoạt gần nhất (`lastInputFocusRequester` trong `SearchScreen.kt`), giúp khi nhấn Back hoặc di chuyển D-pad sẽ quay lại đúng nút tìm kiếm giọng nói hoặc ô nhập text.
  - Xử lý intent khởi động lại ứng dụng một cách an toàn (`LaunchIntentReplay.kt`, `isFreshLaunch`).
  - Quản lý trạng thái root route cho màn hình Discover (`discoverIsRoot`).

### 3.6 Nhóm Collections & Detail Screen Refactoring (6 commit)
- **Công dụng**:
  - Refactor hộp thoại chọn danh sách: chuyển từ `LibraryListPickerDialog` sang `PosterListPickerDialog` dùng chung trong toàn bộ app.
  - Nâng cấp tính năng cuộn chữ tự động (`AutoScrollingText.kt`) mượt mà hơn trên các thiết bị cấu hình yếu.

### 3.7 Nhóm Đa ngôn ngữ (i18n — 10 commit)
- **Công dụng**:
  - Cập nhật và bổ sung chuỗi dịch thuật cho 18 ngôn ngữ (`strings.xml`, `values-vi`, `values-es`, `values-fr`, `values-ja`, `values-nl`, v.v.).

---

## 4. Chiến lược Bảo toàn Tùy chỉnh của `tet2`

1. **Android Leanback Native & UI Tối ưu**:
   - Bảo toàn 100% hệ thống `ModernSidebarScaffold`, `InteropVerticalGridView`, `LeanbackColorEngine`, và D-pad throttle navigation.
2. **TorrServer, Live TV & Custom Routes**:
   - Bảo toàn hoàn toàn route `Screen.LiveTv`, `Screen.LiveTvSettings`, DRM `drmType`, `drmKey` trong `Screen.Player`.
3. **Hiệu năng & Khởi động nhanh**:
   - Giữ nguyên cơ chế tải 0ms cho lượt quét Continue Watching đầu tiên (`_initialCwResolved.value == false` thì debounce = 0ms).
4. **Tùy biến Phụ đề riêng**:
   - Bảo toàn bộ phông chữ tiếng Việt đẹp mắt (`PhimMoi`, `Inter`, `Open Sans`, v.v.) và thanh trượt tùy chỉnh độ dày viền chữ (`outline_width`).
