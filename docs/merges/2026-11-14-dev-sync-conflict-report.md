# Báo cáo Phân tích Xung đột & Giải pháp Kỹ thuật: `dev` → `tet2` (2026-11-14)

> Tài liệu này báo cáo chi tiết **9 xung đột văn bản trực tiếp (Git Conflicts)** và **4 điểm phát sinh kỹ thuật** trong quá trình đồng bộ 101 commit từ `upstream/dev` (`65538103f..a3532b819`) sang nhánh `tet2`.

---

## 1. Bảng tổng hợp Xử lý Xung đột

| # | File | Vùng xung đột | Mức độ | Quyết định kỹ thuật |
|---|---|---:|---|---|
| 1 | [`app/build.gradle.kts`](file:///f:/MyGithub/NuvioTV-Fast/app/build.gradle.kts) | 1 | Thấp | **Giữ `tet2` (`ours`)**: `versionCode = 1187`, `versionName = "1.4.4"` |
| 2 | [`MainActivity.kt`](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/MainActivity.kt) | 1 | Trung bình | **Kết hợp thông minh**: Loại bỏ `LegacySidebarScaffold` (vì `tet2` dùng `ModernSidebarScaffold`), tích hợp `isFreshLaunch` từ `LaunchIntentReplay` và `discoverIsRoot` |
| 3 | [`Screen.kt`](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/navigation/Screen.kt) | 2 | Trung bình | **Kết hợp đầy đủ**: Giữ toàn bộ custom `drmType`, `drmKey`, các route `LiveTv`, `LiveTvSettings` của `tet2`; bổ sung tham số `fromLinkCache` từ upstream |
| 4 | [`MetaDetailsScreen.kt`](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/detail/MetaDetailsScreen.kt) | 1 | Thấp | **Kết hợp**: Giữ hàm `resolveTargetEpisodeForSeason` (cần cho logic chọn tập), loại bỏ `LibraryListPickerDialog` thừa do upstream đã refactor sang `PosterListPickerDialog` |
| 5 | [`HomeViewModelContinueWatching.kt`](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/HomeViewModelContinueWatching.kt) | 1 | Trung bình | **Kết hợp tối ưu**: Giữ `debounce` 0ms lần đầu của `tet2` giúp app khởi động siêu tốc, tích hợp `cwLastCycleStartedAtMs` tracking từ upstream |
| 6 | [`SubtitleSelectionOverlay.kt`](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/SubtitleSelectionOverlay.kt) | 2 | Trung bình | **Kết hợp trọn vẹn**: Giữ tùy biến stepper `OutlineWidth` của `tet2`, áp dụng kiểu chữ Typography mới và đồng bộ bảng focus key từ upstream |
| 7 | [`SearchScreen.kt`](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/search/SearchScreen.kt) | 2 | Cao | **Bảo toàn D-pad custom + Tính năng mới**: Giữ 100% cơ chế D-pad repeat / throttle navigation mượt mà của `tet2`, kết hợp `lastInputFocusRequester` để nhớ nút input |
| 8 | [`PlaybackSettingsViewModel.kt`](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/settings/PlaybackSettingsViewModel.kt) | 1 | Thấp | **Mở rộng tương thích**: Giữ cả `setSubtitleFont` và `setSubtitleFontId`, tích hợp toàn bộ các thiết lập style mới (radius, shadow, thickness) |
| 9 | [`PlaybackSubtitleSettings.kt`](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/settings/PlaybackSubtitleSettings.kt) | 1 | Thấp | **Áp dụng code mới tốt hơn**: Mở hộp thoại `SubtitleAppearanceDialog` với Live Preview trực quan |

---

## 2. Chi tiết từng File Xung đột & Đánh giá Công dụng

### 2.1 File #1: `app/build.gradle.kts`
- **Nội dung xung đột**:
  - `tet2`: `versionCode = 1187`, `versionName = "1.4.4"`
  - `upstream/dev`: `versionCode = 1079`, `versionName = "1.1.0"`
- **Đánh giá & Xử lý**:
  - Nhánh `tet2` sở hữu phiên bản độc lập `1.4.4` phục vụ dự án `NuvioTV-Fast`.
  - **Quyết định**: Giữ nguyên `1.4.4` (`versionCode = 1187`) của `tet2`.

### 2.2 File #2: `app/src/main/java/com/nuvio/tv/MainActivity.kt`
- **Nội dung xung đột**:
  - Upstream cập nhật logic tính toán `LocalRootContentInset` trong hàm `LegacySidebarScaffold`.
  - Tuy nhiên, trên nhánh `tet2`, toàn bộ giao diện đã chuyển sang dùng `ModernSidebarScaffold` mượt mà và hiện đại hơn.
- **Đánh giá & Xử lý**:
  - Khối code xung đột trong `LegacySidebarScaffold` không còn cần thiết trên `tet2`.
  - Upstream có 2 điểm nâng cấp giá trị cao:
    1. Kiểm tra `isFreshLaunch` thông qua `LaunchIntentReplay.consumeInitialLaunchIntentIfFresh(...)` ngăn phát lại intent cũ gây giật lag hoặc mở sai màn hình khi khởi động lại.
    2. Cung cấp cờ `discoverIsRoot = Screen.Discover.route in rootRoutes` cho `NuvioNavHost`.
  - **Quyết định**: Bỏ khối legacy, tích hợp `isFreshLaunch` và `discoverIsRoot`.

### 2.3 File #3: `app/src/main/java/com/nuvio/tv/ui/navigation/Screen.kt`
- **Nội dung xung đột**:
  - `tet2` mở rộng route `Screen.Player` hỗ trợ `drmType` và `drmKey` phục vụ phát trực tiếp IPTV / OTT có bảo vệ bản quyền, đồng thời thêm route `LiveTv` và `LiveTvSettings`.
  - `upstream/dev` bổ sung tham số `fromLinkCache: Boolean = false` để tái sử dụng luồng stream đã cache.
- **Đánh giá & Xử lý**:
  - Cả hai tính năng đều quan trọng và không loại trừ nhau.
  - **Quyết định**: Hợp nhất hàm tạo route `Screen.Player.createRoute(...)` nhận cả `drmType`, `drmKey` và `fromLinkCache`, bảo toàn 100% các class route IPTV.

### 2.4 File #4: `app/src/main/java/com/nuvio/tv/ui/screens/detail/MetaDetailsScreen.kt`
- **Nội dung xung đột**:
  - Upstream refactor hộp thoại chọn thư viện: xóa bỏ `LibraryListPickerDialog` cục bộ và chuyển sang dùng `PosterListPickerDialog`. Quá trình tự động merge vô tình xóa hàm phụ trợ `resolveTargetEpisodeForSeason(...)`.
- **Đánh giá & Xử lý**:
  - Hàm `resolveTargetEpisodeForSeason(...)` là thành phần thiết yếu để chọn đúng tập đang phát dở hoặc tập 1 khi người dùng đổi season.
  - **Quyết định**: Giữ lại hàm `resolveTargetEpisodeForSeason(...)`, đồng thời sử dụng `PosterListPickerDialog` theo kiến trúc mới của upstream.

### 2.5 File #5: `app/src/main/java/com/nuvio/tv/ui/screens/home/HomeViewModelContinueWatching.kt`
- **Nội dung xung đột**:
  - Xung đột tại pipeline coroutine theo dõi tiến độ `watchProgressRepository.observeAll()`:
  - `tet2`: `.debounce { if (_initialCwResolved.value) CW_PROGRESS_DEBOUNCE_MS else 0L }` (giúp hiển thị hàng Tiếp tục xem ngay lập tức 0ms khi mới mở app, các lần cập nhật sau mới debounce 250ms).
  - `upstream/dev`: Thêm `cwLastCycleStartedAtMs = System.currentTimeMillis()` để theo dõi chu kỳ refresh.
- **Đánh giá & Xử lý**:
  - Cơ chế debounce 0ms của `tet2` là tùy biến hiệu năng cực tốt, giúp người dùng không cảm thấy ứng dụng bị trễ khi vào Home. Việc bổ sung biến đo thời gian của upstream giúp đồng bộ chính xác hơn.
  - **Quyết định**: Giữ nguyên `debounce { if (_initialCwResolved.value) ... else 0L }` và gán `cwLastCycleStartedAtMs = System.currentTimeMillis()`.

### 2.6 File #6: `app/src/main/java/com/nuvio/tv/ui/screens/player/SubtitleSelectionOverlay.kt`
- **Nội dung xung đột**:
  - `tet2` có bộ điều khiển tăng giảm độ dày viền phụ đề (`OutlineWidth`).
  - Upstream nâng cấp font-size stepper với thiết kế kiểu chữ (Typography) mới và chuẩn hóa hệ thống focus key (`StyleFocusKey`).
- **Đánh giá & Xử lý**:
  - **Quyết định**: Giữ trọn vẹn custom stepper `OutlineWidth` của `tet2`, áp dụng kiểu chữ hiện đại của upstream, và cập nhật chỉ số `styleListIndexForFocusKey` (index 7 gồm outline width) đảm bảo điều hướng D-pad chuẩn xác 100%.

### 2.7 File #7: `app/src/main/java/com/nuvio/tv/ui/screens/search/SearchScreen.kt`
- **Nội dung xung đột**:
  - `tet2` sở hữu thuật toán điều hướng D-pad chống lag, xử lý phím lặp (`DpadRepeatHandler`), và gán trạng thái cuộn `listState = rowState` cho từng hàng kết quả tìm kiếm.
  - Upstream bổ sung `lastInputFocusRequester` để ghi nhớ vị trí con trỏ (giọng nói / text) khi chuyển đổi.
- **Đánh giá & Xử lý**:
  - Tùy chỉnh D-pad của `tet2` là tính năng cốt lõi giúp SearchScreen chạy cực kỳ mượt mà trên Android TV. Tính năng `lastInputFocusRequester` của upstream rất tiện ích.
  - **Quyết định**: Giữ 100% logic điều hướng D-pad của `tet2`, tích hợp `lastInputFocusRequester` vào cả nút tìm kiếm giọng nói và bàn phím ảo.

### 2.8 File #8: `app/src/main/java/com/nuvio/tv/ui/screens/settings/PlaybackSettingsViewModel.kt`
- **Nội dung xung đột**:
  - `tet2` cung cấp `setSubtitleFont(fontName: String)`.
  - Upstream đổi sang `setSubtitleFontId(fontId: String)` kèm các hàm thiết lập bán kính nền (`setSubtitleBackgroundRadius`), độ bóng viền (`setSubtitleShadowEnabled`), độ dày viền (`setSubtitleOutlineThickness`).
- **Đánh giá & Xử lý**:
  - **Quyết định**: Giữ cả 2 hàm `setSubtitleFont` và `setSubtitleFontId` để đảm bảo tương thích ngược tuyệt đối với mọi thành phần gọi cũ, đồng thời tích hợp toàn bộ các hàm mới.

### 2.9 File #9: `app/src/main/java/com/nuvio/tv/ui/screens/settings/PlaybackSubtitleSettings.kt`
- **Nội dung xung đột**:
  - `tet2` trước đây mở menu chọn font danh sách đơn giản.
  - Upstream xây dựng màn hình hộp thoại chuyên dụng `SubtitleAppearanceDialog(SUBTITLE_APPEARANCE)` với Live Preview trực quan và hỗ trợ phông chữ ngoài.
- **Đánh giá & Xử lý**:
  - Màn hình mới của upstream trực quan và hiện đại hơn nhiều lần.
  - **Quyết định**: Chuyển sang kích hoạt `SubtitleAppearanceDialog`.

---

## 3. Các Điểm Phát sinh Kỹ thuật Đã Xử lý

1. **Trùng lặp Resource Strings (`AAPT2 Duplicate Error`)**:
   - *Hiện tượng*: Khi biên dịch, AAPT2 báo lỗi `Found item String/sub_font more than one time` trong `values/strings.xml` và `values-vi/strings.xml`.
   - *Khắc phục*: Xóa key trùng ở mục cài đặt cũ, thống nhất dùng định nghĩa chuẩn ở phần Subtitle Appearance mới, bảo toàn đầy đủ danh sách phông tiếng Việt tùy chỉnh của `tet2`.
2. **Xung đột Khai báo `OnSetSubtitleFont`**:
   - *Hiện tượng*: `PlayerUiState.kt` bị git tự động gộp cả `data class OnSetSubtitleFont(val font: String)` và `(val fontId: String)`.
   - *Khắc phục*: Giữ 1 data class chuẩn `OnSetSubtitleFont(val fontId: String)` và trong `PlayerRuntimeControllerPlaybackEvents.kt` gọi cả `setSubtitleFont` lẫn `setSubtitleFontId`.
3. **Tích hợp Tùy chỉnh Độ dày Viền vào Live Preview**:
   - *Hiện tượng*: Upstream có Live Preview nhưng không có thanh trượt chỉnh độ dày nét viền (`outline_width`).
   - *Khắc phục*: Trong `SubtitleAppearanceEditor.kt`, khi người dùng bật `style.outlineEnabled == true`, hiển thị ngay slider điều chỉnh độ dày nét viền (`Outline Width: 1 - 5`). Nhờ đó, người dùng vừa có Live Preview vừa chỉnh được nét viền theo ý muốn.
4. **Unit Test `TraktAuthServiceTest`**:
   - *Hiện tượng*: Khi chạy kiểm thử không có API credentials trong `local.properties`, test bị trượt ở nhánh kiểm tra rỗng.
   - *Khắc phục*: Đổi `class TraktAuthService` và hàm `hasRequiredCredentials()` sang `open` để unit test có thể mock trả về `true` trong môi trường kiểm thử CI/CD độc lập.
