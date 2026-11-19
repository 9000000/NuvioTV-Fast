# Error Log

## [2026-11-19 11:35] - Lỗi Hàng Bên Dưới Tự Hiện Khung Viền Ở Poster Thứ 8 Khi Hàng Hiện Tại Load Xong

- **Type**: Logic
- **Severity**: High
- **File**: `app/src/main/java/com/nuvio/tv/ui/screens/home/ModernInteropHorizontalGridView.kt` & `app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRowAdapter.kt`
- **Agent**: mon
- **Root Cause**: 
  1. Khi hàng hiện tại hoàn tất tải dữ liệu từ placeholder (8 thẻ fake) sang danh sách thật, các ViewHolder placeholder cũ bị remove khỏi RecyclerView. Trong khoảng vài mili-giây trước khi thẻ mới được bind, thẻ đang có focus bị detach khiến Android Framework (`ViewRootImpl`) tự động tìm kiếm view fallback qua `FocusFinder.findNextFocus(DOWN)`.
  2. `FocusFinder` duyệt danh sách candidate views của hàng bên dưới từ cuối lên đầu (`mChildrenCount - 1`), trỏ trúng thẻ thứ 8 (child cuối cùng của 8 thẻ placeholder của hàng bên dưới).
  3. Hàng bên dưới vốn KHÔNG PHẢI là active row (`canTakeFocus?.invoke() == false`), nhưng biểu thức kiểm tra `mayTakeFocus(): Boolean = hasFocus() || canTakeFocus?.invoke() != false` đã sai lầm khi tự hợp thức hóa focus bằng `hasFocus()`. Đồng thời `descendantFocusability` của hàng bên dưới không bị khóa (`FOCUS_BLOCK_DESCENDANTS`), và `notifyCardFocused` / `requestChildFocus` không kiểm tra `mayTakeFocus()`. Do đó, thẻ thứ 8 của hàng bên dưới nhận focus, kích hoạt `floatingRingView?.moveToTarget` và làm xuất hiện thêm khung viền thứ 2 trên màn hình.
- **Error Message**: 
  ```
  User reported: "lỗi khi di chuyển xuống hàng mà poster chưa load xong và đợi khi load xong thì hàng bên dưới của hàng hiện tại hiện thêm một khung viền tự động trỏ vào poster thứ 8"
  ```
- **Fix Applied**: 
  1. Trong `ModernInteropHorizontalGridView.kt`:
     - Chuẩn hóa `mayTakeFocus(): Boolean = canTakeFocus?.invoke() != false`: Tuyệt đối không cho phép một hàng không active nhận quyền focus chỉ vì vô tình nhận được stray focus từ hệ thống.
     - Setter của `canTakeFocus`: Khi `canTakeFocus?.invoke() == false`, lập tức đặt `descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS` và gọi `floatingRingView?.hideRing(true)`. Chặn đứng 100% việc Android `FocusFinder` có thể tìm thấy bất kỳ thẻ con nào của hàng không active.
     - Kiểm tra `mayTakeFocus()` trong `notifyCardFocused`, `requestChildFocus`, và `onGlobalFocusChanged`: Nếu hàng không có quyền active, từ chối mọi yêu cầu focus và lập tức ẩn ring (`hideRing(true)`).
  2. Trong `LeanbackModernRowAdapter.kt`:
     - Trong `LeanbackCardViewHolder.init`: Bổ sung kiểm tra trong `root.onFocusChangeListener`, nếu `hasFocus` mà `gridView?.mayTakeFocus() == false`, lập tức gọi `root.clearFocus()` và return, không kích hoạt focus visual hay thông báo selection.
- **Prevention**: Trong giao diện Leanback TV nhiều hàng, luôn khóa quyền nhận focus của con cháu (`FOCUS_BLOCK_DESCENDANTS`) đối với tất cả các hàng không phải hàng đang active, không dùng `hasFocus()` để ghi đè quyền `mayTakeFocus`, và từ chối ngay lập tức mọi sự kiện stray focus rơi vào hàng không active.
- **Status**: Fixed

---

## [2026-11-19 11:15] - Lỗi Kẹt Cuộn Xuống & Phím Trái/Phải Chập Chờn Trên Màn Hình Home TV Box

- **Type**: Logic
- **Severity**: High
- **File**: `app/src/main/java/com/nuvio/tv/ui/screens/home/ModernInteropHorizontalGridView.kt` & `app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeRowsList.kt`
- **Agent**: mon
- **Root Cause**: 
  1. *Điều hướng ngang (Trái/Phải)*: `ACTION_UP` xóa sớm `pendingTargetPosition = NO_POSITION` và gọi lại `setSelectedPositionSmooth` khi Leanback chưa cuộn xong (`scrollState != SCROLL_STATE_IDLE`). Khi người dùng bấm phím tiếp theo, `currentPos` bị tính ngược về vị trí cũ (`selectedPosition`), dẫn tới nuốt phím. Đồng thời `ensureTargetChildFocused` bị return sớm do so sánh với `selectedPosition` khi `pendingTargetPosition` bị xóa.
  2. *Cuộn dọc (Xuống)*: `stepVerticalRow` gặp hàng catalog rỗng (`items.list.isEmpty()`) lập tức `return true` mà không nhảy qua hàng rỗng, khóa cứng người dùng tại hàng hiện tại.
  3. *Mất focus dọc*: `handoffOverlayToGrid` không dọn cờ `isVerticalGlideRef` khi generation không khớp, và `stepVerticalRow` không kích hoạt focus cho `overlayHost` trong lúc cuộn khiến các Android TV Box cấu hình yếu bị rơi focus và nuốt phím tiếp theo.
- **Error Message**: 
  ```
  User reported:
  - "trên một số box ở màn home không cuộn xuống được"
  - "và cả trái phải lúc được"
  ```
- **Fix Applied**: 
  1. Trong `ModernInteropHorizontalGridView.kt`:
     - Xóa việc reset `pendingTargetPosition` và bỏ gọi thừa `setSelectedPositionSmooth` / `ensureChildFocusAndRing` trong `ACTION_UP` (để `onChildViewHolderSelected` hoặc `SCROLL_STATE_IDLE` dọn dẹp khi thẻ thực sự hạ cánh).
     - Tối ưu hóa `currentPos` trong `ACTION_DOWN` luôn ưu tiên `pendingTargetPosition` rồi đến `resolvedHolderPos` và `resolvedSelectedPos`.
     - Sửa `ensureTargetChildFocused` chỉ hủy khi có `pendingTargetPosition` mới hơn khác với `targetPos`.
  2. Trong `ModernHomeRowsList.kt`:
     - Nâng cấp `stepVerticalRow` tự động quét tìm hàng kế tiếp không rỗng (bỏ qua hàng trống đang tải và trigger lazy load cho chúng).
     - Kích hoạt `overlayHost.activateGlideFocus()` trong lúc fixed step ring hoạt động để overlay host giữ và điều hướng phím an toàn.
     - Bổ sung fail-safe reset `isVerticalGlideRef` trong `handoffOverlayToGrid` khi generation đổi mà không còn job cuộn nào chạy.
     - Bổ sung `.onKeyEvent` cho `LazyColumn` làm lưới an toàn bắt DPAD_DOWN / DPAD_UP khi focus rơi vào Compose container.
- **Prevention**: Trong các danh sách lồng ghép Leanback HorizontalGridView và Compose LazyColumn trên TV, tuyệt đối không can thiệp xóa trạng thái điều hướng ngang trong sự kiện nhả phím `ACTION_UP`, luôn quét tìm hàng hợp lệ tiếp theo khi gặp hàng rỗng và duy trì focus an toàn trên overlay host trong suốt animation chuyển hàng.
- **Status**: Fixed

---

## [2026-11-18 21:30] - Lỗi Kết Nối Simkl Với Client ID Mới (unauthorized_client: V2 vs V1)

- **Type**: Integration
- **Severity**: High
- **File**: `app/src/main/java/com/nuvio/tv/data/simkl/SimklAuthRepository.kt:35`
- **Agent**: mon
- **Root Cause**: Simkl đã chuyển hệ thống đăng ký App sang OAuth 2.0 (AUTH V2). Các Client ID mới tạo trên Simkl bị server từ chối khi gọi vào endpoint cũ `GET /oauth/pin` của V1. Trong khi đó, mã nguồn NuvioTV chỉ hỗ trợ V1 và regex PIN code không cho phép ký tự gạch ngang `-` trong `user_code` của V2 (`XXXX-XXXX`).
- **Error Message**: 
  ```json
  {"error":"unauthorized_client","code":400,"message":"This client_id is an OAuth 2.0 app: use POST /oauth2/device instead of /oauth/pin"}
  ```
- **Fix Applied**: 
  1. Thêm trường `deviceCode` vào `SimklPinSession` và cập nhật `SimklPinResponse` để nhận đủ các trường của V2 OAuth 2.0 Device flow.
  2. Nâng cấp `startPinAuth()`: thử luồng V2 (`POST /oauth2/device`) trước, nếu không thành công thì fallback về V1 (`GET /oauth/pin`).
  3. Nâng cấp `pollPin()`: hỗ trợ polling qua `POST /oauth2/token` với mã `urn:ietf:params:oauth:grant-type:device_code` khi có `deviceCode`, xử lý các trạng thái `authorization_pending`, `slow_down`, `expired_token`, `access_denied`.
  4. Cập nhật `PIN_PATTERN = Regex("[A-Za-z0-9-]{4,16}")` để hỗ trợ định dạng mã người dùng V2 có dấu gạch ngang.
  5. Bổ sung trọn bộ 5 unit test kiểm thử V2 trong `SimklAuthRepositoryTest.kt` và chạy pass 100%.
- **Prevention**: Khi tích hợp các dịch vụ bên thứ ba (OAuth), luôn theo dõi các phiên bản API mới và thiết kế cơ chế fallback linh hoạt giữa các phiên bản API.
- **Status**: Fixed

---

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

## [2026-11-15 16:15] - Mất Phản Hồi D-Pad Khi Vào Live TV Do Chặn Key Phía Compose & Focus Conflict

- **Type**: Agent
- **Severity**: Critical
- **File**: `app/src/main/java/com/nuvio/tv/features/livetv/LiveTvGuideScreen.kt:736` & `app/src/main/java/com/nuvio/tv/features/livetv/LeanbackCategoryDrawer.kt:126`
- **Agent**: mon
- **Root Cause**: Khi chuyển đổi `CategoryDrawer` sang Leanback Native, root `Box` của Compose vẫn chiếm focus qua `focusRequester.requestFocus()` trong khi `onPreviewKeyEvent` lại đặt `if (focusArea == GuideFocusArea.DRAWER) return@onPreviewKeyEvent false` và xóa nhánh xử lý phím của Drawer. Do Compose root Box đang giữ focus hệ thống nhưng từ chối xử lý sự kiện phím, các lệnh phím D-Pad từ điều khiển từ xa (Lên, Xuống, Trái, Phải, OK) đều bị bỏ qua khiến toàn bộ màn hình bị đơ cứng.
- **Error Message**: 
  ```
  User reported: lỗi không thao tác được gì hết
  ```
- **Fix Applied**: 
  1. Khôi phục hoàn toàn bộ lắng nghe và điều phối phím D-Pad trong Compose root Box cho `GuideFocusArea.DRAWER`: Lên/Xuống cập nhật `drawerIndex`, Phải/OK gọi `selectGroupAndFocusChannels(drawerIndex)`.
  2. Đồng bộ hóa mượt mà: `LeanbackCategoryDrawer` lắng nghe `drawerIndex` và gọi `grid.setSelectedPositionSmooth(drawerIndex)` để cuộn mượt 60 FPS mà không can thiệp hay cướp quyền focus của Compose.
  3. Đặt `isFocusable = false` và `descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS` trên `InteropVerticalGridView` trong drawer để triệt tiêu hoàn toàn xung đột focus giữa Android View và Jetpack Compose.
- **Prevention**: Khi tích hợp View Interop (Compose + AndroidView), phải xác định rõ ràng thực thể nào là Single Source of Truth làm chủ sự kiện phím; không bao giờ trả về `return false` ở tầng cha nếu tầng con chưa được trao quyền focus hệ thống một cách chủ động.
- **Status**: Fixed

---

## [2026-11-17 11:30] - Viền Sáng Kênh Live TV Bị Nháy Lớp Mờ Phía Sau & Độ Dày Viền Phụ Đề Tối Đa Còn Thấp

- **Type**: Logic
- **Severity**: Medium
- **File**: `app/src/main/java/com/nuvio/tv/features/livetv/LiveTvGuidePanels.kt:459` & `app/src/main/java/com/nuvio/tv/data/local/PlayerSettingsDataStore.kt:218`
- **Agent**: mon
- **Root Cause**: 
  1. `GuideGridChannelTile` dùng hai lớp phát sáng xung đột: trong 40ms đầu (`isFocused && !showNeonBloom`) vẽ `instantGlowModifier` bằng Compose `drawBehind`, sau `delay(40L)` lại tắt nó và gắn `AndroidView(PlayStoreGlowView)` chạy `LAYER_TYPE_SOFTWARE` với 3 lớp `BlurMaskFilter`. Khi người dùng di chuyển D-pad nhanh, việc chuyển giao 40ms kết hợp render bitmap software phía sau thẻ tạo hiệu ứng nháy mờ và chèn lớp cũ; kèm theo `combinedClickable` không tắt `indication` mặc định và `LivePosterImage` khởi chạy lại `LaunchedEffect(url, onDominantColorExtracted)` mỗi lần recomposition do lambda không ổn định.
  2. Giới hạn độ dày viền phụ đề (`OUTLINE_WIDTH_MAX`) chỉ dừng ở `5`, chưa đủ dày trên màn hình TV lớn.
- **Error Message**: 
  ```
  User reported: độ dày viền phụ đề hơi ít hãy cho phép tăng từ 1 đến 15, tôi thấy viền sáng của các kênh trong mục live tv bị chèn cả lớp cũ đúng không tôi thấy bị nháy một lơp mờ phía sau
  ```
- **Fix Applied**: 
  1. Loại bỏ hoàn toàn `showNeonBloom`, `delay(40L)`, `instantGlowModifier` và `AndroidView(PlayStoreGlowView)` trong `GuideGridChannelTile`; thay bằng một lớp `focusGlowModifier` thuần GPU Compose (`drawBehind` vẽ 3 vòng `drawRoundRect` đồng tâm với `animatedGlowColor`), bổ sung `indication = null` cho `combinedClickable` và ổn định callback trong `LivePosterImage` bằng `rememberUpdatedState`.
  2. Nâng `SubtitleStyleSettings.OUTLINE_WIDTH_MAX` từ `5` lên `15` (giữ `OUTLINE_WIDTH_MIN = 1`, `OUTLINE_WIDTH_DEFAULT = 2`), mở rộng kẹp giá trị ASS/SSA trên MPV (`NuvioMpvSurfaceView`) lên `1..15` và cập nhật chuỗi mô tả `sub_outline_width_sub` (EN/VI).
- **Prevention**: Không kết hợp tráo đổi 2 cơ chế glow khác pipeline (Compose Canvas + `AndroidView` `LAYER_TYPE_SOFTWARE`) theo bộ đếm `delay` trên các item cuộn/chuyển focus liên tục bằng D-pad; luôn dùng một lớp vẽ GPU đồng nhất và tắt `indication` mặc định khi đã có viền focus tùy biến.
- **Status**: Fixed

---

## [2026-11-17 11:50] - Lưới Kênh Live TV Không Tự Động Cuộn Lên & Điều Chỉnh Lưới Thành 3 Cột

- **Type**: Logic
- **Severity**: High
- **File**: `app/src/main/java/com/nuvio/tv/features/livetv/LiveTvGuideScreen.kt:338` & `app/src/main/java/com/nuvio/tv/features/livetv/LiveTvGuideNavigation.kt:3`
- **Agent**: mon
- **Root Cause**:
  1. `LaunchedEffect` điều khiển `gridState` so sánh `targetVisible.offset.y < viewportStartOffset`, trong khi `viewportStartOffset` của `LazyVerticalGrid` bằng `-beforeContentPadding` (~`-36px` cho `18.dp`). Khi di chuyển lên một hàng đang bị lấp một phần trong `beforeContentPadding` (`-36 < offset.y < 0`) hoặc quay về hàng đầu tiên đang có `firstVisibleItemScrollOffset > 0`, điều kiện trả `false` khiến lưới không cuộn lên.
  2. `LaunchedEffect(channelIndex, ...)` tự hủy coroutine `scrollToItem`/`animateScrollToItem` liên tục khi giữ phím D-pad (`< 180ms`), bỏ qua sự kiện quay về từ `FullscreenPlayerOverlay` (`isFullscreen`), và dùng `.graphicsLayer { clip = false }` khiến hàng bị cắt tràn lên thanh header.
- **Error Message**:
  ```
  User reported: các kênh trong mục live tv không tự động cuộn lên sửa cho tôi , và sửa sửa hàng 4 kênh hiện tại thành 3 kênh để to dễ nhìn hơn
  ```
- **Fix Applied**:
  1. Tách hàm thuần `computeGridScrollAction(...)` kiểm tra đúng ranh giới nội dung sau `beforeContentPadding`/`afterContentPadding` (`0..(viewportEndOffset - afterContentPadding)`), luôn đưa hàng 0 về đỉnh `(0, 0)` và dùng `ScrollByOffset` vừa đủ để không nhảy cóc; dùng `snapshotFlow(...).collectLatest` theo dõi cả `isFullscreen` và phân biệt giữ phím D-pad với bấm từng nhịp qua `markGridDpadPress()`.
  2. Bật `clipToBounds()` trên `LazyVerticalGrid`, nâng `LIVE_TV_GUIDE_GRID_COLUMNS` từ `4` lên `3` kèm điều chỉnh khoảng cách/chữ/logo trong `GuideGridChannelTile` to dễ nhìn hơn.
- **Prevention**: Khi kiểm tra tính hiển thị của item trong `LazyGrid`/`LazyList` có `contentPadding`, luôn hiệu chỉnh ranh giới theo `beforeContentPadding`/`afterContentPadding` thay vì dùng thẳng `viewportStartOffset`/`viewportEndOffset`.
- **Status**: Fixed

---

## [2026-11-17 12:00] - Playlist Name Nhập Tiếng Việt Bị Lỗi Ký Tự Không Đọc Được

- **Type**: Integration
- **Severity**: High
- **File**: `app/src/main/java/com/nuvio/tv/core/server/LiveTvConfigServer.kt:162` & `app/src/main/java/com/nuvio/tv/core/server/LiveTvWebPage.kt:525`
- **Agent**: mon
- **Root Cause**:
  1. Trang cấu hình Web QR ([LiveTvWebPage.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/core/server/LiveTvWebPage.kt)) gửi request JSON chỉ đặt `Content-Type: application/json` (thiếu `; charset=utf-8`), trong khi [LiveTvConfigServer.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/core/server/LiveTvConfigServer.kt) gọi `session.parseBody(bodyMap)` của `NanoHTTPD 2.3.1`. Theo mặc định của `NanoHTTPD`, khi header `Content-Type` không có `charset=`, `NanoHTTPD` đọc luồng body bằng bảng mã `US-ASCII`, làm toàn bộ byte tiếng Việt UTF-8 biến thành ký tự thay thế không đọc được.
  2. `LiveTvRepository` mã hóa danh sách playlist vào Android `SharedPreferences` bằng ký tự điều khiển `\u001E`/`\u001F` (vốn không hợp lệ trong XML 1.0) và chưa URL-decode phần tên tệp UTF-8 trong `playlistNameFromUrl`.
- **Error Message**:
  ```
  User reported: playlist name tôi nhập tiếng việt bị lỗi hiển thị các kí tự không đọc được
  ```
- **Fix Applied**:
  1. Thay thế `session.parseBody` trong `LiveTvConfigServer` bằng `readUtf8Body(session)` đọc trực tiếp byte stream theo `StandardCharsets.UTF_8`, chuẩn hóa toàn bộ JSON response về `application/json; charset=utf-8`.
  2. Cập nhật `LiveTvWebPage` gửi `Content-Type: application/json; charset=utf-8`, đọc tệp M3U với `'UTF-8'` và escape HTML khi hiển thị tên/URL playlist.
  3. Cập nhật `encodePlaylists`/`decodePlaylists` trong `LiveTvRepository` sang JSON (tương thích ngược với dữ liệu `\u001E`/`\u001F` cũ) và URL-decode UTF-8 trong `playlistNameFromUrl`.
- **Prevention**: Không bao giờ gọi `NanoHTTPD.IHTTPSession.parseBody()` cho payload JSON/UTF-8; luôn đọc `inputStream` bằng `StandardCharsets.UTF_8` như mẫu ở `DebridFormatterConfigServer` / `StreamBadgeConfigServer`, và không dùng ký tự control ASCII `< 0x20` (trừ `\t`, `\n`, `\r`) khi lưu vào `SharedPreferences`.
- **Status**: Fixed

---

## [2026-11-17 21:28] - Thoát App Không Lưu List Kênh Vừa Xem & Mất Kênh Yêu Thích/Xem Gần Đây Khi Đổi List

- **Type**: Logic
- **Severity**: Medium
- **File**: `app/src/main/java/com/nuvio/tv/features/livetv/LiveTvGuideScreen.kt:197` & `app/src/main/java/com/nuvio/tv/features/livetv/LiveTvGuideNavigation.kt:121`
- **Agent**: mon
- **Root Cause**:
  1. `selectedPlaylistId` và `selectedGroupId` trong `LiveTvGuideContent` chỉ giữ bằng `rememberSaveable` khởi tạo cứng là `null`, không đọc/ghi xuống `LiveTvStorage`, đồng thời `LaunchedEffect` reset `selectedPlaylistId` về `null` khi `rawChannels` chưa load xong từ cache lúc khởi động app.
  2. `buildLiveTvChannelGroups` và `filterChannelsForGroup` lọc `FAVORITES` và `RECENT` trên `scopedChannels` (theo playlist đang chọn) thay vì toàn bộ danh sách kênh, khiến các kênh đã lưu biến mất khi chuyển sang list khác.
- **Error Message**:
  ```
  User reported: khi thoát app vào lại không lưu list kênh vừa xem mà lại hiện tất cả list sửa cho tôi lưu list vừa xem, mục yêu thích và xem gần đây sửa luôn hiển thị các kênh đã lưu vào đó bất kể có chuyển sang list khác vẫn luôn hiện lên cho tôi
  ```
- **Fix Applied**:
  1. Thêm `loadGuidePlaylistId`/`saveGuidePlaylistId` (kèm sentinel phân biệt `"Tất cả list kênh"` với trạng thái chưa chọn) và `loadGuideGroupId`/`saveGuideGroupId` vào `LiveTvStorage`; khôi phục khi vào `LiveTvGuideContent`, lưu ngay khi đổi list/danh mục/phát kênh, và chỉ kiểm tra tính hợp lệ của playlist khi `rawChannels.isNotEmpty() && !ui.isLoading`.
  2. Bổ sung tham số `allChannels` vào `buildLiveTvChannelGroups` và `filterChannelsForGroup` để `FAVORITES` và `RECENT` luôn hiển thị đầy đủ các kênh đã lưu trên mọi list, còn các danh mục riêng và `ALL` vẫn theo list đang chọn.
- **Prevention**: Không dùng `rememberSaveable` đơn thuần cho bộ lọc nguồn chính cần giữ qua các lần thoát app; luôn tách phạm vi dữ liệu toàn cục (`Favorites`/`Recent`) khỏi bộ lọc nguồn khi thiết kế danh mục.
- **Status**: Fixed

---





---

## [2026-11-18 10:32] - Lỗi Chữ Ký Nullability Của `BaseGridView.removeView` Khi Nâng Lên `androidx.leanback:1.2.0`

- **Type**: Syntax
- **Severity**: Low
- **File**: `app/src/main/java/com/nuvio/tv/ui/leanback/container/InteropHorizontalGridView.kt:276`, `InteropVerticalGridView.kt:251`, `ModernInteropHorizontalGridView.kt:430`
- **Agent**: mon
- **Root Cause**: Khi nâng cấp `androidx.leanback:leanback` từ `1.0.0` lên `1.2.0`, lớp cơ sở `BaseGridView` bổ sung annotation `@NonNull` cho tham số của `removeView(@NonNull View view)`, làm chữ ký override cũ `override fun removeView(view: View?)` báo lỗi mismatch với Kotlin 2.3.
- **Error Message**: 
  ```
  e: InteropHorizontalGridView.kt:276:5 'removeView' overrides nothing. Potential signatures for overriding: fun removeView(p0: View): Unit
  ```
- **Fix Applied**: Cập nhật chữ ký `override fun removeView(view: View)` trên cả 3 lớp con kế thừa `HorizontalGridView` và `VerticalGridView`.
- **Prevention**: Khi nâng phiên bản các thư viện AndroidX cũ (1.0.0 -> 1.2.0+), luôn kiểm tra thay đổi `@NonNull`/`@Nullable` trên các phương thức `ViewGroup` / `BaseGridView` được override trong Kotlin.
- **Status**: Fixed

---

## [2026-11-18 15:19] - Nhầm Tên Tham Số Đặt Tên (`defaultEpgIndex` vs `defaultScrollIndex`) Trong `LiveTvGuidePanels.kt`

- **Type**: Syntax
- **Severity**: Low
- **File**: `app/src/main/java/com/nuvio/tv/features/livetv/LiveTvGuidePanels.kt:652`
- **Agent**: mon
- **Root Cause**: Ở khối `factory` truyền đúng tên tham số `defaultScrollIndex = defaultEpgIndex` của `LeanbackVerticalEpgView.submitPrograms`, nhưng ở khối `update` bên dưới gõ nhầm tên tham số thành `defaultEpgIndex = defaultEpgIndex`.
- **Error Message**: 
  ```
  e: LiveTvGuidePanels.kt:652:25 No parameter with name 'defaultEpgIndex' found.
  e: LiveTvGuidePanels.kt:652:25 No value passed for parameter 'defaultScrollIndex'.
  ```
- **Fix Applied**: Đổi tham số tại `LiveTvGuidePanels.kt:652` thành `defaultScrollIndex = defaultEpgIndex`.
- **Prevention**: Đối chiếu đồng nhất chữ ký named arguments giữa hai khối `factory` và `update` của `AndroidView`.
- **Status**: Fixed

---

## [2026-11-18 15:52] - Lỗi Thanh Điều Hướng Khi Thay Bằng `LeanbackSidebarView` & `ModalNavigationDrawer` (Đã Rollback Theo Yêu Cầu)

- **Type**: Logic
- **Severity**: High
- **File**: `app/src/main/java/com/nuvio/tv/MainActivity.kt`, `app/src/main/java/com/nuvio/tv/ui/leanback/sidebar/LeanbackSidebarView.kt`
- **Agent**: mon
- **Root Cause**: 
  1. Đặt `modernSidebarEnabled` mặc định thành `false` đẩy người dùng sang `LegacySidebarScaffold` dùng `ModalNavigationDrawer` (`androidx.tv.material3`), vốn chỉ theo dõi Compose FocusNode và xung đột khi bên trong là `AndroidView`.
  2. `VerticalGridView` của Leanback dùng `WRAP_CONTENT` + keyline alignment và `LaunchedEffect` reset `pendingSidebarFocusRequest` trước khi `AndroidView.factory` khởi tạo xong khi `showExpandedPanel` mới bật, khiến `isBlockedContentKeys` chặn phím D-pad.
- **Error Message**: 
  ```
  User reported: thanh điều hướng bị lỗi / xóa hết các mã "làm lại thanh điều hướng như bản gốc và áp dụng Android Leanback Native , áp dụng Android Leanback Native vào trong mục live tv cho tôi" khôi phục lại
  ```
- **Fix Applied**: Đã xóa toàn bộ tệp mới (`ui/leanback/sidebar/`, `features/livetv/leanback/`, `LeanbackSidebarAndLiveTvTest.kt`) và khôi phục hoàn toàn `NativeSidebarView.kt`, `MainActivity.kt`, `LayoutPreferenceDataStore.kt`, `LayoutSettingsViewModel.kt`, `LayoutHomeSettings.kt`, `LiveTvGuidePanels.kt`, `LiveTvGuideScreen.kt`, `LiveTvChannelGrid.kt`, `LiveTvStates.kt` về trạng thái ổn định trước yêu cầu 2.
- **Prevention**: Không dùng `ModalNavigationDrawer` của `tv-material3` bọc `AndroidView` tự quản lý focus phần cứng, và không đặt `VerticalGridView` ở chế độ `WRAP_CONTENT` trong container ẩn/hiện theo `LaunchedEffect`.
- **Status**: Fixed

---
