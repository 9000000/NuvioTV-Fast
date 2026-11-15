# Error Log

## [2026-11-15 09:28] - Poster Bị Khóa Cứng Không Di Chuyển Trái/Phải Được

- **Type**: Logic
- **Severity**: Critical
- **File**: `app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt:760`
- **Agent**: mon
- **Root Cause**: Trong `addOnChildViewHolderSelectedListener`, điều kiện kiểm tra `if (position != targetPos && targetPos > 0)` đã nhầm lẫn việc di chuyển phím ngang thông thường (D-pad LEFT/RIGHT) thành sự kiện chọn thẻ trung gian và cưỡng chế kéo giật `setSelectedPosition(targetPos)` về thẻ cũ, khiến người dùng bị khóa cứng tại chỗ.
- **Error Message**: 
  ```
  User reported: phát sinh lỗi lỗi poster bị khóa cứng không di chuyển được
  ```
- **Fix Applied**: Gỡ bỏ hoàn toàn logic chặn cưỡng chế trong `addOnChildViewHolderSelectedListener` và `ModernHomeRowsList.kt`. Giữ cơ chế bảo toàn vị trí xem chỉ can thiệp vào `requestFocus()` (khi chuyển hàng) và đồng bộ `initialFocusPosition` khi recycle view.
- **Prevention**: Không bao giờ chặn hoặc cưỡng chế ép ngược vị trí trong listener sự kiện cuộn/chọn con (`OnChildViewHolderSelectedListener`) vì listener này phục vụ cả việc điều hướng phím ngang thông thường của người dùng.
- **Status**: Fixed

---
