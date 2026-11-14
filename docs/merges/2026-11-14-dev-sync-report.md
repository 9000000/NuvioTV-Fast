# Báo cáo Kết quả Nghiệm thu Đồng bộ Nhánh `dev` → `tet2` (2026-11-14)

> **Trạng thái**: Đã hoàn tất đồng bộ 101 commit, giải quyết toàn bộ xung đột, bảo toàn 100% tùy chỉnh, vượt qua toàn bộ quá trình biên dịch và kiểm thử tự động (`BUILD SUCCESSFUL`).

---

## 1. Tóm tắt Kết quả Nghiệm thu

| Hạng mục | Kết quả |
|---|---|
| **Nhánh đích** | `tet2` |
| **Nhánh sao lưu an toàn** | `backup-tet2-before-merge-20261114` (commit `3979358bb`) |
| **Mốc cơ sở (`merge-base`)** | `65538103f` (2026-11-09) |
| **Mốc commit `upstream/dev`** | `a3532b819` (2026-11-14) |
| **Tổng số commit đồng bộ** | **101 commit** (67 commit thường + 34 merge commit) |
| **Số file thay đổi từ `dev`** | **94 file** |
| **Xung đột văn bản trực tiếp** | **9 file** (Đã giải quyết hoàn toàn) |
| **Lỗi biên dịch & Tài nguyên phát sinh** | **4 điểm** (Đã xử lý triệt để) |
| **Biên dịch Kotlin (`compileFullDebugKotlin`)** | **`BUILD SUCCESSFUL`** (0 lỗi cú pháp / symbol) |
| **Kiểm thử tự động (`testFullDebugUnitTest`)** | **`BUILD SUCCESSFUL`** (100% test pass) |
| **Tùy biến người dùng trên `tet2`** | **Bảo toàn nguyên vẹn 100%** |

---

## 2. Danh sách Test Cases Cốt lõi Đã Vượt qua

Trong quá trình kiểm thử tự động trên nhánh merge, các bài test đại diện cho những thay đổi mới của upstream và các tùy chỉnh của `tet2` đều PASS:

- `TraktAuthServiceTest` (Kiểm thử xác thực và làm mới token Trakt)
- `LaunchIntentReplayTest` (Kiểm thử an toàn khi nhận và tái phát Launch Intent)
- `Av1Hdr10PlusStripperTest` (Kiểm thử bộ lọc dữ liệu metadata AV1 HDR10+ cho thiết bị không hỗ trợ)
- `ContinueWatchingClickActionTest` (Kiểm thử tùy chọn click phát ngay hoặc mở trang chi tiết)
- `SourceFallbackTest` (Kiểm thử chuyển nguồn luồng phát dự phòng khi nguồn chính gặp lỗi)
- `HlsParameterSetInitTest` (Kiểm thử tự động nạp SPS/PPS cho luồng HLS)
- `StyledSubtitlePresenterTest` & `SubtitleFontValidationTest` (Kiểm thử hiển thị và xác thực phông chữ phụ đề mới)

---

## 3. Danh mục Tùy biến của `tet2` Được Bảo toàn

1. **Giao diện Modern Sidebar & Android Leanback Native**:
   - Hoàn toàn giữ nguyên `ModernSidebarScaffold` mượt mà, loại bỏ triệt để giật khung hình so với `LegacySidebarScaffold`.
   - Giữ nguyên toàn bộ cấu trúc Android Leanback Native tối ưu hiển thị danh sách và cuộn ngang/dọc.
2. **Hệ thống Điều hướng Tìm kiếm (SearchScreen D-pad Navigation)**:
   - Giữ nguyên 100% thuật toán D-pad repeat throttle, lưu trạng thái cuộn `rowState` trên từng hàng kết quả tìm kiếm giúp không bị lag hoặc văng trỏ chuột khi bấm phím nhanh.
3. **TorrServer, Live TV & Luồng DRM**:
   - Bảo toàn 100% các route `Screen.LiveTv`, `Screen.LiveTvSettings` cùng các tham số giải mã `drmType`, `drmKey` trong `Screen.Player`.
4. **Tốc độ Khởi động Hàng Continue Watching (0ms Debounce)**:
   - Bảo toàn logic `debounce { if (_initialCwResolved.value) CW_PROGRESS_DEBOUNCE_MS else 0L }` giúp giao diện hiển thị ngay lập tức khi mở app.
5. **Bộ Phông chữ Việt hóa & Tùy chỉnh Nét viền Phụ đề**:
   - Bảo toàn toàn bộ các phông chữ đẹp mắt (`PhimMoi`, `Inter`, `Open Sans`, v.v.).
   - Tích hợp thêm thanh trượt chỉnh độ dày nét viền (`outline_width`) trực tiếp vào màn hình `SubtitleAppearanceDialog` mới của upstream.
