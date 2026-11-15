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

## [2026-11-15 13:45] - Import Lỗi nativeKeyEvent & Kênh Đầu/Cuối Bị Cắt Nửa

- **Type**: Syntax
- **Severity**: Low
- **File**: `app/src/main/java/com/nuvio/tv/features/livetv/LiveTvGuideScreen.kt:61`
- **Agent**: mon
- **Root Cause**: 
  1. Thêm nhầm lệnh import `import androidx.compose.ui.input.key.nativeKeyEvent` trong khi `nativeKeyEvent` là thuộc tính thành viên (member property) sẵn có trên lớp `KeyEvent`.
  2. Khi di chuyển lên kênh đầu tiên (`channelIndex == 0`) hoặc kênh cuối cùng (`channelIndex == channels.lastIndex`), điều kiện cuộn kiểm tra `first > 0` và `last < channels.lastIndex` bị `false`, khiến danh sách không cuộn kịch biên, kết hợp thiếu `contentPadding` đáy làm item đầu và cuối bị mép màn hình che khuất một nửa.
- **Error Message**: 
  ```
  e: file:///F:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/features/livetv/LiveTvGuideScreen.kt:61:38 Unresolved reference 'nativeKeyEvent'.
  User reported: lỗi kênh đầu tiên và kênh cuối cùng không tự cuộn lên xuống để hiển thị hết chỉ thấy 1 nửa
  ```
- **Fix Applied**: 
  1. Xóa bỏ import sai `nativeKeyEvent`, thêm `@OptIn(ExperimentalFoundationApi::class)` cho tính năng `combinedClickable`.
  2. Bổ sung `contentPadding = PaddingValues(top = 6.dp, bottom = 56.dp)` cho `LazyColumn` kênh và `PaddingValues(vertical = 8.dp)` cho danh mục Drawer.
  3. Xử lý chuyên biệt trong `LaunchedEffect(channelIndex)`: Khi `channelIndex == 0` gọi `animateScrollToItem(0, 0)` kịch đỉnh; khi `channelIndex == channels.lastIndex` gọi `animateScrollToItem(channels.lastIndex, 0)` kịch đáy.
- **Prevention**: Kiểm tra chữ ký thuộc tính trên Compose trước khi import; luôn cấp khoảng thở `contentPadding(bottom)` cho `LazyColumn` trên TV để tránh bị mép màn hình cắt ngang nội dung.
- **Status**: Fixed

---

## [2026-11-15 13:55] - Timeline EPG Không Thể Cuộn Sang Phải Để Xem Thêm Chương Trình Tương Lai

- **Type**: Logic
- **Severity**: Medium
- **File**: `app/src/main/java/com/nuvio/tv/features/livetv/LiveTvGuideScreen.kt:315` & `LiveTvGuideScreen.kt:790`
- **Agent**: mon
- **Root Cause**: Cửa sổ thời gian EPG bị cố định cứng trong 2 tiếng xung quanh thời điểm hiện tại (`startWindow` cố định theo `now`). Khi người dùng bấm phím Phải tới chương trình cuối cùng của 2 tiếng đó, điều kiện `timelineProgramIndex < focusedVisiblePrograms.lastIndex` bị chặn lại và đứng yên tại chỗ, không có cơ chế dịch chuyển mốc thời gian sang tương lai. Đồng thời `loadEpgWindow` trong ViewModel chỉ nạp từ -6h đến +18h khiến dữ liệu các ngày sau không được đưa vào bộ nhớ.
- **Error Message**: 
  ```
  User reported: lỗi epg chỉ xem được vài thông tin không thể di chuyển sang phải để hiện thêm
  ```
- **Fix Applied**: 
  1. Mở rộng phạm vi nạp EPG trong `LiveTvViewModel.loadEpgWindow` lên từ -24h (hôm qua) đến +72h (3 ngày tới).
  2. Bổ sung biến trạng thái `windowOffsetMs`: Khi người dùng bấm phím Phải tại chương trình cuối cùng trong cửa sổ 2 tiếng hiện tại, hệ thống tự động dịch chuyển cửa sổ thời gian sang tương lai (`windowOffsetMs += halfHourMs`), trượt dải Canvas để hiển thị các chương trình tiếp theo tới tối nay, ngày mai và các ngày sau.
  3. Tương tự khi bấm phím Trái: Cho phép lùi từ tương lai về hiện tại, và lùi tiếp về quá khứ 24h để xem lại các chương trình đã phát (Catch-up).
  4. Phím Back trong Timeline: Khi đang ở tương lai/quá khứ (`windowOffsetMs != 0L`), bấm Back sẽ đưa ngay về mốc thời gian Trực tiếp hiện tại; bấm Back lần nữa sẽ quay lại cột danh sách kênh.
  5. Thước đo thời gian Time Ruler Bar tự động hiển thị ngày (ví dụ: `T2, 16/11 · 08:00`) khi người dùng cuộn sang ngày khác.
- **Prevention**: Thiết kế timeline EPG luôn phải hỗ trợ cơ chế Horizontal Window Paging / Offset để người dùng có thể duyệt toàn bộ lịch phát sóng đa ngày.
- **Status**: Fixed

---

## [2026-11-15 14:05] - Bấm Trái Bị Kẹt Lùi Thời Gian Không Về Được Kênh & Hiển Thị Quá Nhiều Chương Trình Đã Phát

- **Type**: Logic
- **Severity**: Medium
- **File**: `app/src/main/java/com/nuvio/tv/features/livetv/LiveTvGuideScreen.kt:790` & `LiveTvGuideScreen.kt:320`
- **Agent**: mon
- **Root Cause**: Khi người dùng bấm phím Trái trong Timeline, nhánh `else if (windowOffsetMs > -24 * 3600 * 1000L && ...)` luôn nhận giá trị `true` do dữ liệu EPG quá khứ luôn tồn tại, dẫn đến việc liên tục lùi thời gian (-30m, -1h, -2h...) tới 48 lần thay vì chuyển về cột kênh. Đồng thời giao diện tải tràn lan toàn bộ các chương trình đã phát từ hôm trước làm rối mắt.
- **Error Message**: 
  ```
  User reported: phát sinh thêm lỗi bấm sang trái lại không trỏ vào được kênh , sửa lại chỉ hiển thị 2 trương trình đã phát thay vì hiện lại rất nhiều
  ```
- **Fix Applied**: 
  1. Giới hạn hiển thị quá khứ: Chỉ lọc và giữ lại tối đa **2 chương trình đã phát gần nhất** (`takeLast(2)`) trước thời điểm hiện tại `now` trong `focusedVisiblePrograms` và `chPrograms`.
  2. Khóa không lùi thời gian âm: Khi ở mốc thời gian hiện tại (`windowOffsetMs == 0L`), bấm phím Trái lùi qua 2 chương trình đã phát là **chuyển ngay lập tức về cột kênh** (`focusArea = GuideFocusArea.CHANNELS`).
  3. Phím Back trong Timeline: Luôn reset `windowOffsetMs = 0L` và đưa focus trở về cột kênh ngay lập tức.
- **Prevention**: Tránh lùi thời gian vô hạn trên trục ngang nếu người dùng muốn thao tác điều hướng chuyển vùng (Focus Area Navigation); luôn giới hạn số lượng item quá khứ theo yêu cầu nghiệp vụ UX.
- **Status**: Fixed

---


