# Error Log

## [2026-11-21 09:02] - Đọc Trễ Câu Tiếp Theo Khi Bật "Chờ Đọc Xong Câu" (`waitForCueCompletion`) Trên Google TTS Offline

- **Type**: Logic
- **Severity**: High
- **File**: `app/src/main/java/com/nuvio/tv/core/tts/GoogleOfflineTtsEngine.kt:438`, `app/src/main/java/com/nuvio/tv/core/tts/SubtitleTtsCoordinator.kt:185`, `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerSidecarSubtitles.kt:272`, `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerStyledSubtitles.kt:17`
- **Agent**: mon
- **Root Cause**: (1) `GoogleOfflineTtsEngine.speakInternal()` gọi đồng bộ Binder IPC `currentTts.voices` và `currentTts.voice = ...` trên Main Thread cho MỖI câu phụ đề dù giọng không đổi, gây trễ 200–500ms mỗi lượt và làm tăng độ trễ dây chuyền. (2) `UtteranceProgressListener.onDone` trên Google TTS Offline có khoảng lặng cuối câu dài (400–900ms) hoặc có thể bị chậm/rớt, khiến `isSpeechActive` giữ `true` lâu hơn thực tế và ép câu sau phải đợi tới `MAX_CARRY_OVER_MS` (`1500ms`), đồng thời `onStop` của câu cũ dập nhầm `_isSpeaking` của câu mới. (3) Sidecar chưa truyền `nextCueStartMs` & `positionMs` thực tế; MPV chỉ cập nhật `subtitleCueTracker` trong vòng lặp `500ms` và không truyền `timingContext`.
- **Error Message**:
  ```
  User report: Bật chờ đọc xong câu thì bắt đầu đọc câu tiếp theo ở những câu ít bị ngắt vẫn bị đọc trễ lâu.
  ```
- **Fix Applied**: (1) Cache `cachedSystemVoices` & `appliedVoiceName`/`appliedLanguageTag`, chỉ đổi `currentTts.voice` khi giọng thật sự thay đổi, đẩy IPC sang `ttsExecutor` nền đơn luồng, sửa race `utteranceId` trong `UtteranceProgressListener` và expose `hasActiveUtteranceStarted()` + `isEngineSpeakingNow()`. (2) Thêm Speech Completion Watchdog + Dynamic Carry-Over (`computeDynamicCarryOverMs`) trong `SubtitleTtsCoordinator` & `SubtitleTtsTimingCalculator` để phát hiện ngay khi Google TTS đã dứt tiếng (`!isEngineSpeakingNow()`) hoặc câu cũ đã vượt quá thời lượng ước lượng (chỉ chờ đuôi tối đa `220ms` thay vì `1500ms`). (3) Truyền đầy đủ `nextCueStartMs` + `positionMs` trên Sidecar và đẩy `timingContext` tức thì trong `syncMpvStyledSubtitles()`.
- **Prevention**: Không gọi `TextToSpeech.voices` / `setVoice` lặp lại trên mỗi utterance; luôn có watchdog + dynamic carry-over cho `UtteranceProgressListener` trên Android TV và giữ unit test `dynamicCarryOverAvoidsLongDelayAfterEstimatedSpeechEnds`.
- **Status**: Fixed

---

## [2026-11-21 08:30] - Kẹt Giá Trị Cài Đặt TTS (`maxAutoSpeed`, `voiceVolume`) & Tự Động Mất Focus Trong `SubtitleTtsOverlay` Trên Player

- **Type**: Logic
- **Severity**: High
- **File**: `app/src/main/java/com/nuvio/tv/ui/screens/settings/PlaybackAudioSettings.kt:185`, `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerScreen.kt:578`, `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt:1321`, `app/src/main/java/com/nuvio/tv/ui/screens/player/SubtitleTtsOverlay.kt:94`
- **Agent**: mon
- **Root Cause**: (1) Danh sách bước nhảy ở Settings dùng mốc ngoài dải clamp (`1.25f` cho `maxAutoSpeed` có `min = 1.4f`; `1.25f/1.50f` cho `voiceVolume` có `max = 1.0f`) kết hợp `cycleNextFloat` tìm exact-match, dẫn đến vòng lặp bị clamp cố định ở `1.40x` / `100%`. (2) `showTtsOverlay` chưa được thêm vào các điều kiện loại trừ tương đương `showAudioOverlay` trong `PlayerScreen` (`LaunchedEffect` focus, `playerControlsVisible`, `panelOrDialogOpen`, `handleBackPress`) và `PlayerRuntimeControllerPlaybackEvents` (`scheduleHideControls`, `schedulePauseOverlay`, `OnShowTtsOverlay`, `OnDismissTransientOverlay`), khiến timer 3s ẩn controls cướp focus về `containerFocusRequester` và root `.onKeyEvent` nuốt phím D-Pad.
- **Error Message**:
  ```
  User report: Cài đặt tốc độ tăng tối đa không chỉnh được; các cài đặt TTS ở player không chỉnh được và tự động bị mất focus.
  ```
- **Fix Applied**: Chuẩn hóa 5 danh sách bước nhảy TTS và hàm `nextSteppedOption` (luôn tiến tới mốc lớn hơn kế tiếp trong dải clamp) làm Single Source of Truth trong `SubtitleTtsTimingCalculator`; thêm `showTtsOverlay` vào toàn bộ điểm kiểm tra focus/key/auto-hide/dismiss trong `PlayerScreen` và các `PlayerRuntimeController*`; nâng cấp `SubtitleTtsOverlay` đầy đủ 12 mục cài đặt cột phải bằng `LazyColumn` giữ focus ổn định và thêm unit test `steppedOptionsStayWithinClampBoundsAndCycleWithoutGettingStuck`.
- **Prevention**: Mọi danh sách option bước nhảy kết hợp DataStore clamp phải có unit test duyệt vòng khép kín qua hàm clamp tương ứng; mọi overlay mới của Player phải được kiểm tra đối chiếu 1-1 với tất cả vị trí xuất hiện `showAudioOverlay`.
- **Status**: Fixed

---

## [2026-11-20 17:23] - Lỗi Cú Pháp (`Syntax error: Expecting a top level declaration`) Khi Thêm Dialog Cài Đặt EPG trong `LiveTvSettingsScreen.kt`

- **Type**: Syntax
- **Severity**: Low
- **File**: `app/src/main/java/com/nuvio/tv/ui/screens/settings/LiveTvSettingsScreen.kt:756`
- **Agent**: mon
- **Root Cause**: Khi cập nhật hàm composable `LiveTvPlaylistDialog` để bổ sung trường nhập URL EPG và thêm dialog `LiveTvEpgDialog`, chuỗi thay thế kết thúc trước phần đuôi của nút `Action Save` cũ, dẫn đến 9 dòng đóng ngoặc và button thừa nằm ngoài phạm vi hàm.
- **Error Message**:
  ```
  e: file:///F:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/settings/LiveTvSettingsScreen.kt:756:25 Syntax error: Expecting a top level declaration.
  e: file:///F:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/settings/LiveTvSettingsScreen.kt:758:19 Functions which invoke @Composable functions must be marked with the @Composable annotation
  ```
- **Fix Applied**: Xóa 9 dòng code đóng ngoặc và nút bấm thừa ở cuối `LiveTvEpgDialog`, biên dịch lại sạch và chạy toàn bộ unit test thành công.
- **Prevention**: Luôn kiểm tra kỹ ranh giới đóng ngoặc của hàm composable khi thực hiện `edit_file` trên các khối code dài.
- **Status**: Fixed

---

## [2026-11-20 13:29] - Văng App (`ComposeRuntimeError: pending composition has not been applied`) Khi Gỡ Hàng `AndroidView` Đang Giữ Focus Lúc `Operation$RemoveNode`

- **Type**: Runtime
- **Severity**: Critical
- **File**: `app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeRowsList.kt:591`, `app/src/main/java/com/nuvio/tv/ui/screens/home/ModernInteropHorizontalGridView.kt:125`, `app/src/main/java/com/nuvio/tv/ui/screens/home/ModernRowFloatingFocusRingView.kt:326`, `app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt:233`
- **Agent**: mon
- **Root Cause**: Khi một hàng `PlaceholderCatalog` đang giữ View focus (hoặc bị `LazyColumn` cuộn ra ngoài viewport khi chuyển sang hàng chưa attach) bị gỡ khỏi cây Compose qua `Operation$RemoveNode` -> `LayoutNode.detach` -> `AndroidComposeView.removeAndroidView`, `ViewGroup.removeViewInternal(androidViewHolder)` kiểm tra thấy `androidViewHolder.hasFocus() == true` và gọi `rootViewRequestFocus()` -> `AndroidComposeView.requestFocus()` -> `TwoDimensionalFocusSearch` -> `searchBeyondBounds` -> `LayoutNodeSubcompositionsState.subcompose()` ngay giữa lúc `CompositionImpl.applyChanges()` đang chạy (khi `pending` composition chưa apply xong), ném `ComposeRuntimeError`. Lưu ý: `LayoutNode.detach` chạy `onDetach` (`removeAndroidView`) trước `NodeChain.runDetachLifecycle()`, nên `Modifier.Node.onDetach()` chạy quá muộn.
- **Error Message**:
  ```
  FATAL EXCEPTION: main (PID 6654, com.nuvio.tv.debug)
  androidx.compose.runtime.ComposeRuntimeError: Compose Runtime internal error. Unexpected or incorrect use of the Compose internal runtime API (pending composition has not been applied).
    at androidx.compose.ui.layout.LayoutNodeSubcompositionsState.subcompose(...)
    at androidx.compose.foundation.lazy.layout.LazyLayoutBeyondBoundsModifierLocal.layout(...)
    at androidx.compose.ui.focus.TwoDimensionalFocusSearchKt.generateAndSearchChildren(...)
    at androidx.compose.ui.platform.AndroidComposeView.requestFocus(...)
    at android.view.ViewGroup.removeViewInternal(ViewGroup.java:5629)
    at androidx.compose.ui.platform.AndroidComposeView.removeAndroidView(...)
    at androidx.compose.ui.node.LayoutNode.detach$ui(...)
    at androidx.compose.runtime.changelist.Operation$RemoveNode.execute(...)
  ```
- **Fix Applied**:
  1. Thêm `remember(carouselRows)` trong `ModernHomeRowsList.kt` chạy đồng bộ ngay trong pha Composition (trước `applyChanges()` / `Operation$RemoveNode`) để phát hiện các `rowGrids` sắp bị xóa hoặc trở nên rỗng nhưng đang `hasFocus() == true`, chuyển View focus ngay sang `fallbackGrid` (nếu đã attach) hoặc `verticalOverlayHostRef` (`activateGlideFocus()`), và đánh dấu `prepareForDetachment()` + `ModernRowContainerLayout.isDetaching = true` rồi `clearFocus()` nếu vẫn còn focus.
  2. Bổ sung `isDetaching` trên `ModernInteropHorizontalGridView` và `ModernRowContainerLayout` để không chặn `clearChildFocus` hoặc chạy lại `post { requestFocus() }` lúc hàng sắp detach, và reset `isDetaching = false` trong `LeanbackModernRow.update` / `prepareForIncomingFocus()`.
  3. Cập nhật `stepVerticalRow` và `onNavigateBack` trong `ModernHomeRowsList.kt` để luôn chuyển focus khỏi `prevGrid`/`currentGrid` (sang `targetGrid` hoặc `overlayHost.activateGlideFocus()`) trước khi `LazyColumn` cuộn hàng cũ ra ngoài màn hình.
- **Prevention**: Không bao giờ để một `AndroidViewHolder` bị `RemoveNode` hoặc bị recycle khi vẫn đang giữ Android View focus; luôn di dời hoặc xóa focus đồng bộ trong pha Composition (hoặc trước khi kích hoạt scroll off-screen).
- **Status**: Fixed

---

## [2026-11-20 13:25] - Mất Focus, Đóng Băng Điều Hướng DPAD & `FocusRelatedWarning` Khi Cuộn Qua Hàng Lazy Rỗng (`items=0`) Ở Màn Hình Home

- **Type**: Logic
- **Severity**: Critical
- **File**: `app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeContent.kt:460`, `app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeRowsList.kt:570`, `app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt:97`, `app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeRows.kt:148`
- **Agent**: mon
- **Root Cause**: 
  1. *Kẹt `activeRowKey` vào hàng đã bị xóa (`hadActiveRow -> null // Wait for data to reappear`)*: Khi người dùng cuộn qua các hàng catalog lazy (`HomeRow.PlaceholderCatalog`) như `plaviohd-live-plaviotv`, `plaviohd-live-streamz`, `cat_search` lúc đang ở trạng thái placeholder (có item giả) rồi server/cache trả về `items=0` hoặc lỗi khiến hàng bị gỡ bỏ khỏi `carouselRows.list`, `ModernHomeContent` giữ nguyên `focusHolder.activeRowKey` và `activeRowKey.value` trỏ tới key đã xóa. `effectiveActiveRowKey()` cũng chỉ kiểm tra tồn tại trên `navTargetRowKey`, không kiểm tra `activeRowKey.value` và `currentRowKeyTracker`. Hệ quả là mọi hàng còn lại đều có `isActiveRow() == false` (`FOCUS_BLOCK_DESCENDANTS`), Hero phía trên chuyển thành đen hoàn toàn, focus cửa sổ thành `null`, toàn bộ phím DPAD Lên/Xuống/Trái/Phải/OK bị vô hiệu hóa (chỉ còn phím Back hiện Toast thoát app).
  2. *`FocusRelatedWarning: FocusRequester is not initialized` tại `.focusRestorer`*: `rowFocusRequesters` trả về `FocusRequester` của hàng đã bị gỡ hoặc vừa cuộn ra ngoài viewport của `LazyColumn` (không còn `FocusRequesterNode` gắn trên cây Compose).
  3. *Rơi focus trong lúc handoff overlay / `LocalPendingFocus`*: `handoffOverlayToGrid` tắt `overlayHost` (`clearFocus()`) ngay trước khi hàng đích attach và `executeRowFocus` chưa gọi `prepareForIncomingFocus()`.
- **Error Message**: 
  ```
  User test on com.nuvio.tv.debug (v1.6.5, MuMu 127.0.0.1:16416):
  - PID 2197 (13:05:14..13:05:15): 20x System.out: FocusRelatedWarning: FocusRequester is not initialized
  - PID 3868 (13:06:01): Home catalog loaded plaviohd-live-plaviotv items=0 -> mất khung focus, hero đen, mọi phím DPAD đứng im -> người dùng bấm Back thoát app (Toast confirm_exit_toast) và báo "đa test xong và lỗi".
  ```
- **Fix Applied**: 
  1. Trong `ModernHomeContent.kt` & `ModernHomeRowsList.kt`: Thêm helper dùng chung `findNearestNonEmptyRowIndex(...)` để khi hàng đang giữ focus bị gỡ hoặc rỗng (nhưng vẫn còn các hàng khác trong `carouselRows`), tự động khôi phục focus, `activeRowKey`, `navTargetRowKey`, `heroItem` và `focusedCatalogSelection` về hàng còn dữ liệu gần nhất (ưu tiên hàng đang hiển thị trong viewport) thay vì chờ vô hạn.
  2. Trong `ModernHomeRowsList.kt`: Kiểm tra `effectiveActiveRowKey()` trên toàn bộ chuỗi fallback (`navTargetRowKey`, `activeRowKey`, `currentRowKeyTracker`, hàng hiển thị gần nhất), thêm `LaunchedEffect(carouselRows)` dọn key cũ và phục hồi focus ngay nếu hàng đích biến mất; theo dõi `attachedRowKeys` qua `DisposableEffect(row.key)` trong `itemsIndexed` để `focusRestorerRequester` chỉ trả `FocusRequester` đang gắn node hợp lệ; giữ `overlayHost` thêm tối đa 4 frame khi `grid == null` trong `handoffOverlayToGrid`.
  3. Trong `LeanbackModernRow.kt` & `ModernHomeRows.kt`: Gọi `prepareForIncomingFocus()` trước `requestFocus()` trong `executeRowFocus` và khi placeholder đổi sang dữ liệu thật.
- **Prevention**: Không bao giờ để `activeRowKey`/`navTargetRowKey` trỏ tới một row key không còn tồn tại trong danh sách hiển thị khi vẫn còn row khác khả dụng, và luôn kiểm tra `FocusRequester` đang gắn vào cây Compose trước khi trả cho `focusRestorer`.
- **Status**: Fixed

---

## [2026-11-20 08:10] - Văng App (`StackOverflowError`) Khi Cuộn / Tải Xuống Hàng Catalog Dưới Ở Màn Hình Modern Home

- **Type**: Runtime
- **Severity**: Critical
- **File**: `app/src/main/java/com/nuvio/tv/ui/leanback/holder/LeanbackBaseViewHolder.kt:27`, `app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRowAdapter.kt:105`, `app/src/main/java/com/nuvio/tv/ui/screens/home/ModernInteropHorizontalGridView.kt:93`, `app/src/main/java/com/nuvio/tv/ui/leanback/container/InteropHorizontalGridView.kt:130`
- **Agent**: fixer
- **Root Cause**: 
  1. *Vòng lặp đệ quy `clearFocus() -> rootViewRequestFocus() -> requestFocus()` trên `FocusIsolatedRootLayout`*: `LeanbackCardViewHolder.root` sử dụng `FocusIsolatedRootLayout` vốn override `getRootView(): View = this` để ngăn `rootViewRequestFocus()` nhảy lên `DecorView`/`AndroidComposeView`. Tuy nhiên, khi `LazyColumn` cuộn và subcompose làm `AndroidComposeView.clearOwnerFocus()` gọi `clearFocus()` xuống cây View của hàng đã không còn active (`mayTakeFocus() == false`), hoặc khi `LeanbackCardViewHolder` `onFocusChangeListener` từ chối stray focus bằng `root.clearFocus()`, hàm `View.clearFocusInternal()` của Android gọi `rootViewRequestFocus()` -> `getRootView().requestFocus()` -> gọi ngược lại `root.requestFocus()` trên chính thẻ đang bị clear. Thẻ nhận lại focus (`hasFocus = true`), `onFocusChangeListener` lại thấy `mayTakeFocus() == false` rồi gọi `root.clearFocus()` tạo thành đệ quy vô tận gây `StackOverflowError` (8MB stack).
  2. *Gán sớm `items = newItems` trong `LeanbackModernRowAdapter.applyItems` & đồng bộ focus khi `bind`*: `applyItems` gán `items = newItems` trước khi kiểm tra `rv.isComputingLayout` rồi mới `rv.post { applyItemsInternal(...) }`, có thể làm lệch `getItemCount()` với snapshot của `RecyclerView`. Đồng thời `LeanbackCardViewHolder.bind()` và `onScrollSettled()` chưa kiểm tra `mayTakeFocus()` trước khi giữ/cấp focus visual.
- **Error Message**: 
  ```
  java.lang.StackOverflowError: stack size 8188KB
    at com.nuvio.tv.ui.screens.home.ModernHomeRowsListKt$ModernHomeRowsList$8$1$4$1$3$isActiveRowLambda$1$1.invoke(ModernHomeRowsList.kt:1468)
    at com.nuvio.tv.ui.screens.home.ModernRowContainerLayout.clearChildFocus(ModernRowFloatingFocusRingView.kt:340)
    at com.nuvio.tv.ui.screens.home.ModernInteropHorizontalGridView.clearChildFocus(ModernInteropHorizontalGridView.kt:150)
    at com.nuvio.tv.ui.screens.home.LeanbackCardViewHolder._init_$lambda$0(LeanbackModernRowAdapter.kt:109)
    at android.view.View.onFocusChanged(View.java:8769)
    at android.view.View.rootViewRequestFocus(View.java:8611)
    at android.view.View.clearFocusInternal(View.java:8597)
    at com.nuvio.tv.ui.screens.home.LeanbackCardViewHolder._init_$lambda$0(LeanbackModernRowAdapter.kt:109)
  ```
- **Fix Applied**: 
  1. Trong `FocusIsolatedRootLayout` (`LeanbackBaseViewHolder.kt`): Thêm `clearFocusDepth` bọc `clearFocus()` và chặn `requestFocus()` trả `false` khi đang ở bên trong `clearFocus()`, vừa giữ cô lập khỏi `DecorView`/`AndroidComposeView` vừa ngăn `rootViewRequestFocus()` tự cấp lại focus cho chính thẻ vừa clear.
  2. Trong `ModernInteropHorizontalGridView.kt` & `InteropHorizontalGridView.kt`: Áp dụng cơ chế `clearFocusDepth` tương tự để chặn self-refocus trong `clearFocus()`, đồng thời dọn `clearFocus()` nếu `canTakeFocus` chuyển sang `false` mà grid vẫn giữ zombie focus (và không có `pendingIncomingFocus`).
  3. Trong `LeanbackModernRowAdapter.kt`:
     - Khi từ chối stray focus ở `onFocusChangeListener`, khóa `FOCUS_BLOCK_DESCENDANTS` và gọi `updateFocusVisual(false)` cùng `root.clearFocus()`.
     - Kiểm tra `mayTakeFocus()` trong `bind()` và `onScrollSettled()` trước khi `requestFocus()` hoặc `updateFocusVisual(true)`.
     - Hoãn cập nhật `items = newItems` trong `applyItems()` qua `pendingItems` + `scheduleFlushPendingItems(rv)` khi `rv.isComputingLayout || rv.isInLayout` để luôn đồng bộ với `RecyclerView`.
- **Prevention**: Khi override `getRootView(): View = this` trên bất kỳ `View`/`ViewGroup` focusable nào để cô lập focus khỏi Compose/DecorView, luôn phải chặn `requestFocus()` trong lúc đang thực thi `clearFocus()`, vì `View.clearFocusInternal()` mặc định gọi `getRootView().requestFocus()`.
- **Status**: Fixed

---

## [2026-11-19 21:55] - Lỗi Chỉ Nhấn Giữ Nút Di Chuyển Xuống Mới Xuống Được Ở Màn Hình Home Trên Một Vài Điều Khiển Bluetooth / Hồng Ngoại

- **Type**: Logic
- **Severity**: High
- **File**: `app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeRowsList.kt`, `app/src/main/java/com/nuvio/tv/ui/screens/home/ModernInteropHorizontalGridView.kt`, `app/src/main/java/com/nuvio/tv/ui/leanback/container/InteropHorizontalGridView.kt`
- **Agent**: mon
- **Root Cause**: 
  1. *Xung đột Early Repeat Cancel & Snap-Back trên điều khiển Hồng ngoại (IR) / Bluetooth*: Theo giao thức IR NEC và một số remote Bluetooth HID có `repeat_delay` ngắn, một lần bấm nút Xuống bình thường (~80-130ms) phát ra `ACTION_DOWN(repeatCount = 0)` rồi kèm theo 1 xung `ACTION_DOWN(repeatCount = 1)` chỉ sau ~40-100ms trước khi người dùng nhấc tay (`ACTION_UP`). Do code kích hoạt `startOrExtendVerticalHoldScroll` ngay từ `repeatCount == 1` (`event.repeatCount > 0`), nó lập tức gọi `scrollJob?.cancel()` chặt đứt bước nhảy 1 hàng (`stepVerticalRow(1)`). Khi người dùng nhả phím (`ACTION_UP`), `endVerticalHoldScroll()` thấy Hàng 0 chưa cuộn qua 25% chiều cao hàng nên bỏ qua `targetRowIndexTracker` (= 1), gán `landingIdx = 0` và kéo giật màn hình ngược trở về Hàng 0.
  2. *Bất đối xứng tại `LazyColumn.onPreviewKeyEvent` & Khóa `FOCUS_BLOCK_DESCENDANTS`*: `LazyColumn.onPreviewKeyEvent` chỉ bắt `repeatCount > 0` mà bỏ qua `repeatCount == 0`, đồng thời `targetGrid` chưa được mở khóa `descendantFocusability = FOCUS_AFTER_DESCENDANTS` tức thì trên UI thread trước khi `requestFocus()`.
  3. *Classic Home (`InteropHorizontalGridView.focusSearch`)*: Hàm `focusSearch` chặn cả hướng dọc (`FOCUS_DOWN` / `FOCUS_UP`) không cho thoát ra khỏi hàng khi `onNavigateDown` không được truyền.
- **Error Message**: 
  ```
  User reported:
  - "hiện tại chỉ có 1 vài box android tv báo lỗi chỉ nhấn giữ nút di chuyển xuống mới xuống được ở màn hình home , các mục khác hoạt động bình thường"
  - "kiểm tra lại tôi cho rằng không phải do box cấu hình yếu mà do điều khiển dùng bluetooth hoặc dùng hồng ngoại"
  ```
- **Fix Applied**: 
  1. Trong `ModernHomeRowsList.kt`:
     - Thêm cơ chế bảo vệ bước nhảy đơn (`VERTICAL_HOLD_GRACE_PERIOD_MS = 200L` với `lastVerticalSingleStepAtMsRef`): Khi nhận xung lặp sớm (`repeatCount > 0`) trong vòng 200ms kể từ bước nhảy đơn `repeatCount = 0`, chỉ consume sự kiện (`return true`) mà không gọi `scrollJob?.cancel()`, bảo vệ bước nhảy 1 hàng hoàn tất trọn vẹn.
     - Trong `endVerticalHoldScroll()`: Chặn tuyệt đối Snap-Backward (`landingIdx = maxOf(rawLandingIdx, targetRowIndexTracker)` khi đi xuống và `minOf(rawLandingIdx, targetRowIndexTracker)` khi đi lên).
     - Trong `LazyColumn.onPreviewKeyEvent`: Xử lý trực tiếp cả `repeatCount == 0` (`stepVerticalRow`) tại cùng cửa ngõ với `repeatCount > 0`.
  2. Trong `ModernInteropHorizontalGridView.kt`:
     - Bổ sung `prepareForIncomingFocus()` mở khóa tức thời `descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS` và cờ `pendingIncomingFocus = true` ngay khi `stepVerticalRow` hoặc `handoffOverlayToGrid` chọn hàng đích.
  3. Trong `InteropHorizontalGridView.kt`:
     - Cho phép `focusSearch` ủy quyền lên `parent` đối với `View.FOCUS_DOWN` và `View.FOCUS_UP`.
- **Prevention**: Khi kết hợp điều hướng từng bước (Single Step) và nhấn giữ cuộn nhanh (Hold Fast Scroll) trên Android TV, luôn đặt khoảng thời gian bảo vệ (grace period >= thời gian animation bước đơn) trước khi hủy job bước đơn để tương thích với các điều khiển Hồng ngoại (IR NEC) và Bluetooth phát xung `repeatCount = 1` sớm, đồng thời không bao giờ cho phép vị trí hạ cánh khi nhả phím lùi ngược về sau đích đến của bước đơn.
- **Status**: Fixed

---

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

## [2026-11-22 10:59] - Crash bản Release ngay khi mở app do R8 obfuscate `CachedInProgressItem` / `CachedNextUpItem`

- **Type**: Runtime
- **Severity**: Critical
- **File**: `app/src/main/java/com/nuvio/tv/data/local/ContinueWatchingEnrichmentCache.kt:47`, `app/proguard-rules.pro:43`
- **Agent**: mon
- **Root Cause**: `CachedInProgressItem` và `CachedNextUpItem` được serialize/deserialize bằng Gson nhưng không có `@Keep` và không có trong `proguard-rules.pro`. Mỗi lần build bản Release mới, R8 đổi tên field khiến Gson đọc file cache cũ ra `contentId = null` (qua Unsafe), làm `HomeViewModel.loadContinueWatching()` ném `NullPointerException: Parameter specified as non-null is null: parameter contentId` khi gọi `watchProgressRepository.isDroppedShow(it.contentId)`.
- **Error Message**:
  ```
  java.lang.NullPointerException: Parameter specified as non-null is null: method ha.hg.u, parameter contentId
      at ha.hg.u(SourceFile:3)
      at mb.j4.invokeSuspend(SourceFile:959)
  ```
- **Fix Applied**: Thêm `@Keep` và `-keep` trong `proguard-rules.pro` cho `CachedNextUpItem`, `CachedInProgressItem`, `PendingProgressUpsert`, `PendingWatchedUpsert` và các model Gson Live TV; bổ sung bước lọc tự chữa lành (`sanitizeInProgressItem`/`sanitizeNextUpItem` tự xóa file cache obfuscated cũ) trong `ContinueWatchingEnrichmentCache.kt`; bọc `runCatching` trong `HomeViewModel.loadContinueWatching()`.
- **Prevention**: Mọi data class lưu đĩa bằng Gson bắt buộc phải gắn `@Keep` (hoặc `@SerializedName`) và có lọc phòng thủ null-safety khi đọc cache từ đĩa.
- **Status**: Fixed

---

## [2026-11-22 11:18] - Crash bản Release khi bấm vào mục Live TV do dữ liệu Gson cũ bị obfuscated bởi R8

- **Type**: Runtime
- **Severity**: Critical
- **File**: `app/src/main/java/com/nuvio/tv/features/livetv/LiveTvRepository.kt:911`, `LiveTvStorage.kt:128`, `LiveTvCacheManager.kt:92`, `LiveTvModels.kt`, `app/proguard-rules.pro`
- **Agent**: mon
- **Root Cause**: Các model Live TV (`LiveTvPlaylist`, `LiveTvChannel`, `LiveTvSettings`, `LiveTvStalkerSettings`, `LiveTvXtreamSettings`, `CatchupInfo`, `DrmInfo`, `LiveTvPlaylistType`) trước đây không có `@Keep`, nên dữ liệu đã lưu trong SharedPreferences & file `live_tv_channels_cache.json` ở bản Release cũ mang tên field obfuscated. Khi bản Release mới giữ tên field thật, Gson đọc JSON cũ qua Unsafe để các trường non-null (`id`, `source`, `type`, `streamUrl`, `portalUrl`, `guideSize`...) thành `null` → ném `NullPointerException` ngay khi vào Live TV.
- **Fix Applied**: Gắn `@Keep` + bổ sung `-keep` trong `proguard-rules.pro` cho cả nested models (`CatchupInfo`, `DrmInfo`, `EpgAssignment`, `LiveTvPlaylistType`); thêm lọc phòng thủ qua `Any?` trong `decodePlaylists()` (tự rơi về `legacyUrl`), `LiveTvStorage` (sanitize 3 settings), và `LiveTvCacheManager.loadChannels()` (tự xóa file cache kênh cũ hỏng để tự nạp lại).
- **Prevention**: Khi thêm `-keep` cho bất kỳ model Gson nào đã từng chạy ở Release không có `-keep`, BẮT BUỘC phải kèm lớp sanitize kiểm tra null qua `Any?` ở chỗ đọc từ đĩa/SharedPreferences để không làm văng app của người dùng nâng cấp đè từ bản cũ.
- **Status**: Fixed

---


## [2026-11-22 12:01] - Thiếu import extension `requestFocusAfterFrames` trong `FolderDetailScreen.kt`

- **Type**: Syntax
- **Severity**: Low
- **File**: `app/src/main/java/com/nuvio/tv/ui/screens/collection/FolderDetailScreen.kt:290`
- **Agent**: mon
- **Root Cause**: Gọi extension `FocusRequester.requestFocusAfterFrames()` trong `FolderDetailScreen.kt` mà chưa import `com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames`.
- **Error Message**: 
  ```
  e: file:///F:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/collection/FolderDetailScreen.kt:290:54 Unresolved reference 'requestFocusAfterFrames'.
  ```
- **Fix Applied**: Thay bằng `repeat(2) { androidx.compose.runtime.withFrameNanos { } }` kèm `runCatching { tabFocusRequesters.getOrNull(targetIdx)?.requestFocus() }` trực tiếp không cần phụ thuộc package `detail`.
- **Prevention**: Kiểm tra danh sách imports đầu file trước khi sử dụng extension hàm nội bộ từ package khác.
- **Status**: Fixed

---

## [2026-11-22 12:02] - Thiếu tham số `sortTimestamp` trong constructor `NextUpInfo` ở `LeanbackCoreOptimizationTest.kt`

- **Type**: Syntax
- **Severity**: Low
- **File**: `app/src/test/java/com/nuvio/tv/ui/leanback/LeanbackCoreOptimizationTest.kt:870`
- **Agent**: mon
- **Root Cause**: Khởi tạo `NextUpInfo` trong unit test mới mà chưa truyền tham số bắt buộc `sortTimestamp: Long`.
- **Error Message**: 
  ```
  e: file:///F:/MyGithub/NuvioTV-Fast/app/src/test/java/com/nuvio/tv/ui/leanback/LeanbackCoreOptimizationTest.kt:870:13 No value passed for parameter 'sortTimestamp'.
  ```
- **Fix Applied**: Thêm `sortTimestamp = 1000L` vào constructor `NextUpInfo` trong test.
- **Prevention**: Kiểm tra định nghĩa data class khi viết mock/fixture dữ liệu mới trong unit test.
- **Status**: Fixed

---

## [2026-11-22 12:04] - `registerAdapterDataObserver` không nhận callback trên JVM Unit Test do `android.database.Observable` stub

- **Type**: Process
- **Severity**: Low
- **File**: `app/src/test/java/com/nuvio/tv/ui/leanback/LeanbackCoreOptimizationTest.kt:844`
- **Agent**: mon
- **Root Cause**: Trên môi trường pure JVM Unit Test (`returnDefaultValues = true`, không dùng Robolectric), `android.database.Observable.registerObserver()` trong `android.jar` là stub rỗng nên không lưu observer vào `mObservers`.
- **Error Message**: 
  ```
  LeanbackCoreOptimizationTest > modern row adapter detects watched state changes in place even when item list reference is unchanged FAILED
      java.lang.AssertionError at LeanbackCoreOptimizationTest.kt:844
  ```
- **Fix Applied**: Cho `LeanbackRowAdapter.syncWatchedStateChanges()` trả về `List<Int>` các vị trí vừa thay đổi trạng thái watched và kiểm chứng trực tiếp kết quả trả về trong unit test.
- **Prevention**: Không dùng `RecyclerView.Adapter.registerAdapterDataObserver` trong pure JVM test không có Robolectric; trả về danh sách thay đổi hoặc verify trực tiếp trên method/ViewHolder.
- **Status**: Fixed

---

## [2026-11-22 13:16] - Thiếu import LeanbackThemeStyle và assertFalse trong LeanbackCoreOptimizationTest

- **Type**: Syntax
- **Severity**: Low
- **File**: `app/src/test/java/com/nuvio/tv/ui/leanback/LeanbackCoreOptimizationTest.kt:932`
- **Agent**: mon
- **Root Cause**: Thêm test case mới trong `LeanbackCoreOptimizationTest.kt` nhưng chưa import hoặc dùng qualified name cho `com.nuvio.tv.ui.leanback.core.LeanbackThemeStyle` và `org.junit.Assert.assertFalse`.
- **Error Message**: 
  ```
  e: file:///F:/MyGithub/NuvioTV-Fast/app/src/test/java/com/nuvio/tv/ui/leanback/LeanbackCoreOptimizationTest.kt:932:21 Unresolved reference 'LeanbackThemeStyle'.
  e: file:///F:/MyGithub/NuvioTV-Fast/app/src/test/java/com/nuvio/tv/ui/leanback/LeanbackCoreOptimizationTest.kt:939:9 Unresolved reference 'assertFalse'.
  ```
- **Fix Applied**: Sử dụng tên đầy đủ `com.nuvio.tv.ui.leanback.core.LeanbackThemeStyle` và `org.junit.Assert.assertFalse`.
- **Prevention**: Kiểm tra header import của file test hoặc dùng fully-qualified reference khi thêm test ở cuối file.
- **Status**: Fixed

---

## [2026-11-23 13:17] - Dấu ngoặc nhọn thừa đóng sớm class NuvioMpvSurfaceView khi resolve conflict syncStyledSubtitles

- **Type**: Syntax
- **Severity**: Medium
- **File**: `app/src/main/java/com/nuvio/tv/ui/screens/player/NuvioMpvSurfaceView.kt:307`
- **Agent**: mon
- **Root Cause**: Khi dung hợp code giữa tính năng phụ đề kiểu mới (upstream) và tùy chỉnh outline width/visibility (custom), một dấu `}` thừa vô tình được giữ lại trong block `runCatching`, khiến định nghĩa class `NuvioMpvSurfaceView` bị đóng sớm và đẩy các method bên dưới ra ngoài top-level scope, gây ra 470 lỗi cascade.
- **Error Message**: 
  ```
  e: file:///F:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/NuvioMpvSurfaceView.kt:309:10 Syntax error: Expecting member declaration.
  e: file:///F:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/NuvioMpvSurfaceView.kt:31:1 Class 'NuvioMpvSurfaceView' is not abstract and does not implement abstract base class members:
  ```
- **Fix Applied**: Xóa dấu `}` thừa tại dòng 307 và chạy script kiểm tra cân bằng đóng mở ngoặc nhọn (`Opens: 229, Closes: 229`).
- **Prevention**: Luôn chạy script kiểm tra cân bằng ngoặc nhọn sau khi giải quyết conflict trên các file có block lồng nhau phức tạp.
- **Status**: Fixed

---

## [2026-11-23 13:20] - Thiếu mock usenetSourceSettings trong PlaybackSettingsTorrServerMutualExclusionTest

- **Type**: Integration
- **Severity**: Low
- **File**: `app/src/test/java/com/nuvio/tv/ui/screens/settings/PlaybackSettingsTorrServerMutualExclusionTest.kt:52`
- **Agent**: mon
- **Root Cause**: Constructor của `PlaybackSettingsViewModel` nhận thêm tham số `usenetSourceSettings` từ upstream commit `#4760`, nhưng test cũ của TorrServer chưa cập nhật mock cho tham số mới này.
- **Error Message**: 
  ```
  e: file:///F:/MyGithub/NuvioTV-Fast/app/src/test/java/com/nuvio/tv/ui/screens/settings/PlaybackSettingsTorrServerMutualExclusionTest.kt:52:13 No value passed for parameter 'usenetSourceSettings'.
  ```
- **Fix Applied**: Bổ sung `val usenetSourceSettings = mockk<UsenetSourceSettings>(relaxed = true)` vào hàm khởi tạo test.
- **Prevention**: Khi thêm tham số vào ViewModel constructor trong quá trình merge, grep toàn bộ test files để bổ sung mock tương ứng.
- **Status**: Fixed

---
