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



---

## [2026-11-15 13:00] - Danh Mục Không Tự Cuộn & EPG Không Khớp Do Dùng Cache Cũ

- **Type**: Logic
- **Severity**: Medium
- **File**: `app/src/main/java/com/nuvio/tv/features/livetv/LiveTvGuideScreen.kt:570` & `app/src/main/java/com/nuvio/tv/features/livetv/LiveTvEpgSyncService.kt:30`
- **Agent**: mon
- **Root Cause**: 
  1. `LazyColumn` danh mục trong Drawer không được gắn `state = drawerListState`, nên khi di chuyển phím Lên/Xuống danh sách không tự cuộn theo con trỏ `drawerIndex`.
  2. Kênh đã được cache trước khi bổ sung `epgUrl` khiến hàm phân tích cú pháp M3U không chạy lại, dẫn đến `playlist.epgUrl` vẫn bị null và không kích hoạt đồng bộ EPG.
- **Error Message**: 
  ```
  User reported: các danh mục không tự động cuộn khi di chuyển lên xuống , tôi thấy có rất nhiều file mới đuọc tạo có cần thiết không, và lỗi epg vẫn không hiện gì cả
  ```
- **Fix Applied**: 
  1. Gắn `drawerListState` và `LaunchedEffect(drawerIndex)` để tự động cuộn mượt mà (`animateScrollToItem`) theo con trỏ danh mục.
  2. Xóa 4 file dư thừa (`LiveTvPlayerScreen.kt`, `LiveTvOverlayMode.kt`, `GuidePrograms.kt`, `LivePosterImage.kt`).
  3. Thêm hàm `syncAll()` tự động probe URL playlist phát hiện `url-tvg="..."` ngay cả khi playlist đã nằm trong cache, đồng thời chuẩn hóa case-insensitive mapping kênh.
- **Prevention**: Luôn gán scroll state và listener điều hướng cho mọi danh sách điều khiển bằng D-Pad trên Android TV; luôn có cơ chế probe fallback khi nạp dữ liệu từ cache cũ.
- **Status**: Fixed

---

## [2026-11-15 13:10] - Mở Rộng Live Preview Bị Load Lại Stream & Kênh Chạy Ngầm Khi Thoát

- **Type**: Logic
- **Severity**: Medium
- **File**: `app/src/main/java/com/nuvio/tv/features/livetv/LiveTvGuideScreen.kt:320`
- **Agent**: mon
- **Root Cause**: Khi người dùng nhấn mở rộng toàn màn hình từ Live Preview hoặc bấm phím OK trên kênh đang phát, ứng dụng kích hoạt điều hướng sang màn hình riêng `PlayerScreen`, dừng `LiveTvPlaybackController` và khởi tạo lại ExoPlayer mới làm luồng stream bị ngắt quãng, re-buffer từ đầu. Đồng thời khi thoát khỏi màn hình Live TV, nếu player không được dừng triệt để trong lifecycle `onDispose`, tiến trình phát audio/video vẫn tiếp tục chạy ngầm.
- **Error Message**: 
  ```
  User reported: khi xem live preview và mở rộng ra tại sao lại bị load lại; thoát khỏi live tv rồi nhưng kênh vẫn bị chạy ngầm
  ```
- **Fix Applied**: 
  1. Xây dựng chế độ Seamless Fullscreen trực tiếp trong `LiveTvGuideScreen`: chuyển trạng thái `isFullscreen = true/false` dùng chung duy nhất một instance `ExoPlayer` của `LiveTvPlaybackController`. Chuyển đổi giữa Preview và Toàn màn hình diễn ra tức thì 0ms, không re-buffer, không load lại.
  2. Tích hợp đầy đủ điều khiển D-Pad trong Fullscreen: phím `Back` thu nhỏ về TV Guide (luồng phát tiếp tục); phím `Up`/`Down` chuyển kênh nhanh (zapping); phím `Left` trượt Quick Channel Overlay; phím `OK` bật/tắt OSD Banner thông tin kênh và chương trình phát sóng.
  3. Bổ sung `LifecycleEventObserver` (bắt `ON_PAUSE`, `ON_STOP`) kết hợp `onDispose { viewModel.stopPlayback() }` trong `LiveTvGuideScreen`, đảm bảo ngắt phát và giải phóng tài nguyên triệt để ngay khi người dùng rời khỏi giao diện Live TV.
- **Prevention**: Tránh chuyển đổi Composable NavDestination đối với luồng video đang phát nếu không cần thiết; ưu tiên dùng state-driven fullscreen trong cùng context để chia sẻ playback surface mượt mà; luôn gắn lifecycle cleanup chặt chẽ với Composable scope.
- **Status**: Fixed

---

## [2026-11-15 13:25] - Lỗi Focus EPG Không Chính Xác & Giật Lag, Loạn Hiển Thị Khi Lướt Kênh

- **Type**: Logic
- **Severity**: High
- **File**: `app/src/main/java/com/nuvio/tv/features/livetv/LiveTvGuideScreen.kt:280` & `LiveTvGuideScreen.kt:1000`
- **Agent**: mon
- **Root Cause**: 
  1. **Lệch index EPG**: `timelineProgramIndex` trước đây thao tác trên toàn bộ danh sách `focusedPrograms` của cả tuần (hàng chục/trăm item), trong khi thanh Canvas `GuideRowStrip` chỉ vẽ các chương trình đã filter trong cửa sổ 2 tiếng (`startWindow` đến `endWindow`). Khi người dùng bấm sang phải, chương trình đang live nằm ở index 40 trong danh sách tuần, nhưng Canvas chỉ có 2-3 item (`pIdx = 0, 1, 2`), dẫn đến điều kiện `pIdx == timelineProgramIndex` luôn sai (không highlight được ô) hoặc khi focus vào index thấp thì tiêu đề ở Header hiển thị lịch phát của ngày hôm trước.
  2. **Gây giật lag nặng**: Trong `onPreviewKeyEvent`, mỗi lần bấm phím Lên / Xuống đều gọi trực tiếp `viewModel.play(ch)` làm `ExoPlayer` khởi tạo luồng giải mã liên tục, gây nghẽn UI thread.
  3. **Hiển thị loạn xạ**: `LazyColumn` kênh thiếu định danh `key = { _, ch -> ch.key }` khiến Compose tái sử dụng sai ViewHolder khi cuộn, kết hợp lệnh `scrollToItem` cưỡng bức nhảy giật màn hình mỗi bước.
- **Error Message**: 
  ```
  User reported: focus vào vào epg lỗi không chính xác , khi di chuyển lên xuống giữa các kênh bị lag và hiện loạn lên
  ```
- **Fix Applied**: 
  1. Đồng bộ hóa `focusedVisiblePrograms` theo đúng cửa sổ thời gian 2 tiếng hiển thị. Khi bấm phím Phải vào timeline, con trỏ tự động chọn đúng chương trình đang live trong cửa sổ đó. Khi bấm Lên/Xuống giữa các kênh trong timeline, tự động tính mốc thời gian `targetTime` để bám theo chương trình tương ứng của kênh mới.
  2. Loại bỏ hoàn toàn lệnh gọi `play()` tức thời khi bấm phím Lên / Xuống. Thay thế bằng cơ chế **Debounce 450ms** (`LaunchedEffect(channelIndex)`): khi người dùng lướt nhanh thì danh sách trượt mượt mà 60fps, chỉ khi dừng lại 450ms thì Live Preview mới nạp stream.
  3. Khai báo `key = { _, ch -> ch.key }` và bọc `remember` cho `chPrograms` trong `LazyColumn`. Áp dụng cuộn thông minh bám theo viewport (`animateScrollToItem` khi chạm biên trên/dưới) thay vì nhảy cóc cưỡng bức.
- **Prevention**: Luôn đảm bảo danh sách hiển thị và danh sách quản lý focus index có cùng kích thước và phạm vi; luôn dùng Debounce cho các tác vụ tải media nặng khi điều hướng phím nhanh; luôn gán key duy nhất cho Compose LazyColumn.
- **Status**: Fixed

---


