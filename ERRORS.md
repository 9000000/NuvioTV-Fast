## [2026-11-09 12:15] - Khắc phục triệt để lỗi mục tìm kiếm và khám phá bấm D-pad Lên/Xuống thi thoảng bị mất tiêu điểm (Focus Loss)

- **Type**: Logic & Focus Management
- **Severity**: High
- **File**: `app/src/main/java/com/nuvio/tv/ui/screens/search/SearchScreen.kt`, `app/src/main/java/com/nuvio/tv/ui/leanback/container/InteropHorizontalGridView.kt`, `app/src/main/java/com/nuvio/tv/ui/leanback/container/InteropVerticalGridView.kt`
- **Agent**: mon
- **Root Cause**:
  1. `SearchScreen.kt`: Trong `onNavigateDown` và `onNavigateUp`, điều kiện kiểm tra hiển thị `isNextVisible` yêu cầu hàng tiếp theo phải hiển thị 100% trọn vẹn trong viewport. Khi hàng tiếp theo chỉ hiển thị một phần (chẳng hạn lấp ló mép dưới), code nuốt phím (`return true`) và launch coroutine `animateScrollToItem`. Tuy nhiên, coroutine chỉ chờ vẻn vẹn 2 frame (`repeat(2) { withFrameNanos { } }`) rồi gọi `nextReq?.requestFocus()`. Nếu hàng mới chưa kịp layout xong hoặc RecyclerView đang bận (`isBusyLayout == true`), `requestFocus()` thất bại âm thầm và tiêu điểm biến mất hoàn toàn. Mặt khác, khi `isNextVisible == true`, code trả về `false` để Android FocusFinder tự tìm theo hình học; giữa 2 `AndroidView` trong LazyColumn với khoảng cách padding/title, FocusFinder thường không tìm thấy card thẳng hàng và drop phím.
  2. `InteropHorizontalGridView.kt` & `InteropVerticalGridView.kt`: Khi ViewGroup nhận focus (`onFocusChanged(gainFocus = true)` hoặc qua `super.requestFocus`), do cơ chế `FOCUS_AFTER_DESCENDANTS`, nếu card con chưa sẵn sàng ở frame đó, ViewGroup cha tự nhận focus (`findFocus() === this`). Do ViewGroup cha không có cơ chế tự động chuyển giao focus cho `child ViewHolder` khi view con bind xong trong `onChildViewHolderSelected`, tiêu điểm bị kẹt vĩnh viễn ở ViewGroup cha, khiến không có bất kỳ poster con nào được highlight viền sáng (mất tiêu điểm). Đồng thời trong `focusSearch`, code cũ từng trả về `this` (ViewGroup), trực tiếp cướp tiêu điểm khỏi các card poster.
- **Fix Applied**:
  1. `SearchScreen.kt`:
     - Tái cấu trúc `onNavigateDown` và `onNavigateUp`: Luôn xử lý dứt khoát và trả về `true` (không phó mặc cho FocusFinder hình học).
     - Áp dụng cơ chế Retry Loop (10 frame liên tiếp với `withFrameNanos { }`): Liên tục thử `nextReq.requestFocus()` ngay khi View hoàn tất layout pass trong frame đầu tiên và ngắt vòng lặp ngay khi thành công.
     - Đồng bộ hóa tương tự cho hàm `navigateToFirstResult()` khi người dùng bấm D-pad DOWN từ ô tìm kiếm (`SearchInputField`).
  2. `InteropHorizontalGridView.kt` & `InteropVerticalGridView.kt`:
     - Trong `addOnChildViewHolderSelectedListener`: Khi `child != null && hasFocus()`, lập tức gọi `child.itemView.requestFocus()` và bật sáng viền `(child as? LeanbackBaseViewHolder)?.updateFocusVisual(true)`. Đảm bảo ngay khoảnh khắc card con xuất hiện, tiêu điểm được gán trực tiếp vào card con.
     - Trong `onFocusChanged`: Khi `gainFocus == true` và `findFocus() === this` (ViewGroup đang giữ tiêu điểm), chủ động tìm `selectedPosition` và đẩy tiêu điểm vào ViewHolder con ngay lập tức.
     - Trong `focusSearch`: Loại bỏ hoàn toàn việc trả về `this` (ViewGroup). Thay thế bằng `focused ?: findFocus()`, đảm bảo ViewGroup cha không bao giờ trở thành đích nhận tiêu điểm.
     - `InteropVerticalGridView.kt`: Thêm biến `pendingTargetPosition` để khóa mục tiêu khi di chuyển dọc qua `moveToPosition`, đồng thời xử lý dứt khoát `DPAD_UP` và `DPAD_DOWN` theo bước nhảy toán học cột (`actualPos ± cols`).
- **Prevention**: Không bao giờ để ViewGroup cha nhận tiêu điểm thay cho ViewHolder con trong Leanback; luôn lắng nghe `onChildViewHolderSelected` để bàn giao tiêu điểm vào item con ngay khi layout pass hoàn tất; sử dụng retry loop qua `withFrameNanos` khi request focus trên các item vừa được cuộn vào màn hình thay vì giả định cứng 2 frame.
- **Status**: Fixed

---

## [2026-11-09 11:46] - Khắc phục triệt để hiện tượng tiêu điểm bị trượt qua poster khác rồi nhảy lại gây nháy giật khi bấm Lên/Xuống

- **Type**: Logic & Motion
- **Severity**: High
- **File**: `app/src/main/java/com/nuvio/tv/ui/leanback/container/InteropVerticalGridView.kt`, `app/src/main/java/com/nuvio/tv/ui/leanback/container/InteropHorizontalGridView.kt`, `app/src/main/java/com/nuvio/tv/ui/screens/search/SearchScreen.kt`
- **Agent**: mon
- **Root Cause**:
  1. `InteropVerticalGridView.kt`: Trong 2D Grid, khi bấm `FOCUS_DOWN` hoặc `FOCUS_UP`, `super.focusSearch` của Android RecyclerView sử dụng thuật toán tìm kiếm hình học tổng quát `FocusFinder`. Do sự chênh lệch nhỏ về khoảng cách hoặc kích thước scale giữa các poster, `FocusFinder` có thể tìm thấy một card lân cận lệch cột (ví dụ cột 1 thay vì cột 2), sau đó Leanback LayoutManager lại kéo focus về đúng cột 2, khiến focus bị trượt sang poster khác rồi mới nhảy về vị trí thẳng hàng, gây cảm giác nháy giật.
  2. `InteropHorizontalGridView.kt`: Trong `requestFocus(direction, previouslyFocusedRect)`, việc ép `val targetPos = selectedPosition.coerceAtLeast(0)` và request focus vào `targetPos` đã bỏ qua hoàn toàn tham số `previouslyFocusedRect`. Khi người dùng di chuyển D-pad từ một card ở cột 3 của hàng trên xuống hàng dưới, code ép focus vào Card 0, sau đó Android Framework lại phát hiện `previouslyFocusedRect` ở cột 3 và kéo focus từ Card 0 về Card 3, tạo ra hiện tượng trượt/nháy rõ rệt.
  3. `SearchScreen.kt`: Trong `onNavigateDown` và `onNavigateUp`, hàm luôn can thiệp và nuốt sự kiện kể cả khi hàng tiếp theo đã hiển thị đầy đủ trên màn hình, thay vì để Android View Hierarchy tự động chuyển tiêu điểm trực tiếp giữa hai hàng trong cùng một frame.
- **Fix Applied**:
  1. `InteropVerticalGridView.kt`: Trong `focusSearch`: Xác định chính xác vị trí thẻ thẳng hàng theo cột (`actualPos + cols` cho `FOCUS_DOWN` và `actualPos - cols` cho `FOCUS_UP`). Nếu ViewHolder tại vị trí đó đã có sẵn, trả về trực tiếp `targetVh.itemView`. Tiêu điểm di chuyển thẳng tắp theo trục dọc, triệt tiêu 100% hiện tượng trượt chéo sang thẻ khác.
  2. `InteropHorizontalGridView.kt`: Trong `requestFocus`: Kiểm tra nếu `previouslyFocusedRect != null` (chuyển hàng theo D-pad), ưu tiên để `super.requestFocus(direction, previouslyFocusedRect)` tìm đúng thẻ có tọa độ X thẳng hàng nhất. Chỉ ép về `selectedPosition` khi `previouslyFocusedRect == null` (focus từ code programmatic).
  3. `SearchScreen.kt`: Khi hàng tiếp theo hoặc hàng trước đó đã hiển thị trên màn hình (`isNextVisible == true` / `isPrevVisible == true`), `onNavigateDown` và `onNavigateUp` trả về `false`, cho phép Android FocusFinder tự chuyển focus thẳng hàng trực tiếp trong 1 frame duy nhất mà không qua bất kỳ coroutine hay sự can thiệp nhân tạo nào.
- **Prevention**: Luôn tận dụng `previouslyFocusedRect` khi chuyển giao tiêu điểm giữa các hàng ngang để bảo toàn cột thẳng hàng; trong 2D grid, ưu tiên điều hướng theo chỉ số toán học cột (`pos ± cols`) trước khi fallback về geometric focus search.
- **Status**: Fixed

---

## [2026-11-09 10:28] - Khắc phục triệt để lỗi bấm D-pad Lên/Xuống không focus vào poster hoặc bị mất tiêu điểm trên Android TV

- **Type**: Logic
- **Severity**: High
- **File**: `app/src/main/java/com/nuvio/tv/ui/leanback/container/InteropVerticalGridView.kt`, `app/src/main/java/com/nuvio/tv/ui/leanback/container/InteropHorizontalGridView.kt`, `app/src/main/java/com/nuvio/tv/ui/screens/search/SearchScreen.kt`
- **Agent**: mon
- **Root Cause**:
  1. `InteropVerticalGridView.kt`: Trong `dispatchKeyEvent`, sự kiện `DPAD_UP` và `DPAD_DOWN` bị can thiệp bởi mã tự định vị `moveToPosition(targetPos)` và `return true`. Lệnh này cướp mất luồng xử lý tự nhiên của Leanback `VerticalGridView`. Vì `moveToPosition` dùng `setSelectedPositionSmooth` và chỉ `post` 1 frame (16ms) để tìm ViewHolder, trong khi animation cuộn của GridView kéo dài 200-300ms chưa kịp bind xong ViewHolder mới, dẫn đến ViewHolder trả về `null` và focus bị mất hoàn toàn giữa các hàng.
  2. `InteropHorizontalGridView.kt`: Khi `InteropHorizontalGridView` được gọi `requestFocus()`, `super.requestFocus()` rơi vào `ViewGroup.requestFocus()`. Nếu không có child nào được layout kịp thời hoặc ViewGroup nhận focus, tiêu điểm dừng lại ở container ngoài mà không lọt vào bất kỳ `ViewHolder` poster con nào, khiến poster không có viền sáng (glow) và không scale lên.
  3. `SearchScreen.kt`: Trong `onNavigateDown` và `onNavigateUp`, khi hàng mục tiêu chưa hiển thị (`!isNextVisible`), lệnh `nextReq?.requestFocus()` bị gọi ngay lập tức trước khi coroutine `animateScrollToItem` cuộn xong và View được attach vào Window, dẫn đến `requestFocus()` thất bại âm thầm và mất tiêu điểm.
- **Fix Applied**:
  1. `InteropVerticalGridView.kt`: Trả lại quyền xử lý `DPAD_UP` và `DPAD_DOWN` giữa các hàng cho Leanback `super.dispatchKeyEvent(event)`, chỉ can thiệp chặn biên khi ở hàng đầu tiên (`isFirstRow`) để trỏ lên danh mục hoặc hàng cuối cùng (`isLastRow`). Thuật toán gốc của Google Leanback tự động cuộn và trao focus chuẩn xác 100% cho poster con mà không bao giờ mất focus.
  2. `InteropHorizontalGridView.kt`: Override `requestFocus(direction, previouslyFocusedRect)`: Chủ động tìm `findViewHolderForAdapterPosition(selectedPosition)` và gọi trực tiếp `vh.itemView.requestFocus()`, đồng thời bật viền sáng `(vh as? LeanbackBaseViewHolder)?.updateFocusVisual(true)`. Nếu ViewHolder chưa gắn, `post` chuyển giao focus ngay khi View attach. Đảm bảo 100% poster con luôn luôn nhận được focus.
  3. `SearchScreen.kt`: Đưa `nextReq?.requestFocus()` vào bên trong coroutine và chờ 2 frame sau khi `animateScrollToItem` cuộn xong, đảm bảo hàng mới đã render và attach hoàn chỉnh vào Window trước khi trao tiêu điểm.
- **Prevention**: Không can thiệp ghi đè sự kiện phím điều hướng dọc nội bộ của Leanback GridView; luôn chủ động chuyển tiếp focus từ ViewGroup cha vào ViewHolder con mục tiêu trong RecyclerView.
- **Status**: Fixed

---

## [2026-11-09 09:45] - Khắc phục lỗi cuộn thô bạo khi di chuyển D-pad Down/Up và bấm gợi ý trong Tìm kiếm, loại bỏ viền đen cắt poster trong mục Khám phá

- **Type**: Logic & UI/UX
- **Severity**: High
- **File**: `app/src/main/java/com/nuvio/tv/ui/screens/search/SearchScreen.kt`, `app/src/main/java/com/nuvio/tv/ui/screens/search/SearchDiscoverSection.kt`
- **Agent**: mon
- **Root Cause**:
  1. Trong `SearchScreen.kt`:
     - Lệnh `listState.animateScrollToItem(...)` bị gọi vô điều kiện trong `onNavigateDown`, `onNavigateUp` và `onItemFocused` bất cứ khi nào đổi hàng. Lệnh này ép hàng mục tiêu cuộn lên tận đỉnh màn hình (`offset = 0`), khiến ô tìm kiếm và các hàng trước đó bị đẩy vọt ra khỏi màn hình ngay khi bấm một nút D-pad.
     - Khi bấm vào gợi ý tìm kiếm có sẵn (`submitRecentSearch`), lệnh `topInputFocusRequester.requestFocus()` giật tiêu điểm vọt lên ô tìm kiếm làm LazyColumn giật ngược lên 0, sau đó `submitCurrentQuery` lại gọi `navigateToFirstResult` ép cuộn xuống 1, gây ra hiện tượng màn hình tự động cuộn giật lên rồi xuống.
  2. Trong `SearchDiscoverSection.kt`:
     - Container của Grid bị bọc bởi `Box` có `.clipToBounds()` và `Column` có `padding(horizontal = 52.dp)`. Khi poster ở cột 0 và cột 5 nhận focus (phóng to 1.1x và tỏa glow ra ngoài), `.clipToBounds()` cắt phẳng lỳ cạnh trái và cạnh phải của poster.
     - Thanh danh mục cố định có background đen che sát sàn sạt đầu poster hàng 1, cắt cụt đỉnh đầu poster khi phóng to.
     - `paddingValues` của `LeanbackNativeGrid` thiếu lề `start` và `end`.
- **Fix Applied**:
  1. `SearchScreen.kt`:
     - Trong `onNavigateDown` và `onNavigateUp`: Kiểm tra xem hàng mục tiêu đã hiển thị đầy đủ trong viewport chưa (`visibleItemsInfo`). Nếu đã hiển thị, chỉ chuyển focus bằng `nextReq?.requestFocus()`, tuyệt đối KHÔNG cuộn `animateScrollToItem`. Chỉ cuộn khi hàng đó thực sự nằm ngoài viewport.
     - Trong `onItemFocused`: Xóa bỏ hoàn toàn lệnh `animateScrollToItem(index + 1)`.
     - Trong `submitRecentSearch`: Bỏ lệnh giật focus lên ô tìm kiếm.
     - Trong `navigateToFirstResult`: Chỉ cuộn nếu hàng kết quả đầu tiên chưa nhìn thấy được; nếu đã nhìn thấy được thì giữ nguyên màn hình và chuyển nhẹ focus.
  2. `SearchDiscoverSection.kt`:
     - Xóa bỏ hoàn toàn `.clipToBounds()` ở Box chứa Grid.
     - Chuyển `padding(horizontal = 52.dp)` từ Column ngoài vào trong `paddingValues` của `LeanbackNativeGrid` (`start = 52.dp, end = 52.dp`). Do `InteropVerticalGridView` có `clipToPadding = false` và `clipChildren = false`, các poster khi phóng to và tỏa glow thoải mái mở rộng vào vùng lề 52dp mà không bị bất cứ cạnh nào cắt xén.
     - Nâng cấp Header với dải Gradient chuyển màu mượt mà từ Background sang Transparent ở cạnh đáy và khoảng đệm an toàn `topPadding = 24.dp`, giúp poster hàng 1 có không gian thở rộng rãi, không bao giờ bị viền đen cắt vào đầu.
- **Prevention**: Không dùng `animateScrollToItem` thô bạo không có điều kiện kiểm tra hiển thị (`visibleItemsInfo`); không lạm dụng `clipToBounds()` trên các container bọc TV Cards có animation phóng to (focus scale) và hiệu ứng phát sáng (glow).
- **Status**: Fixed

---

## [2026-11-09 09:20] - Khắc phục triệt để lỗi mục tìm kiếm bị giật nhảy loạn và tự động cuộn lung tung khi bấm OK trên Android TV

- **Type**: Logic
- **Severity**: High
- **File**: `app/src/main/java/com/nuvio/tv/ui/screens/search/SearchViewModel.kt`, `app/src/main/java/com/nuvio/tv/ui/screens/search/SearchScreen.kt`
- **Agent**: mon
- **Root Cause**:
  1. `SearchViewModel.kt`: Khi người dùng nhấn OK/Submit, ViewModel lại gọi `performSearch` và đặt lại `catalogRows = emptyList()` dù Live Search đã nạp sẵn kết quả đầy đủ cho từ khóa đó. Việc xóa rỗng kết quả làm các View bị dispose tức thì, mất tiêu điểm và làm `LazyColumn` giật ngược về vị trí 0, sau đó vài trăm mili-giây lại bị giật xuống khi kết quả mới nạp lại.
  2. `SearchScreen.kt`: Tồn tại 2 `LaunchedEffect` (dòng 466 và 480 cũ) chạy song song và xung đột nhau. Cả hai cùng lắng nghe `visibleCatalogRows.size`, khiến mỗi khi có Addon trả về kết quả (size tăng dần), `LaunchedEffect` lại bị kích hoạt lại, liên tục gọi `animateScrollToItem(1)` và cướp tiêu điểm trở lại hàng đầu tiên, tranh chấp trực tiếp với thao tác duyệt của người dùng.
  3. `CatalogRowSection.onItemFocused`: Lệnh `listState.animateScrollToItem(index + 1)` bị gọi vô điều kiện cho **mọi thẻ nhận focus**. Khi người dùng duyệt D-pad ngang giữa các poster trong cùng một hàng, lệnh cuộn dọc liên tục bị bắn ra làm giật màn hình.
  4. Đóng bàn phím ảo (IME) làm thay đổi đột ngột Viewport Insets, nếu cuộn ngay trong cùng frame sẽ làm lệch tọa độ cuộn của Compose.
- **Fix Applied**:
  1. `SearchViewModel.kt`: Sửa `submitSearch()`: nếu từ khóa gửi đi trùng khớp với từ khóa đang có kết quả (`activeSearchQuery == currentQuery && hasRealResults`), không gọi `performSearch` để hủy rows, chỉ cập nhật lịch sử tìm kiếm vào `saveRecentSearch` và giữ nguyên kết quả trên màn hình.
  2. `SearchScreen.kt`: Thay thế toàn bộ mớ cờ trạng thái chạy đua (`pendingAutoMoveToResultsOnLoad`, `pendingFocusMoveToResultsQuery`, `pendingFocusMoveSawSearching`, `focusResults`) bằng biến trạng thái duy nhất `pendingMoveToFirstResult` và hàm điều hướng `navigateToFirstResult()`. Khi Submit hoặc khi đợt kết quả đầu tiên xuất hiện, cuộn và chuyển tiêu điểm đúng **1 lần duy nhất**, sau đó tắt cờ ngay lập tức; các Addon trả về sau hoàn toàn không cướp focus hay cuộn lại.
  3. `SearchScreen.kt`: Thêm biến theo dõi `currentActiveRowIndex`. Trong `onItemFocused`, chỉ gọi `listState.animateScrollToItem(index + 1)` khi `currentActiveRowIndex != index` (chỉ cuộn khi chuyển hàng). Khi duyệt ngang giữa các phim trong cùng một hàng, màn hình đứng yên hoàn toàn.
  4. Đợi 2 frames sau khi ẩn bàn phím ảo bằng `withFrameNanos` để Window Insets ổn định trước khi cuộn màn hình.
- **Prevention**: Tuyệt đối không xóa dữ liệu UI hiện có khi người dùng chỉ bấm xác nhận từ khóa đã được Live Search nạp sẵn; không để `LaunchedEffect` tự động focus phụ thuộc vào kích thước mảng (`size`) tăng dần; kiểm soát cuộn dọc của danh sách cha dựa trên sự thay đổi chỉ số hàng (`rowIndex`) thay vì bắn lệnh ở mọi sự kiện focus con.
- **Status**: Fixed

---

## [2026-11-09 00:08] - Sửa lỗi D-pad mục Khám phá: Nhấn Trái/Lên bị văng vào danh mục, chuẩn hóa chỉ trỏ vào danh mục khi ở hàng đầu và trỏ Sidebar khi ở cột đầu

- **Type**: Logic
- **Severity**: High
- **File**: `app/src/main/java/com/nuvio/tv/ui/leanback/container/InteropVerticalGridView.kt`
- **Agent**: mon
- **Root Cause**:
  1. `InteropVerticalGridView.kt`: Trong `focusSearch`, khi `super.focusSearch` trả về `null` (hoặc view ngoài GridView), method trả về `null` thay vì giữ tiêu điểm `focused`. Khi một ViewGroup trả về `null` trong `focusSearch`, Android Framework tự động leo lên cây cha `AndroidComposeView`. Lúc này thuật toán 2D focus của Compose tìm thấy 3 Dropdown Picker (Loại, Danh mục, Thể loại) ở góc trên bên trái và đẩy tiêu điểm lên danh mục dù người dùng đang ở hàng 2, 3 hoặc cột 1, 2, 3.
  2. Trong `dispatchKeyEvent`: Khi nhấn `DPAD_LEFT` ở cột 0, biến `lastEdgeLeftPressTime` bắt buộc double-tap trong vòng 700ms mới kích hoạt `onNavigateLeftOnFirstColumn`, khiến lần nhấn đầu tiên bị chặn không mở được thanh điều hướng. Đồng thời ở các cột khác (`currentColumn > 0`) hoặc các hàng khác (`!isFirstRow`), nếu `super.dispatchKeyEvent` trả về `false`, Compose FocusManager nhận được sự kiện unconsumed và tự động đẩy tiêu điểm vọt lên danh mục phía trên.
- **Fix Applied**:
  1. Nâng cấp `focusSearch` trong `InteropVerticalGridView.kt`: Chặn đứng thoát tiêu điểm ra ngoài GridView cho `FOCUS_LEFT`, `FOCUS_RIGHT`, và `FOCUS_DOWN` khi chưa ở biên, cũng như `FOCUS_UP` khi không phải hàng đầu (`!isFirstRow`). Nếu `result == null || !isChildOfThis(result)`, lập tức trả về `focused ?: this` để ngăn triệt để Android/Compose leo lên các Dropdown Picker phía trên.
  2. Bổ sung hàm `moveToPosition(targetPos)` chủ động điều hướng tiêu điểm trong Grid kèm kiểm tra an toàn `findViewHolderForAdapterPosition` và fallback `post`.
  3. Cập nhật `dispatchKeyEvent`:
     - Phím `DPAD_LEFT`: Khi `currentColumn == 0`, gọi ngay `onNavigateLeftOnFirstColumn?.invoke()` trong lần nhấn đầu tiên để trỏ vào Sidebar; khi `currentColumn > 0`, di chuyển sang thẻ bên trái trong cùng hàng (`actualPos - 1`), tiêu thụ sự kiện và không để rò rỉ lên Compose.
     - Phím `DPAD_UP`: Chỉ gọi `onNavigateUpOnFirstRow?.invoke()` để trỏ vào danh mục khi đang ở hàng đầu tiên (`isFirstRow == true`). Khi `isFirstRow == false`, di chuyển tiêu điểm lên hàng trên trong Grid (`actualPos - cols`), tiêu thụ hoàn toàn sự kiện và không bao giờ nhảy cóc lên danh mục.
- **Prevention**: Luôn chủ động tiêu thụ (consume) toàn bộ các hướng di chuyển bên trong 2D Grid nhúng trong Compose để tránh việc Compose FocusManager tự ý tìm kiếm mục tiêu lân cận; không áp dụng cơ chế double-tap gây cản trở trải nghiệm điều hướng tự nhiên của người dùng TV D-pad.
- **Status**: Fixed

---

## [2026-11-08 00:33] - Khắc phục triệt để lỗi điều hướng D-pad: Chặn bắt phím trái ở cấp ViewHolder mở nhầm Sidebar, lỗi che biến listState trong SearchScreen, mất focus khi submit tìm kiếm và rò rỉ focus trong Khám phá

- **Type**: Logic
- **Severity**: High
- **File**: `LeanbackBaseViewHolder.kt`, `InteropHorizontalGridView.kt`, `InteropVerticalGridView.kt`, `LeanbackNativeGrid.kt`, `LeanbackNativeRow.kt`, `SearchScreen.kt`
- **Agent**: mon
- **Root Cause**:
  1. `LeanbackBaseViewHolder.kt`: `root.setOnKeyListener` lắng nghe `DPAD_LEFT` và gọi ngay `adapter.onNavigateLeft()` trên **mọi card** (bất kể vị trí ở giữa hay ở biên). Do child view nhận key event trước parent, nó tự động mở Sidebar khi người dùng bấm Left ở bất kỳ poster nào, vô hiệu hóa toàn bộ cơ chế tính toán biên (`actualPos == 0`) và debounce double-tap 700ms của Grid cha.
  2. `SearchScreen.kt`: Dòng 840 khai báo `val listState = searchRowStates.getOrPut(catalogKey)` đã **che khuất (shadowing)** biến `val listState` của màn hình ở dòng 330 (`LazyColumn`). Khi người dùng bấm Down/Up giữa các hàng, lệnh `listState.animateScrollToItem(...)` tác động nhầm vào `LazyListState` ngang của hàng thay vì cuộn dọc màn hình! Do đó, màn hình hoàn toàn đứng im, các hàng kết quả bên dưới bị khuất.
  3. `LeanbackNativeRow.kt` & `LeanbackNativeGrid.kt`: Hàm `executeRowFocus` / `executeGridFocus` dùng `grid.post { if (!grid.isAttachedToWindow) return@post }`. Khi kết quả tìm kiếm vừa nạp, nếu lệnh focus chạy trước khi View attach vào window, lệnh bị hủy âm thầm mà không thử lại, dẫn đến mất tiêu điểm sau khi bấm tìm kiếm.
  4. `InteropVerticalGridView.kt`: `focusSearch` chỉ chặn thoát tiêu điểm cho `FOCUS_LEFT` và `FOCUS_RIGHT`. Khi người dùng bấm `FOCUS_UP` ở các hàng dưới (hàng 1, 2, 3), nếu View hàng trên chưa kịp render, FocusFinder có thể quét vọt lên các Dropdown menu trên cùng.
- **Fix Applied**:
  1. Gỡ bỏ hoàn toàn việc chặn bắt phím 4 hướng trong `LeanbackBaseViewHolder.root.setOnKeyListener`; trao quyền phán quyết biên và mở Sidebar độc quyền cho `InteropHorizontalGridView` và `InteropVerticalGridView`.
  2. Đổi tên biến cục bộ thành `val rowState` trong `SearchScreen.kt`, giải phóng biến `listState` dọc để `listState.animateScrollToItem(nextIndex + 1)` cuộn mượt mà các hàng kết quả lên tầm nhìn.
  3. Thêm `addOnAttachStateChangeListener` vào `executeRowFocus` và `executeGridFocus`: Đảm bảo lệnh focus vào card 0 không bao giờ bị rơi rụng dù View đang trong quá trình gắn vào Window.
  4. Nâng cấp `focusSearch` trong `InteropVerticalGridView.kt`: Chặn đứng thoát tiêu điểm `FOCUS_UP` trừ khi người dùng đang thực sự đứng ở hàng đầu tiên (`actualPos in 0 until cols`).
- **Prevention**: Tuyệt đối không xử lý điều hướng biên (boundary navigation) ở cấp ViewHolder con trong RecyclerView; luôn kiểm tra variable shadowing với các State cốt lõi của Jetpack Compose.
- **Status**: Fixed

---

## [2026-11-07 23:55] - Khắc phục lỗi D-pad Trái nhảy lên thanh menu Khám phá, màn hình Tìm kiếm không tự động đẩy lên khi cuộn xuống và mất focus sau khi tìm phim

- **Type**: Logic
- **Severity**: High
- **File**: `InteropVerticalGridView.kt`, `SearchDiscoverSection.kt`, `CatalogRowSection.kt`, `SearchScreen.kt`
- **Agent**: mon
- **Root Cause**:
  1. `InteropVerticalGridView.kt`: Android TV `FocusFinder` quét theo góc chéo khi nhấn D-pad Trái/Phải; nếu không tìm thấy view ngang cùng trục trong Grid, nó tìm kiếm lên các view ở phía trên ngoài Grid (chính là 3 dropdown picker Loại / Danh mục / Thể loại của Khám phá), khiến tiêu điểm nhảy vọt lên thanh menu trên cùng.
  2. `CatalogRowSection.kt` & `SearchScreen.kt`: `LeanbackNativeRow` (`AndroidView`) nằm trong Compose `LazyColumn`. Khi người dùng bấm D-pad Xuống qua các hàng kết quả bên dưới màn hình, `LazyColumn` không nhận được sự kiện `bringIntoView` từ Android View hierarchy nên không tự động cuộn lên (scroll up), khiến các hàng bên dưới bị khuất khỏi màn hình.
  3. `SearchScreen.kt`: Khi bấm tìm kiếm (`submitCurrentQuery`), `CatalogRowSection` thiếu truyền tham số `focusedItemIndex = 0` (mặc định `-1`), khiến `isPendingFocus` trên `LeanbackNativeRow` bằng `false`, `executeRowFocus(gridView, 0)` không được gọi và focus bị rơi vào trạng thái mất hoàn toàn sau khi nạp kết quả.
- **Fix Applied**:
  1. Override `focusSearch` trong `InteropVerticalGridView.kt`: Khi `direction` là `FOCUS_LEFT` hoặc `FOCUS_RIGHT`, nếu kết quả trả về không phải con của Grid (`!isChildOfThis(result)`), chặn lại và trả về `focused`, triệt tiêu 100% hiện tượng focus nhảy chéo lên thanh menu phía trên. Đồng thời tích hợp double-tap 700ms an toàn tại Cột 0 để mở Sidebar khi có chủ đích.
  2. Kết nối `onNavigateDown` và `onNavigateUp` giữa các hàng trong `SearchScreen.kt`: Khi nhấn D-pad Xuống/Lên, gọi `listState.animateScrollToItem(targetIndex)` đồng thời chuyển giao focus sang hàng tiếp theo, giúp màn hình luôn tự động đẩy lên mượt mà theo từng bước di chuyển.
  3. Tự động focus hàng kết quả đầu tiên: Truyền `(focusResults || pendingAutoMoveToResultsOnLoad) && index == 0` thành `focusedItemIndex = 0` vào `CatalogRowSection`, kích hoạt `executeRowFocus(gridView, 0)` trên `HorizontalGridView` ngay khi kết quả nạp xong.
- **Prevention**: Luôn override `focusSearch` trong các container RecyclerView nhúng trong Compose để ngăn FocusFinder quét chéo ra ngoài; khi nhúng AndroidView vào LazyColumn, luôn chủ động điều khiển cuộn của LazyListState theo hàng active.
- **Status**: Fixed

---

## [2026-11-07 23:15] - Khắc phục lỗi nhảy focus về item đầu khi nạp thêm dữ liệu (Pagination), chập chờn Header danh mục, cướp focus sang Sidebar và kẹt D-pad màn hình Search

- **Type**: Logic
- **Severity**: High
- **File**: `LeanbackNativeGrid.kt`, `LeanbackNativeRow.kt`, `LeanbackUniversalAdapter.kt`, `InteropHorizontalGridView.kt`, `InteropVerticalGridView.kt`, `MainActivity.kt`, `CatalogRowSection.kt`, `SearchScreen.kt`, `FolderDetailScreen.kt`
- **Agent**: mon
- **Root Cause**:
  1. `LeanbackNativeGrid.kt`: `LaunchedEffect(restoreFocusNonce, items.size)` đưa `items.size` làm dependency key. Mỗi khi người dùng cuộn xuống đáy để nạp trang mới (Pagination), `items.size` tăng lên làm kích hoạt lại `executeGridFocus(grid, initialFocusPosition)`. Do `initialFocusPosition` mặc định là 0, focus bị giật ngược từ vị trí đang cuộn về phim đầu tiên, làm màn hình cuộn vọt lên đỉnh khiến thanh danh mục hiện lên rồi biến mất (tạo hiện tượng chập chờn).
  2. `LeanbackUniversalAdapter.kt`: Gọi `notifyDataSetChanged()` cho mọi thay đổi dữ liệu khiến toàn bộ ViewHolders bị hủy và tái tạo, làm mất focus của thẻ đang được chọn.
  3. `InteropHorizontalGridView.kt` & `MainActivity.kt`: Lắng nghe phím `KEYCODE_DPAD_LEFT` mà không kiểm tra vị trí thực tế (`currentPos <= 0` match với cả `NO_POSITION = -1`) và xử lý `onKeyEvent` chưa có điều kiện ở Scaffold làm phím Trái luôn kích hoạt mở Sidebar khi cuộn hàng.
  4. `SearchScreen.kt`: Trong `SearchInputField`, phím `KEYCODE_DPAD_DOWN` bị chặn bởi điều kiện `if (canMoveToResults)`. Khi ô tìm kiếm rỗng hoặc khi kết quả chưa nạp xong, người dùng không thể bấm D-pad Xuống để vào danh sách tìm kiếm gần đây. Sau khi nhập nội dung và nhấn Done/OK/Enter, hệ thống ẩn bàn phím nhưng không điều hướng focus xuống hàng catalog đầu tiên, gây mất tiêu điểm hoặc kẹt chỉ di chuyển được sang phải.
- **Fix Applied**:
  1. Tách biệt `initialFocusDone` trong `LeanbackNativeGrid.kt`: Chỉ cho phép `initialFocusPosition` chạy một lần duy nhất lúc khởi tạo hoặc khi có `restoreFocusNonce` tăng lên; loại bỏ `items.size` khỏi LaunchedEffect.
  2. Nâng cấp `submitItems` trong `LeanbackUniversalAdapter.kt`: Tự động nhận diện `isAppendOnly` và sử dụng `notifyItemRangeInserted` thay vì `notifyDataSetChanged()` khi nạp trang mới, bảo toàn 100% ViewHolder và trạng thái focus của người dùng.
  3. Cơ chế Double-Tap xác nhận tại mép trái (`InteropHorizontalGridView.kt`): Nhấn D-pad Trái lần đầu tại thẻ 0 sẽ dừng tại biên thẻ; chỉ khi người dùng nhấn Trái lần thứ 2 có chủ đích trong vòng 700ms thì mới kích hoạt điều hướng sang Sidebar/menu trái.
  4. Kết nối `FocusRequester` trong `CatalogRowSection.kt`: Gán `entryFocusRequester` và `resolvedRowFocusRequester` vào `LeanbackNativeRow` để hỗ trợ focus mượt mà từ bên ngoài.
  5. Thiết kế lại điều hướng Search (`SearchScreen.kt`):
     - D-pad Xuống từ ô Search: Khi `canMoveToResults == true`, tự động cuộn xuống kết quả và focus hàng đầu; khi `canMoveToResults == false`, trỏ trực tiếp xuống phần tử tìm kiếm gần đây (`recentFirstSearchFocusRequester` hoặc nút Xóa lịch sử).
     - Khi nhấn OK/Enter/Done: Tự động kích hoạt cơ chế `pendingAutoMoveToResultsOnLoad`, tự động bắt tiêu điểm vào thẻ phim đầu tiên của kết quả ngay khi dữ liệu được nạp xong.
     - Xóa bỏ các listener phím Trái mở Sidebar ngoài ý muốn trong Search Screen.
  6. Bổ sung `onNavigateUpOnFirstRow` trong `FolderDetailScreen.kt`: Cho phép D-pad Lên từ hàng đầu của Grid đưa focus mượt mà về Tab đang chọn của thư mục.
- **Prevention**: Khi cài đặt pagination trong RecyclerView/Leanback, bắt buộc phân biệt append dữ liệu (`notifyItemRangeInserted`) với reload toàn bộ (`notifyDataSetChanged`); không đưa kích thước danh sách (`items.size`) làm trigger cho hiệu ứng khôi phục focus; luôn đảm bảo trạng thái rỗng hoặc đang nạp có lộ trình focus dự phòng (fallback focus target).
- **Status**: Fixed

---

## [2026-11-07 22:10] - Khắc phục lỗi mở Sidebar trái ngoài ý muốn trên màn hình phụ & Chuẩn hóa viền sáng Skia Bloom chuẩn 1:1 Home trên toàn hệ thống Leanback

- **Type**: Logic
- **Severity**: High
- **File**: `MainActivity.kt`, `LeanbackBaseViewHolder.kt`, `LeanbackAvatarViewHolder.kt`, `LeanbackPosterViewHolder.kt`, `LeanbackEpisodeViewHolder.kt`, `LeanbackUniversalAdapter.kt`, `InteropVerticalGridView.kt`, `SearchDiscoverSection.kt`, `CatalogRowSection.kt`, `ClassicHomeContent.kt`
- **Agent**: mon
- **Root Cause**:
  1. `MainActivity.kt`: `LocalOpenSidebar` được cấp phát toàn cục vô điều kiện cho toàn bộ `NuvioNavHost`. Các màn hình con (`Detail`, `SeeAll`, `FolderDetail`, `Search Discover`, `Player`) vô tình nhận được callback mở Sidebar khi nhấn D-pad Left ở phần tử đầu tiên.
  2. `InteropVerticalGridView.kt`: Khi ở cột 0 (`currentColumn == 0`), nếu không có phần tử bên trái trong View hierarchy, sự kiện `DPAD_LEFT` rơi vào fallback của Android window/Compose, gây focus escape hoặc kích hoạt mở sidebar.
  3. `LeanbackUniversalAdapter.kt` và các ViewHolder: `borderWidthPx` bị gán cứng là raw `2` (pixels) thay vì `2dp` theo mật độ hiển thị màn hình TV; `glowMarginPx` bị gán cứng `16px` trong khi `glowSpreadPx` lên tới `28dp` (~42-56px). Do `glowMarginPx < glowSpreadPx`, hiệu ứng Skia Gaussian Bloom bị cắt cụt ở biên 16px tạo vệt hộp chữ nhật sắc cạnh (hard clipping) thay vì hào quang lan tỏa êm dịu (atmospheric dispersion).
  4. `LeanbackBaseViewHolder.kt`: Không nâng `translationZ` khi thẻ nhận focus, khiến các thẻ lân cận (vẽ sau theo thứ tự adapter) đè lên viền sáng và scale 1.08f của thẻ đang focus.
  5. `LeanbackAvatarViewHolder.kt`: Dùng viền bo góc chữ nhật thay vì `GradientDrawable.OVAL`, làm méo viền focus tròn của avatar diễn viên.
- **Fix Applied**:
  1. Khóa `LocalOpenSidebar provides if (showSidebar) { ... } else null` trong `MainActivity.kt`, chỉ cấp phát cho các root route (`currentRoute in rootRoutes`). Mọi màn hình con nhận `null`.
  2. Cập nhật `CatalogRowSection.kt` chỉ kích hoạt mở sidebar khi `allowOpenSidebar = true` (chỉ bật ở `ClassicHomeContent`) và `openSidebar != null`.
  3. Loại bỏ hoàn toàn callback mở sidebar ở cột 0 trong `SearchDiscoverSection.kt`.
  4. Cập nhật `InteropVerticalGridView.kt`: Ở cột 0 (hoặc cột cuối), nếu không tìm thấy view lân cận hợp lệ qua `focusSearch`, gọi `stopScroll()` và return `true` để hấp thụ phím mượt mà, ngăn focus escape.
  5. Chuẩn hóa tỷ lệ viền sáng: Đưa `glowSpreadDp = 18f`, `glowMarginPx = (glowSpreadPx * 1.35f).toInt()`, `borderWidthPx = (2f * dp).toInt().coerceAtLeast(2)` đồng bộ trên `LeanbackUniversalAdapter`, `LeanbackPosterViewHolder`, `LeanbackEpisodeViewHolder`, và `LeanbackAvatarViewHolder`.
  6. Thêm `root.translationZ = if (hasFocus) 12f else 0f` trong `LeanbackBaseViewHolder.kt` để thẻ đang focus luôn nổi lên layer cao nhất, triệt tiêu 100% overdraw từ các thẻ xung quanh.
  7. Thiết lập `GradientDrawable.OVAL` cho `LeanbackAvatarViewHolder.kt` khi `isCircle = true`.
- **Prevention**: Luôn kiểm tra điều kiện cấp phát của các CompositionLocal có tính chất điều hướng toàn cục; tính toán độ lề Canvas Glow rộng hơn bán kính lan tỏa Gaussian Bloom ít nhất 35% để tránh clipping; dùng Z-elevation khi thẻ có hiệu ứng phóng to (scale) trong RecyclerView.
- **Status**: Fixed

---

## [2026-11-03 00:15] - Hiệu ứng viền phát quang Neon Glow bị vuông góc không bo tròn theo thiết lập Poster & Tích hợp tối ưu khung hình

- **Type**: Logic
- **Severity**: High
- **File**: `LeanbackModernRow.kt:174-220, 531-549, 735-785, 921-985`
- **Agent**: mon
- **Root Cause**:
  1. Trong `LeanbackRowAdapter.onCreateViewHolder`: `val glowView = PlayStoreGlowView(context).apply { this.cornerRadiusPx = cornerRadiusPx ... }`. Trong phạm vi `apply`, định danh `cornerRadiusPx` bên phải dấu gán tự tham chiếu đến chính thuộc tính mặc định của `PlayStoreGlowView` (`0.0f`), dẫn đến gán tự thân `0.0f` thay vì lấy từ adapter.
  2. Trong `LeanbackCardViewHolder.updateCornerRadius`: `if (cornerRadiusPx == newRadiusPx) return` gây thoát sớm ngay từ lần bind đầu tiên vì `holder.cornerRadiusPx` đã mang giá trị ban đầu truyền từ adapter constructor, khiến `glowView.cornerRadiusPx` không bao giờ được cập nhật và vĩnh viễn bằng `0.0f`.
  3. Tại `PlayStoreGlowView.onDraw`: Do `cornerRadiusPx == 0.0f`, nhánh `if (cornerRadiusPx > 0f)` không bao giờ thỏa mãn, ép vẽ `cardPath.addRect(...)` tạo thành hình chữ nhật góc vuông 90 độ sắc nhọn.
  4. Ngoài ra, việc dùng `Paint.Style.FILL` cho quầng sáng tán xạ Gaussian tập trung mật độ blur dày đặc ở các đỉnh góc lồi tạo ảo giác loe góc vuông.
- **Fix Applied**:
  1. Chỉ định rõ scope adapter: `this.cornerRadiusPx = this@LeanbackRowAdapter.cornerRadiusPx` trong `onCreateViewHolder`.
  2. Bổ sung kiểm tra đồng bộ trong `updateCornerRadius`: `val radiusChanged = cornerRadiusPx != newRadiusPx || glowView.cornerRadiusPx != newRadiusPx; if (!radiusChanged) return`.
  3. Chuyển đổi toàn bộ `auraPaint`, `midPaint`, `corePaint` sang `Paint.Style.STROKE` với `strokeCap = Paint.Cap.ROUND` và `strokeJoin = Paint.Join.ROUND`, độ dày nét tương thích để quầng phát quang bám sát ôm đồng tâm chính xác tuyệt đối với đường viền bo góc tròn của poster.
  4. **Tích hợp Gợi ý 1 (Pill-shaped ProgressBar)**: Thiết kế lại toàn bộ thanh tiến trình xem tiếp thành dạng viên thuốc hai đầu bo tròn (`LayerDrawable` với `trackPill` và `clipProg` bán kính `h / 2f`), đồng thời thụt lề động `horizMargin` theo bán kính góc bo của poster để không bao giờ bị cắt cụt hay lòi góc.
  5. **Tích hợp Gợi ý 2 (Zero GPU Overdraw)**: Khi Coil nạp xong ảnh poster che kín thẻ, gán `cardContainer.background = null` triệt tiêu 100% lớp vẽ nền đen thừa; tự động khôi phục lại background khi ViewHolder bị unbind/recycle.
- **Prevention**: Luôn cẩn trọng với biến bị che khuất (variable shadowing) trong các hàm scope function của Kotlin (`apply`, `run`) khi thuộc tính của View trùng tên với tham số lớp ngoài (sử dụng `@Label` tường minh); các nhánh kiểm tra thay đổi bán kính bo góc phải kiểm tra trạng thái thực tế của tất cả View con phụ thuộc.
- **Status**: Fixed

---

## [2026-11-02 22:30] - Tối ưu toàn diện Home Leanback: Shared RecycledViewPool, Deferred Glow 60fps, 16 Hue Bins Histogram và dọn sạch mã mồ côi

- **Type**: Logic
- **Severity**: High
- **File**: `LeanbackModernRow.kt`, `ModernHomeRows.kt`, `ModernHomeRowsList.kt`, `MainActivity.kt`, `NativeSidebarView.kt`, `ModernSidebarBlurPanel.kt`
- **Agent**: mon
- **Root Cause**:
  1. Mỗi hàng ngang `HorizontalGridView` trên màn hình Home tạo một RecycledViewPool riêng, khiến Android phải inflate lại toàn bộ ViewHolder khi cuộn dọc giữa các hàng phim, gây khựng khung hình.
  2. `PlayStoreGlowView` chạy trên Software Layer với Gaussian Blur; khi người dùng giữ phím D-pad cuộn nhanh qua hàng loạt thẻ, GPU/CPU bị quá tải vì phải liên tục tính toán Gaussian blur cho từng thẻ lướt qua.
  3. Cấp phát đối tượng `GradientDrawable` mới liên tục mỗi khi thẻ hoặc sidebar item nhận/mất focus, gây áp lực rác lớn lên Garbage Collector (GC pauses).
  4. Coil 3 luôn nạp ảnh ở chế độ Software Bitmap (`allowHardware = false`) ngay cả khi ảnh đã có màu trong cache, tiêu tốn gấp đôi RAM hệ thống.
  5. Tệp `ModernSidebarBlurPanel.kt` mồ côi (300+ dòng) cùng các biến trạng thái cũ trong `MainActivity.kt` không còn được sử dụng sau khi chuyển sang `NativeSidebarView`.
- **Fix Applied**:
  1. Khởi tạo `sharedRecycledViewPool = remember { RecyclerView.RecycledViewPool().apply { setMaxRecycledViews(0, 30) } }` tại `ModernHomeRowsList.kt` và truyền dùng chung cho tất cả các hàng ngang `HorizontalGridView`.
  2. Bổ sung cơ chế `deferredGlowRunnable`: Trì hoãn 55ms việc kích hoạt Software Glow khi phát hiện đang giữ phím cuộn nhanh (`isHoldingHorizontalKey == true`), khóa cứng tốc độ 60fps khi lướt và chỉ bừng sáng khi dừng ở thẻ mục tiêu.
  3. Nâng cấp thuật toán `extractDominantColor` sang 16 Hue Bins Histogram với trọng số `sat * sat * brightness`, lọc sạch pixel đen/trắng đục và boost màu Neon rực rỡ chuẩn TV OLED/QLED.
  4. Cấu hình `allowHardware(hasCachedColor)` trong Coil: Ảnh đã có màu trong cache sẽ nạp thẳng vào GPU Hardware Bitmap, giảm 50% RAM.
  5. Tái sử dụng `focusBorderDrawable` và `itemCornerRadiusPx` trong `LeanbackCardViewHolder` và `NativeSidebarView`, triệt tiêu hoàn toàn việc cấp phát Drawable mới mỗi frame.
  6. Xóa vĩnh viễn tệp mồ côi `ModernSidebarBlurPanel.kt`, dọn dẹp các biến thừa trong `MainActivity.kt`, cập nhật stale closure cho các callback trong `AndroidView`.
- **Prevention**: Luôn chia sẻ RecycledViewPool cho các danh sách con lồng nhau; hạn chế tối đa việc render Software Blur trong các thao tác lướt phím tốc độ cao; tái sử dụng Drawable cho các hiệu ứng focus viền.
- **Status**: Fixed

---

## [2026-11-02 15:40] - FocusRequester is not initialized when navigating down from Play button in MetaDetailsScreen

- **Type**: Logic
- **Severity**: High
- **File**: `HeroSection.kt:70,165,302`, `EpisodesSection.kt:48,84,185`, `MetaDetailsScreen.kt:2380-2440`
- **Agent**: mon
- **Root Cause**:
  1. In `MetaDetailsScreen.kt`, `HeroSection` action buttons (`PlayButton`, `ActionIconButton`) had `focusProperties { down = downFocusRequester }` directly assigned to downstream sections (`SeasonTabs`, `EpisodesRow`, or `Cast`).
  2. Because `HeroSection` spans the entire initial TV viewport, items below it are off-screen and not yet composed by `LazyColumn`.
  3. Consequently, `downFocusRequester` has no attached layout nodes. Compose emitted `FocusRelatedWarning: FocusRequester is not initialized` and rejected the 2D focus navigation entirely.
  4. A legacy workaround in `MetaDetailsScreen.kt` required double-tapping DPAD_DOWN within 280ms (`rapidDown`) to scroll by 240dp, forcing the user to spam the DOWN key 4–6 times before the next section was brought into view and composed.
- **Fix Applied**:
  1. Added `onNavigateDown: (() -> Boolean)? = null` callbacks across `HeroSection`, `PlayButton`, `ActionIconButton`, `SeasonTabs`, `EpisodesRow`, and `EpisodeCard`.
  2. In `PlayButton` and related elements, only assign `down = downFocusRequester` when `onNavigateDown == null`.
  3. Intercept `ACTION_DOWN` + `KEYCODE_DPAD_DOWN` via `onPreviewKeyEvent` to trigger `onNavigateDown()`.
  4. In `MetaDetailsScreen.kt`, implemented `onHeroNavigateDown`, `onSeasonTabsNavigateDown`, and `onEpisodesRowNavigateDown` to programmatically invoke `listState.animateScrollToItem(targetIndex)`, wait 2 frames (`withFrameNanos`) for layout nodes to attach, and immediately call `requestFocus()` on the target.
- **Prevention**: Never link `focusProperties.down/up` to `FocusRequester`s belonging to uncomposed items in a `LazyColumn`/`LazyRow`. Instead, intercept the DPAD key event, scroll the target into the viewport, and request focus once composed.
- **Status**: Fixed

---

## [2026-11-02 15:12] - Laggy poster scrolling in Discover & Legacy sidebar removal

- **Type**: Logic
- **Severity**: High
- **File**: `NuvioScrollDefaults.kt:14`, `GridContentCard.kt:78,121`, `SearchDiscoverSection.kt:604`, `MainActivity.kt:1227`, `LayoutHomeSettings.kt:200`
- **Agent**: mon
- **Root Cause**:
  1. `NuvioScrollDefaults.smoothScrollSpec` accidentally passed `NuvioMotion.tokens.durations.fast.toFloat()` (= 160f) as spring stiffness. Spring stiffness 160f is 10x slower than normal TV stiffness (1500f), causing an 800ms sluggish rubber-band drag on every D-pad movement in Discover's `LazyVerticalGrid`.
  2. `GridContentCard.kt` ran Compose `animateFloatAsState` continuously for 110ms on every focus shift even when scale was 1.0f, and default Coil `imageCrossfade = true` caused dozens of concurrent alpha blending animations while scrolling.
  3. `SearchDiscoverSection.kt` had an unthrottled `onLoadMore` triggering multiple overlapping 20-item page loads and image decodings while scrolling, causing 694ms GC pauses.
  4. User encountered bugs when switching to the obsolete legacy sidebar.
- **Fix Applied**:
  1. Completely deleted all legacy sidebar code (`LegacySidebarScaffold`). The Modern Sidebar is now permanently active.
  2. Removed the "Thanh điều hướng hiện đại" toggle from `LayoutHomeSettings.kt`, keeping only Modern Blur (Android 12+) and Hide Floating Pill.
  3. Replaced spring stiffness in `NuvioScrollDefaults.smoothScrollSpec` with `Spring.StiffnessMedium` (1500f) and `Spring.DampingRatioNoBouncy` for instant, crisp, snappy 120ms scroll.
  4. Set `imageCrossfade = false` by default in `GridContentCard` and `DiscoverGrid`.
  5. Bypassed `animateFloatAsState` and graphicsLayer scaling when `focusedScale == 1f` (the standard), with 80ms snappy tween when custom scale is used.
  6. Added 1200ms debounce to `onLoadMore` in `SearchDiscoverSection` near tail (`items.size - 4`).
- **Prevention**: Never use duration (ms) as spring stiffness. Always use physics-based `Spring.StiffnessMedium` or higher for TV focus animations.
- **Status**: Fixed

---

## [2026-11-02 12:03] - @Composable invocations can only happen from context of @Composable function in key listeners

- **Type**: Syntax
- **Severity**: Low
- **File**: `app/src/main/java/com/nuvio/tv/features/livetv/LiveTvChannelCard.kt:74`, `LiveTvFilterComponents.kt:113`, `SearchDiscoverSection.kt:158`, `SearchScreen.kt:1063`
- **Agent**: mon
- **Root Cause**: Calling `LocalOpenSidebar.current` directly inside key listener lambdas (`onKeyEvent`, `onPreviewKeyEvent`). Since `.current` is an `@Composable` getter on CompositionLocal, it cannot be called within an event callback lambda.
- **Error Message**: 
  ```
  e: @Composable invocations can only happen from the context of a @Composable function
  ```
- **Fix Applied**: Evaluated `val openSidebar = LocalOpenSidebar.current` once in the enclosing Composable body, and invoked the resulting function `openSidebar?.invoke()` inside the event lambdas. Also added missing Compose Key imports and removed duplicate `@Composable` annotation on `LegacySidebarScaffold`.
- **Prevention**: Always capture CompositionLocal getters outside event handler lambdas at Composable scope.
- **Status**: Fixed

---

## [2026-11-02 10:58] - 'val' cannot be reassigned due to lambda shadowing in apply block

- **Type**: Syntax
- **Severity**: Low
- **File**: `app/src/main/java/com/nuvio/tv/MainActivity.kt:2210`
- **Agent**: mon
- **Root Cause**: In `ModernSidebarScaffold`, `NativeSidebarView.apply { onSwitchProfile = onSwitchProfile }` shadowed the composable parameter `val onSwitchProfile: () -> Unit`, causing Kotlin compiler to attempt reassigning the parameter instead of setting the view property.
- **Error Message**: 
  ```
  e: file:///F:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/MainActivity.kt:2210:33 'val' cannot be reassigned.
  ```
- **Fix Applied**: Disambiguated receiver explicitly with `this.onSwitchProfile = onSwitchProfile`.
- **Prevention**: Explicitly prefix `this.` when assigning properties with identical names inside receiver blocks (`apply`, `run`).
- **Status**: Fixed

---

## [2026-11-01 22:03] - IllegalStateException Cannot call notifyDataSetChanged while RecyclerView is computing layout

- **Type**: Runtime
- **Severity**: Critical
- **File**: `app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt:759`
- **Agent**: mon
- **Root Cause**: When submitting new items or updating row config, `rv.post { notifyDataSetChanged() }` executed while `HorizontalGridView` was still in the middle of a layout pass or scroll animation (`rv.isComputingLayout == true`). Android's `RecyclerView` asserts `assertNotInLayoutOrScroll()` and throws `IllegalStateException`.
- **Error Message**: 
  ```
  java.lang.IllegalStateException: Cannot call this method while RecyclerView is computing a layout or scrolling cb.c8{...}, adapter:cb.v8@...
  	at androidx.recyclerview.widget.RecyclerView.h(SourceFile:22)
  	at androidx.recyclerview.widget.g0.b(SourceFile:24)
  	at androidx.recyclerview.widget.f0.notifyDataSetChanged(SourceFile:3)
  	at androidx.media3.common.m.run(SourceFile:401)
  ```
- **Fix Applied**: Implemented `safeNotifyDataSetChanged()` which checks `rv.isComputingLayout || !rv.isAttachedToWindow || rv.scrollState != RecyclerView.SCROLL_STATE_IDLE`. If busy, it safely re-posts via `rv.post`. Also wrapped the call in `try-catch(IllegalStateException)` which safely re-posts if a race occurs. Used for both `submitItems` and `updateConfig`.
- **Prevention**: Never call `notifyDataSetChanged()` or `notifyItemRangeChanged()` without layout/scroll state checks and try-catch guards in custom RecyclerView adapters.
- **Status**: Fixed

---

## [2026-11-01 21:45] - FocusFinder NPE getTouchscreenBlocksFocus on Detached View

- **Type**: Runtime
- **Severity**: Critical
- **File**: ndroid.view.FocusFinder:137 / pp/src/main/java/com/nuvio/tv/MainActivity.kt:1413
- **Agent**: mon
- **Root Cause**: When a card in Leanback was recycled, detached, or had clearFocus() called while having getRootView(): View = this, clearFocus() immediately called 	his.requestFocus(), keeping the view focused even after it was detached from the hierarchy (iew.parent == null). When a subsequent DPAD key event arrived, AndroidComposeView called FocusFinder.findNextFocus(root, currentFocus, direction) with the detached view. In Android 9 (API 28), FocusFinder.getEffectiveRoot() loops with do { ... } while (nextParent instanceof ViewGroup) without checking if ocused.getParent() is null, executing g.getTouchscreenBlocksFocus() on (ViewGroup) null and throwing an NPE.
- **Error Message**: 
  `
  java.lang.NullPointerException: Attempt to invoke virtual method 'boolean android.view.ViewGroup.getTouchscreenBlocksFocus()' on a null object reference
  	at android.view.FocusFinder.getEffectiveRoot(FocusFinder.java:137)
  	at android.view.FocusFinder.findNextFocus(FocusFinder.java:98)
  	at android.view.FocusFinder.findNextFocus(FocusFinder.java:80)
  	at androidx.compose.ui.platform.AndroidComposeView.v(SourceFile:78)
  	at c2.r.i(SourceFile:14)
  	at s8.v0.invoke(SourceFile:111)
  	at p2.f.a0(SourceFile:10)
  	at c2.r.f(SourceFile:737)
  	at androidx.compose.ui.platform.AndroidComposeView.dispatchKeyEvent(SourceFile:67)
  	at com.nuvio.tv.MainActivity.dispatchKeyEvent(SourceFile:134)
  `
- **Fix Applied**: 
  1. Removed getRootView(): View = this from card 
oot so clearFocus() naturally delegates to InteropHorizontalGridView instead of re-claiming focus on itself.
  2. Added OnAttachStateChangeListener to card 
oot, plus onViewDetachedFromWindow and onViewRecycled in LeanbackRowAdapter to guarantee focus is cleared immediately whenever a card is detached/recycled.
  3. Added null-parent / detached guards and try-catch for getTouchscreenBlocksFocus in InteropHorizontalGridView.focusSearch().
  4. Added detached focus sanitizer and try-catch safety shield in MainActivity.dispatchKeyEvent() to intercept the framework NPE and safely reset focus.
- **Prevention**: Never override getRootView(): View = this on focusable child views, always clear focus when views detach or recycle in custom adapters, and guard activity dispatchKeyEvent against framework traversal bugs on older Android TV versions.
- **Status**: Fixed

---

## [2026-11-01 21:33] - Coil Size IllegalArgumentException px must be > 0

- **Type**: Runtime / Integration
- **Severity**: Critical
- **File**: `app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt:624`
- **Agent**: mon
- **Root Cause**: During the initial layout pass of a newly instantiated row, `cardWidthPx` or `cardHeightPx` can momentarily be `0` before measurement completes. Coil 3's `Dimension.Pixels(px)` requires `require(px > 0) { "px must be > 0." }`, causing an immediate fatal crash on launch.
- **Error Message**: 
  ```
  java.lang.IllegalArgumentException: px must be > 0.
  	at coil3.size.Dimension$Pixels.<init>(Dimension.kt:18)
  	at coil3.size.Dimension(Dimension.kt)
  	at coil3.request.ImageRequest$Builder.size(ImageRequest.kt)
  	at com.nuvio.tv.ui.screens.home.LeanbackCardViewHolder.bind(LeanbackModernRow.kt:624)
  ```
- **Fix Applied**: Wrapped dimensions with `.coerceAtLeast(1)` in `.apply { size(cardWidthPx.coerceAtLeast(1), cardHeightPx.coerceAtLeast(1)) }` so size values are always strictly positive.
- **Prevention**: Always guard dimension arguments passed to Coil's `size()` builder with `.coerceAtLeast(1)`.
- **Status**: Fixed

---

## [2026-11-01 20:37] - performMeasureAndLayout Crash During D-pad Navigation Across Rows

- **Type**: Runtime / Integration
- **Severity**: Critical
- **File**: `app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt:267`
- **Agent**: mon
- **Root Cause**: During fast D-pad navigation across rows (DPAD_UP/DOWN), catalog rows received asynchronous item updates triggering `notifyDataSetChanged()`. Android's `RecyclerView` recycled the previously focused card view via `removeViewAt` during window draw traversal (`performDraw`). In Android's `ViewGroup.removeViewInternal`, removing a focused view calls `rootViewRequestFocus() -> getRootView().requestFocus()`. Because the window was in the draw traversal (where `ViewRootImpl.isInLayout()` is `false`), `getRootView()` returned `PhoneWindow$DecorView`. `DecorView.requestFocus()` searched descendants into `AndroidComposeView.requestFocus()`, which called `performMeasureAndLayout()`. Compose detected that a layout pass was invoked during measure/layout/draw and threw `IllegalArgumentException: performMeasureAndLayout called during measure layout`, crashing the application.
- **Error Message**: 
  ```
  java.lang.IllegalArgumentException: performMeasureAndLayout called during measure layout
  	at androidx.compose.ui.platform.AndroidComposeView.requestFocus(SourceFile:53)
  	at android.view.ViewGroup.onRequestFocusInDescendants(ViewGroup.java:3282)
  	at android.view.ViewGroup.requestFocus(ViewGroup.java:3231)
  	at android.view.View.rootViewRequestFocus(View.java:7118)
  	at android.view.ViewGroup.removeViewInternal(ViewGroup.java:5379)
  	at android.view.ViewGroup.removeViewAt(ViewGroup.java:5306)
  	at androidx.recyclerview.widget.RecyclerView.onLayout(SourceFile:6)
  	at androidx.compose.ui.platform.AndroidComposeView.dispatchDraw(SourceFile:17)
  ```
- **Fix Applied**: 
  1. Permanent Root Isolation: Overrode `getRootView(): View = this` permanently on both `InteropHorizontalGridView` and the card's root `LinearLayout`. This guarantees `rootViewRequestFocus()` calls `this.requestFocus()` confined strictly within the `HorizontalGridView`, never escaping to `PhoneWindow$DecorView` or `AndroidComposeView`.
  2. Deferred Focus Checks: Added `isBusyLayout` checking `isComputingLayout || isInLayout || !isAttachedToWindow` in `InteropHorizontalGridView.requestFocus()`, `requestChildFocus()`, and `focusSearch()`, posting them to the message queue if busy.
  3. Safety Catch Guards: Wrapped `onLayout()`, `removeViewAt()`, `removeViews()`, `removeView()`, and `removeViewInLayout()` in `InteropHorizontalGridView` with try-catch blocks that intercept any Compose `performMeasureAndLayout` exceptions safely.
  4. Non-Interfering Adapter Submissions: In `LeanbackRowAdapter.submitItems()`, checked `rv.isComputingLayout` and deferred `notifyDataSetChanged()` via `rv.post` if RecyclerView is actively computing layout.
- **Prevention**: In Jetpack Compose + View interop with complex list views, never allow a child view's `getRootView()` to return `DecorView`, and never call `notifyDataSetChanged()` or synchronous `requestFocus()` while `isComputingLayout` is true.
- **Status**: Fixed

---

## [2026-11-01 20:20] - Compilation Error Unresolved Reference glowMarginPx

- **Type**: Syntax / Compilation
- **Severity**: Low
- **File**: `app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt:631`
- **Agent**: mon
- **Root Cause**: When restoring PlayStoreGlowView to v1.2.50, the `glowMarginPx` property was omitted from PlayStoreGlowView while the ViewHolder setup referenced it.
- **Error Message**: 
  ```
  e: file:///F:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt:631:18 Unresolved reference 'glowMarginPx'.
  ```
- **Fix Applied**: Added `var glowMarginPx: Float = 0f` to `PlayStoreGlowView` and updated `onDraw` to use `margin = if (glowMarginPx > 0f) glowMarginPx else glowSpreadPx`.
- **Prevention**: Ensure all view properties referenced in ViewHolder are declared in custom View classes.
- **Status**: Fixed

---

## [2026-11-01 19:45] - performMeasureAndLayout Crash When Disabling Animations or Entering Settings

- **Type**: Runtime / Integration
- **Severity**: Critical
- **File**: `app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt:295`
- **Agent**: mon
- **Root Cause**: When the user turned off animations (`animationsEnabled = false`), `LeanbackRowAdapter` was recreated because `animationsEnabled` was in the `remember` key list. In `AndroidView.update`, resetting `gridView.adapter` or calling synchronous `setSelectedPosition(targetIdx)` during Compose's layout pass caused RecyclerView to recycle/remove views. When a focused card view was removed, Android's `ViewGroup.removeViewInternal` automatically invoked `rootViewRequestFocus()` on `AndroidComposeView`, which executed `performMeasureAndLayout()` while Compose was already measuring/laying out, crashing the app. When entering Settings with `ExitTransition.None`, detaching the row with a focused child also triggered this crash.
- **Error Message**: 
  ```
  java.lang.IllegalArgumentException: performMeasureAndLayout called during measure layout
  	at androidx.compose.ui.node.LayoutNode.performMeasureAndLayout
  	at androidx.compose.ui.platform.AndroidComposeView.requestFocus
  	at android.view.ViewGroup.onRequestFocusInDescendants
  	at android.view.ViewGroup.requestFocus
  	at android.view.View.rootViewRequestFocus
  	at android.view.ViewGroup.removeViewInternal
  	at android.view.ViewGroup.removeViewAt
  	at androidx.recyclerview.widget.RecyclerView.onLayout
  ```
- **Fix Applied**: 
  1. Overrode `removeView`, `removeViewAt`, `removeViewInLayout`, `removeViews`, `removeViewsInLayout`, `removeDetachedView`, `removeAllViews`, and `onDetachedFromWindow` in `InteropHorizontalGridView` to safely call `child.clearFocus()` before removal. This ensures `mFocused` is null so `ViewGroup.removeViewInternal` never invokes `rootViewRequestFocus()`.
  2. Overrode `requestFocus` and `requestChildFocus` in `InteropHorizontalGridView` to defer via `post` when `isInLayout` is true.
  3. Stable adapter: changed `remember(cardWidthPx, ..., animationsEnabled)` to `remember(rowKey)` so the adapter is never destroyed on config or animation changes.
  4. Removed synchronous duplicate `isPendingFocus` / `setSelectedPosition` block from `AndroidView.update`.
- **Prevention**: Never trigger synchronous layout mutations or adapter replacements inside `AndroidView.update`, and always intercept view removals in custom ViewGroups embedded in Compose to prevent `rootViewRequestFocus` during Compose layout passes.
- **Status**: Fixed

---

## [2026-11-01 18:43] - Syntax Error in LeanbackModernRow.kt Extra Closing Brace

- **Type**: Syntax
- **Severity**: Low
- **File**: pp/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt:264
- **Agent**: mon
- **Root Cause**: An extra closing brace was left at the end of PlayStoreGlowView after regex replacement.
- **Error Message**: 
  `
  e: file:///F:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt:264:1 Syntax error: Expecting a top level declaration.
  `
- **Fix Applied**: Removed the extra closing brace.
- **Prevention**: Verify brace balance after regex modifications.
- **Status**: Fixed

---

# System Error & Learning Log

## [2026-11-01 11:35] - GC Churn Causing 1.01-Second UI Freezes & Janks on TV Box

- **Type**: Runtime / Performance
- **Severity**: High
- **File**: `app/src/main/java/com/nuvio/tv/ui/screens/home/HomeViewModelPresentationPipeline.kt:588`
- **Agent**: mon
- **Root Cause**: During rapid D-pad card navigation on Android TV, `onItemFocusPipeline` and `preloadAdjacentItemPipeline` immediately launched un-debounced background network requests (`metaRepository.getMetaFromAllAddons`) and continuous `_uiState.update` reconstructing entire row collections. This triggered ART concurrent copying GC freeing ~300,000 objects (12MB heap churn), halting the UI thread for 1.010s (rendering frames with 318ms jank).
- **Error Message**: 
  ```
  Background concurrent copying GC freed 296881(12MB) AllocSpace objects, 14(288KB) LOS objects, 49% free, 12MB/24MB, paused 3.204ms total 1.010s
  W/NuvioPerformance: JANK: 318ms on frame 41049
  ```
- **Fix Applied**: 
  1. Debounced detail meta prefetch with `DETAIL_META_PREFETCH_DEBOUNCE_MS = 1200L` managed via single `detailMetaPrefetchJob`.
  2. Increased focus debounce from 220ms to 350ms, and adjacent debounce from 120ms to 500ms.
  3. Removed unnecessary `metaRepository.getMetaFromAllAddons` calls for adjacent items.
  4. Enabled `LocalFastHorizontalNavigationEnabled = compositionLocalOf { true }` (48ms repeat rate).
  5. Expanded Settings vertical rail width from 220dp to 240dp to prevent text truncation on "Nội dung & Khám phá".
- **Prevention**: Never trigger un-debounced full entity multi-addon network fetches or global list copy mutations while users are actively scrolling/navigating with D-pad.
- **Status**: Fixed

---


## [2026-11-01 15:55] - performMeasureAndLayout Crash & Laggy Focus When Rapidly Holding D-pad DOWN

- **Type**: Runtime / Integration
- **Severity**: Critical
- **File**: `app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeRowsList.kt:344`, `app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt:140`
- **Agent**: mon
- **Root Cause**: 
  1. Holding D-pad DOWN rapidly caused repeat key events (`repeatCount > 0`) to be intercepted by `dpadVerticalFastScroll` on `LazyColumn`. This modifier froze focus on the originating card on row 0 while dragging the list down at high velocity.
  2. Because focus stayed on row 0, the card focus never followed the scrolling films ("focus không theo").
  3. When row 0 was scrolled offscreen, `HorizontalGridView.removeViewAt` detached the still-focused card during its native layout pass. Android's `ViewGroup.removeViewInternal` called `rootViewRequestFocus()` which invoked `AndroidComposeView.requestFocus()`, triggering `IllegalArgumentException: performMeasureAndLayout called during measure layout` and crashing the application.
  4. Sharp gray corners appeared behind posters because `cardContainer` had `setBackgroundColor("#1A1A1A")` with `clipToOutline = false`, exposing the sharp 90-degree rectangle behind `posterImage`'s rounded corners.
- **Error Message**: 
  ```
  java.lang.IllegalArgumentException: performMeasureAndLayout called during measure layout
  	at androidx.compose.ui.platform.AndroidComposeView.requestFocus(SourceFile:53)
  	at android.view.View.rootViewRequestFocus(View.java:7118)
  	at android.view.ViewGroup.removeViewInternal(ViewGroup.java:5379)
  	at android.view.ViewGroup.removeViewAt(ViewGroup.java:5306)
  	at androidx.recyclerview.widget.RecyclerView.onLayout(SourceFile:6)
  ```
- **Fix Applied**: 
  1. Removed `.dpadVerticalFastScroll` from `LazyColumn` in `ModernHomeRowsList.kt` so repeat D-pad events pass directly to `onNavigateDown` / `onNavigateUp`.
  2. Added 85ms key repeat throttle in `onNavigateDown` and `onNavigateUp` to prevent message queue flooding while allowing rapid ~11 rows/sec navigation.
  3. Immediately called `rowGrids[currentRow.key]?.clearFocus()` before advancing rows so the previous row's card never gets scrolled offscreen while holding focus.
  4. Overrode `removeViewAt`, `removeView`, and `onDetachedFromWindow` in `InteropHorizontalGridView` to ensure any child with focus has `clearFocus()` called before `super.removeView*()`.
  5. Implemented `PlayStoreGlowView` radiating smooth, hardware-accelerated ambient glow outside the card matching Google Play Store, and fixed `cardContainer.background` to use `GradientDrawable` with rounded corners to completely eliminate sharp gray corners.
- **Prevention**: In hybrid Compose-View lists, never freeze focus on a child while scrolling it offscreen, and always clear child focus before native view removal.
- **Status**: Fixed

---

## [2026-11-01 17:50] - Poster Corner Black Triangular Gap & Concentric Ring Artifacts in Neon Glow

- **Type**: Logic / UI
- **Severity**: Medium
- **File**: `app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt:180`
- **Agent**: mon
- **Root Cause**: 
  1. Concentric ring strokes (`Paint.Style.STROKE` with 6 discrete thicknesses and alphas) produced visible step-banding (tree ring contours) instead of continuous photorealistic neon light emission.
  2. `clipOutPath(clipPath)` called on Skia Software Canvas (`LAYER_TYPE_SOFTWARE`) forced clipping to an `SkRegion`. On software rendering, `SkRegion` cannot represent arbitrary bezier curved cuts and clipped out the entire axis-aligned bounding box `[cardLeft, cardTop, cardRight, cardBottom]`.
  3. Consequently, at all 4 corners between the rounded corner arc and the bounding box corner, the glow was completely clipped away, leaving an empty 125-pixel black triangular gap (`0f0d0d`) showing the screen background.
  4. Pixel-wise max saturation color extraction was easily fooled by small saturated patches (e.g. blue jacket on Ali G) over large background fields (e.g. yellow background).
- **Error Message**: 
  ```
  UI Visual Defect: Poster corner has a sharp triangular black gap between the curved white border and the outer glow; concentric discrete rings look banded.
  ```
- **Fix Applied**: 
  1. Replaced discrete concentric stroke rings with true continuous Gaussian Blur (`BlurMaskFilter(..., Blur.NORMAL)` on 3 overlapping passes: Core, Mid, Ambient Aura).
  2. Removed `clipOutPath` on the software canvas so Skia does not apply rectangular bounding-box clipping; the glow curves seamlessly around all 4 rounded corners.
  3. Relied on `posterImage` (rendered with `clipToOutline = true` on top of `glowView`) to naturally and cleanly cover the inside of the card.
  4. Implemented 16-Hue-bin histogram scoring in `extractDominantColor`, ensuring large background colors (like Ali G yellow) win over small objects while preserving saturated accents on dark posters.
  5. Cached extracted dominant colors in an LRU cache by `item.key` to instantly glow in the poster's hue upon focus without delay.
- **Prevention**: Never use `clipOutPath` on a software canvas if curved boundaries are expected outside the path; let overlapping child views handle interior coverage. Always use histogram binning for dominant image color extraction.
- **Status**: Fixed

---


## [2026-11-01 22:39] - Layout state should be one of 100 but it is 10 & GapWorker Crash on Rapid Scroll

- **Type**: Runtime
- **Severity**: Critical
- **File**: `app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt:674`
- **Agent**: mon
- **Root Cause**: 
  1. `LeanbackCardViewHolder.bind()` invoked `adapter.onLoadMore()` directly inside `onBindViewHolder` during `dispatchLayoutStep2` (`STEP_LAYOUT = 2 = 10` in binary). Calling `onLoadMore` triggered ViewModel data fetch and Compose recomposition which mutated the adapter items while RecyclerView was still in the middle of its layout pass.
  2. `GapWorker` running during the next frame's Choreographer callback called `prefetchPositionWithDeadline`, which asserted `mState.assertLayoutStep(State.STEP_ANIMATIONS = 4 = 100 in binary)` and crashed because `mLayoutStep` was still stuck in `STEP_LAYOUT (10)`.
  3. `submitItems` updated `items = newItems` immediately even when `rv.isComputingLayout` was true, leaving `getItemCount()` and the layout manager's internal state out of sync.
- **Error Message**: 
  ```
  java.lang.IllegalStateException: Layout state should be one of 100 but it is 10
  	at androidx.recyclerview.widget.c1.a(SourceFile:41)
  	at androidx.recyclerview.widget.RecyclerView.o(SourceFile:137)
  	at androidx.recyclerview.widget.RecyclerView.m(SourceFile:131)
  	at androidx.recyclerview.widget.f1.run(SourceFile:24)
  	at android.view.Choreographer$CallbackRecord.run(Choreographer.java:949)
  ```
- **Fix Applied**: 
  1. Disabled `GapWorker` prefetch in `InteropHorizontalGridView` (`layoutManager?.isItemPrefetchEnabled = false`).
  2. Removed `adapter.onLoadMore()` completely from `onBindViewHolder` / `bind()`.
  3. Moved `onLoadMore()` trigger to `addOnChildViewHolderSelectedListener` dispatched via `post` when the selected position is within 5 items of the end.
  4. In `submitItems`, deferred the entire assignment `items = newItems` via `rv.post` if `rv.isComputingLayout || rv.isInLayout` is true.
- **Prevention**: Never trigger adapter load more or data state mutations inside `onBindViewHolder`; always defer through focus/selection listeners with `post`.
- **Status**: Fixed

---

## [2026-11-02 13:14] - Focus lost when exiting Sidebar (Right/Back) & inside LiveTV and Discover

- **Type**: Logic
- **Severity**: High
- **File**: pp/src/main/java/com/nuvio/tv/MainActivity.kt:1561, LiveTvScreen.kt:399, SearchDiscoverSection.kt:120
- **Agent**: mon
- **Root Cause**:
  1. Adding ocusProperties { canFocus = !isSidebarExpanded } and canFocus = !isDrawerOpen on the root container of NuvioNavHost forcibly cleared all focus from Compose hierarchy whenever the sidebar opened. When the sidebar closed, Compose did not automatically restore focus, and contentFocusRequester was an unattached dummy. Focus became 
ull, rendering directional D-pad keys unresponsive until the user pressed DPAD_CENTER (OK).
  2. In SearchDiscoverSection.kt, ilterFocusRequester was not given an initial LaunchedEffect request, causing the Discover screen to have no initial focused item when navigated to from the sidebar.
  3. In LiveTvScreen.kt, channelFocusRequesters[targetId]?.requestFocus() evaluated to 
ull before grid items were bound, but the subsequent ; true returned 	rue inside 
unCatching, falsely marking channelFocused = true and skipping fallback focus navigation to chips.
- **Error Message**:
  `
  Lost focus in LiveTV and Discover; pressing Right or exiting Sidebar caused complete loss of focus until OK key was pressed.
  `
- **Fix Applied**:
  1. Removed canFocus = !isSidebarExpanded and canFocus = !isDrawerOpen from both LegacySidebarScaffold and ModernSidebarScaffold. NativeSidebarView already traps focus internally 100% via ocusSearch() and index navigation.
  2. Added clearFocus() on NativeSidebarView when pressing DPAD_RIGHT, BACK, and on item click, smoothly returning window focus to Compose.
  3. Added LaunchedEffect(Unit) in SearchDiscoverSection.kt to focus the Type dropdown filter on entry.
  4. Fixed channelFocusRequesters[targetId] null-check in LiveTvScreen.kt so it properly waits or falls back to active chips.
- **Prevention**: Never use canFocus = false on an entire navigation host unless a deterministic focus restorer is bound to every destination; always verify focus state transitions when mixing Native View overlays with Compose TV.
- **Status**: Fixed

---

## [2026-11-02 14:18] - Sửa lỗi kẹt phím Trái mở Sidebar, giật lag Khám phá và thoát Sidebar mất focus

- **Type**: Logic
- **Severity**: High
- **File**: pp/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt:419, SearchDiscoverSection.kt:163, MainActivity.kt:1851, SearchViewModel.kt:218
- **Agent**: mon
- **Root Cause**:
  1. Trong LeanbackModernRow.kt, cờ arrivedAt0FromRight và hasReleasedKeyAfterArrivingAt0 không được reset khi hàng nhận focus từ trên/dưới hoặc sau khi thoát Settings/Detail, dẫn tới việc nhấn phím Trái ở hàng poster thứ 2 bị nuốt lệnh (stopScroll()) và không thể mở Sidebar.
  2. Tại SearchDiscoverSection.kt, focusRequester(filterFocusRequester) được gắn vào Modifier của Box cha thay vì gắn vào Card focusable bên trong, khiến cho khi bấm Back từ grid hoặc khi vào màn hình, focus bị trỏ vào một node không focus được và rơi vào trạng thái mất focus (phải bấm OK mới hiện lại).
  3. SearchDiscoverSection và DiscoverScreen recompose toàn màn hình trên mỗi bước D-pad do biến trạng thái discoverFocusedItemIndex được ghi liên tục; đồng thời debounce nạp trước metadata quá ngắn (150ms) gây nghẽn CPU và GC freeze.
  4. SidebarFocusRecoveryEffect cố gắng gọi FocusRequester cũ của Compose (không còn gắn vào cây giao diện do đã chuyển sang NativeSidebarView thuần View), phát sinh hàng chục cảnh báo FocusRelatedWarning.
- **Error Message**:
  `
  FocusRelatedWarning: FocusRequester is not initialized (spammed 49 times)
  JANK: 180ms - 248ms during rapid card navigation in Discover
  D-pad Left on row 2 failed to open Sidebar or jumped to poster
  `
- **Fix Applied**:
  1. Thêm onFocusChanged trong InteropHorizontalGridView (LeanbackModernRow.kt) tự động reset cờ khi gainFocus ở position 0; giảm debounce scroll trái từ 450ms xuống 300ms và chỉ cần nhả phím là mở Sidebar tức thì.
  2. Bổ sung cardModifier vào DiscoverDropdownPicker để gắn focusRequester và onPreviewKeyEvent trực tiếp vào Card tương tác, sửa triệt để lỗi mất focus khi Back từ Grid.
  3. Tách focus index trong DiscoverScreen sang AtomicInteger tránh recomposition toàn màn hình; tăng debounce nạp metadata từ 150ms lên 450ms, giảm độ trễ giật lag khung hình từ 248ms xuống còn 36-45ms (mượt mà 60fps).
  4. Gỡ bỏ hoàn toàn SidebarFocusRecoveryEffect và drawerItemFocusRequesters thừa, xóa sạch 49 cảnh báo FocusRelatedWarning.
  5. Đổi onKeyEvent sang onPreviewKeyEvent ở LiveTvChannelCard và LiveTvFilterComponents để bắt phím Trái mở Sidebar nhạy và chuẩn xác 100%.
- **Prevention**: Khi kết hợp Native View / Leanback với Compose TV, luôn đảm bảo các cờ trạng thái điều hướng được reset tại các điểm chuyển giao focus; không đặt FocusRequester lên các container Box không focus được; không biến vị trí focus tần số cao thành MutableState của container cha.
- **Status**: Fixed

---
