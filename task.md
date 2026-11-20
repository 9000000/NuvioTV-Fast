# Danh Sách Task - Sửa Lỗi D-Pad Down Trên Điều Khiển Bluetooth / Hồng Ngoại Ở Màn Hình Home

> **Kế hoạch tương ứng**: [PLAN-fix-dpad-down-home-navigation.md](file:///f:/MyGithub/NuvioTV-Fast/PLAN-fix-dpad-down-home-navigation.md)

---

## 📋 Task Breakdown

- [x] **TASK-01**: Chống xung lặp sớm (`repeatCount = 1`) từ Remote IR / Bluetooth và chặn Snap-Backward trong `ModernHomeRowsList`
  - **Mô tả**:
    1. Thêm `lastVerticalSingleStepAtMs` và ngưỡng `VERTICAL_HOLD_GRACE_PERIOD_MS = 200L` trước khi kích hoạt `startOrExtendVerticalHoldScroll`, tránh việc xung `repeatCount = 1` của điều khiển IR / Bluetooth gọi `scrollJob?.cancel()` làm đứt gãy bước nhảy đơn.
    2. Trong `endVerticalHoldScroll()`, đảm bảo `landingIdx` không bao giờ lùi ngược về hàng trước `targetRowIndexTracker` (`maxOf` khi `dir > 0`, `minOf` khi `dir < 0`).
    3. Xử lý trực tiếp cả `repeatCount == 0` tại `LazyColumn.onPreviewKeyEvent`.
  - **File**: [ModernHomeRowsList.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeRowsList.kt)
  - **Trạng thái**: Completed

- [x] **TASK-02**: Đồng bộ ngưỡng bảo vệ repeat & mở khóa focus tức thời trong `ModernInteropHorizontalGridView`
  - **Mô tả**:
    1. Bổ sung `prepareForIncomingFocus()` mở `descendantFocusability = FOCUS_AFTER_DESCENDANTS` tức thì và bật cờ `pendingIncomingFocus = true` khi chuyển hàng.
    2. Giải phóng `pendingIncomingFocus = false` trong `prepareForRowChange()`.
  - **File**: [ModernInteropHorizontalGridView.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/ModernInteropHorizontalGridView.kt)
  - **Trạng thái**: Completed

- [x] **TASK-03**: Mở khóa `focusSearch` trục dọc cho `InteropHorizontalGridView` (Classic Home)
  - **Mô tả**: Sửa `focusSearch(focused, direction)` trong `InteropHorizontalGridView.kt` để chỉ khóa biên trái/phải nội bộ mà không chặn `View.FOCUS_DOWN` / `View.FOCUS_UP`, giúp bấm 1 lần ở Classic Home cũng chuyển hàng bình thường.
  - **File**: [InteropHorizontalGridView.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/container/InteropHorizontalGridView.kt)
  - **Trạng thái**: Completed

- [x] **TASK-04**: Kiểm thử hồi quy & xác nhận kịch bản Remote IR / Bluetooth
  - **Mô tả**: Chạy unit test suite `com.nuvio.tv.ui.screens.home.*` và kiểm tra kịch bản `ACTION_DOWN(repeatCount=0)` + `ACTION_DOWN(repeatCount=1)` + `ACTION_UP`.
  - **File**: `app/src/test/java/com/nuvio/tv/ui/screens/home/`
  - **Trạng thái**: Completed
