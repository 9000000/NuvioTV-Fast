# BÁO CÁO KIỂM TOÁN & KẾ HOẠCH TỐI ƯU HÓA TOÀN DIỆN UI ANDROID LEANBACK NATIVE

## 1. TỔNG QUAN PHẠM VI KIỂM TOÁN
Đã rà soát toàn bộ **28 tệp mã nguồn** thuộc kiến trúc Android Leanback Native và tất cả các màn hình tiêu thụ (Consumers):
- **Core Leanback (`ui/leanback/`)**: `LeanbackNativeRow.kt`, `LeanbackNativeGrid.kt`, `LeanbackUniversalAdapter.kt`, `InteropHorizontalGridView.kt`, `InteropVerticalGridView.kt`, `LeanbackColorEngine.kt`, `PillProgressBarDrawable.kt`, `PlayStoreGlowView.kt`, `LeanbackBaseViewHolder.kt`, `LeanbackPosterViewHolder.kt`, `LeanbackEpisodeViewHolder.kt`, `LeanbackAvatarViewHolder.kt`, `LeanbackCardItem.kt`, `LeanbackPoolManager.kt`.
- **Home Legacy Leanback (`ui/screens/home/`)**: `LeanbackModernRow.kt`, `ModernHomeRowsList.kt`.
- **Màn hình & Component tiêu thụ**: `CatalogRowSection.kt`, `SearchScreen.kt`, `SearchDiscoverSection.kt`, `CatalogSeeAllScreen.kt`, `TmdbEntityBrowseScreen.kt`, `CastDetailScreen.kt`, `FolderDetailScreen.kt`, `EpisodesSection.kt`, `CastSection.kt`, `CollectionSection.kt`, `MoreLikeThisSection.kt`, `TrailerSection.kt`.

---

## 2. NHỮNG ĐIỂM ĐÃ ĐƯỢC TỐI ƯU TỐT (STRENGTHS)
1. **Zero Compose Subcomposition bên trong Card**: Toàn bộ ViewHolder (`Poster`, `Episode`, `Avatar`) được dựng bằng Android View thuần (`LinearLayout`, `FrameLayout`, `ImageView`, `TextView`), loại bỏ hoàn toàn chi phí Recomposition khi cuộn nhanh trên Android TV.
2. **Deferred Software Layer Glow (55ms)**: `PlayStoreGlowView` (sử dụng `LAYER_TYPE_SOFTWARE` cho `BlurMaskFilter`) tự động ẩn đi khi người dùng giữ phím D-pad cuộn liên tục và chỉ kích hoạt lại sau `55ms` khi dừng cuộn, giảm tải đáng kể cho CPU/Skia.
3. **Zero GPU Overdraw**: Khi ảnh Coil tải thành công (`onSuccess`), `cardContainer.background = null` được gọi để xóa lớp nền chờ `#1A1A1A` phía dưới tấm ảnh.
4. **Hardware Bitmaps + LRU Color Cache (512 mục)**: `LeanbackColorEngine` lưu cache màu neon chủ đạo theo `item.key`; khi màu đã có trong cache, Coil bật `.allowHardware(true)` để nạp thẳng bitmap lên GPU.
5. **Cô lập RootView (`getRootView(): View = this`)**: Ngăn chặn hành vi `rootViewRequestFocus()` của Android View nhảy ngược lên `AndroidComposeView` làm crash ứng dụng khi đang `measure/layout`.
6. **Khóa điều hướng D-Pad tại màn Tìm kiếm (`SearchScreen`)**: Đã xử lý triệt để hiện tượng trượt focus / mất focus khi chuyển giao giữa các hàng `InteropHorizontalGridView` và lưới `InteropVerticalGridView`.

---

## 3. DANH SÁCH 13 LỖI & ĐIỂM NGHẼN CÒN TỒN ĐỌNG (BUGS & BOTTLENECKS)

### Nhóm A: Lỗi Cướp Focus & Mất Focus nghiêm trọng (Critical / High)

1. **[CRITICAL] Lỗi tự động cướp Focus về tập đầu/anchor mỗi khi Recompose tại màn Chi tiết phim (`EpisodesSection`, `CollectionSection`, `MoreLikeThisSection`, `TrailerSection`)**
   - **Vị trí**:
     - `LeanbackNativeRow.kt:256-260`
     - `EpisodesSection.kt:550, 599`
     - `CollectionSection.kt:112`, `MoreLikeThisSection.kt:103`, `TrailerSection.kt:106`
   - **Nguyên nhân**:
     - Trong `LeanbackNativeRow.kt`, bên cạnh `LaunchedEffect(isPendingFocus, pendingFocusNonce)` (dòng 152), khối `update` của `AndroidView` (dòng 256-260) cũng gọi trực tiếp `executeRowFocus(gridView, targetIdx)` mỗi khi `isPendingFocus == true`.
     - Trong `EpisodesSection.kt:550`, `targetFocusEpisodeId = restoreEpisodeId ?: scrollToEpisodeId ?: anchorEpisodeId` luôn khác `null` (do `anchorEpisodeId` luôn có giá trị), dẫn đến `isPendingFocus = !targetFocusEpisodeId.isNullOrBlank()` **luôn bằng `true`**, đồng thời `EpisodesSection` (cũng như `CollectionSection`, `MoreLikeThisSection`, `TrailerSection`) **không truyền callback `onClearPendingFocus`**.
   - **Hệ quả**: Bất kỳ khi nào màn hình Chi tiết phim recompose (ví dụ tải xong IMDb rating, tiến độ xem, hoặc đổi trạng thái nút), khối `update` của `LeanbackNativeRow` lập tức bắn `executeRowFocus` và **cướp focus nhảy ngược về tập phim `anchorEpisodeId`**!

2. **[CRITICAL] Bản sao `LeanbackModernRow.kt` (1,208 dòng) ở Home Screen bị phân mảnh, thiếu toàn bộ các bản vá Focus & Z-Index**
   - **Vị trí**: `LeanbackModernRow.kt:1-1208`
   - **Nguyên nhân**: File này nhân bản toàn bộ `PlayStoreGlowView`, `InteropHorizontalGridView`, `LeanbackCardViewHolder`, `LeanbackRowAdapter` nhưng không được đồng bộ với gói chuẩn `ui/leanback/`:
     - `requestFocus` (dòng 341-347): Chỉ gọi `super.requestFocus`, không ép focus xuống `ViewHolder` con và không gọi `updateFocusVisual(true)`.
     - `focusSearch` (dòng 357-362): Không khóa biên trái/phải (`FOCUS_LEFT`/`FOCUS_RIGHT`) và không chặn `if (result === this) return focused`.
     - `onFocusChanged` (dòng 407-415) & `onChildViewHolderSelected` (dòng 307-328): Không chuyển giao focus từ `GridView` xuống card con khi đang layout -> gây mất viền focus khi cuộn dọc nhanh trên Home Screen.
     - `updateFocusVisual` (dòng 631-632): Thiếu `root.translationZ = if (hasFocus) 12f else 0f`, khiến card đang phóng to `1.08x` bị card bên phải đè lên trên làm cắt lẹm hiệu ứng hào quang (`PlayStoreGlowView`).
     - `onScrollSettled` (dòng 854-858): Không gọi lại `vh.updateFocusVisual(true)` khi dừng cuộn, làm hào quang có thể không bật lại sau khi giữ phím cuộn nhanh.

3. **[HIGH] Không khôi phục được Focus khi bấm Back từ Detail về `CatalogSeeAllScreen` + Dead Code `LazyGridState`**
   - **Vị trí**: `CatalogSeeAllScreen.kt:131-203, 264-308`
   - **Nguyên nhân**:
     - `CatalogSeeAllScreen` đã chuyển sang dùng `LeanbackNativeGrid`, nhưng vẫn giữ lại `gridState = rememberLazyGridState()` (dòng 131) và `restoreFocusRequester` (dòng 132) không gắn vào bất kỳ Composable nào.
     - `LaunchedEffect(shouldRestoreFocus)` (dòng 187-203) cố cuộn `gridState` và gọi `restoreFocusRequester.requestFocus()` (luôn ném lỗi ngầm), trong khi `LeanbackNativeGrid` (dòng 264) **không được truyền `restoreFocusNonce`**. Vì `initialFocusDone` đã là `true` từ lần mở đầu tiên, khi quay lại từ màn Detail, `LeanbackNativeGrid` không bao giờ focus lại vào card vừa xem.

4. **[HIGH] Phá hủy Adapter mỗi khi khôi phục Focus và kẹt D-pad Lên tại `CastDetailScreen`**
   - **Vị trí**: `CastDetailScreen.kt:215, 291, 622-644`
   - **Nguyên nhân**:
     - Dòng 623 truyền `rowKey = "cast_filmography_${restoreFocusToken}"`. Mỗi lần quay lại từ Detail (`restoreFocusToken` tăng lên), `remember(rowKey)` trong `LeanbackNativeRow` hủy toàn bộ `LeanbackUniversalAdapter` và tạo lại từ đầu, thay vì dùng cơ chế `isPendingFocus = restoreFocusToken > 0, pendingFocusNonce = restoreFocusToken`.
     - Thiếu `onNavigateUp` trên `LeanbackNativeRow` để di chuyển D-pad Lên từ danh sách phim quay lại khu vực Tiểu sử (`portraitFocusRequester`).

5. **[MEDIUM] Thiếu điều hướng D-Pad dọc & tồn đọng Dead Code tại `TmdbEntityBrowseScreen`, `EpisodesSection`, `FolderDetailScreen`**
   - **Vị trí**:
     - `TmdbEntityBrowseScreen.kt:201-206, 289-321, 461-562`: Tạo `railFocusRequesters`, `railListStates`, `firstCardFocusRequester` nhưng không gắn vào `EntityRailRow`; thiếu `onNavigateUp`/`onNavigateDown` giữa các hàng rail.
     - `EpisodesSection.kt:435-521`: Giữ `lazyListState = rememberLazyListState()` và 3 `LaunchedEffect` cuộn `lazyListState` không còn tác dụng.
     - `FolderDetailScreen.kt:554-567`: `BackHandler` trong `RowsContent` gọi `listState?.scrollToItem(0, 0)` của Compose cũ thay vì đưa `LeanbackNativeRow` về vị trí `0`.

---

### Nhóm B: Lỗi Tái sử dụng ViewHolder (View Recycling) & Hiển thị (High)

6. **[HIGH] Mất tiêu đề phim (`titleText == null`) khi dùng chung `SharedRecycledViewPool`**
   - **Vị trí**: `LeanbackPosterViewHolder.kt:304-318` và `LeanbackModernRow.kt:991-1004`
   - **Nguyên nhân**: Trong `create()`, `titleText` chỉ được khởi tạo nếu `showLabels == true` lúc tạo ViewHolder. Khi ViewHolder từ hàng có `showLabels = false` được trả về `LeanbackPoolManager.sharedPool` (`VIEW_TYPE_POSTER`) và tái sử dụng cho hàng có `showLabels = true`, `titleText` mang giá trị `null` vĩnh viễn -> **Card bị mất tên phim**.

7. **[HIGH] `LeanbackAvatarViewHolder.unbind()` làm hỏng nền tròn (Oval) của diễn viên thành hình chữ nhật & gây rác GC**
   - **Vị trí**: `LeanbackBaseViewHolder.kt:165-175` và `LeanbackAvatarViewHolder.kt:121, 142-146`
   - **Nguyên nhân**: Khi ảnh diễn viên tải xong, `cardContainer.background = null`. Khi cuộn qua và ViewHolder bị `unbind()`, `LeanbackBaseViewHolder.unbind()` gán lại `GradientDrawable()` mới hình chữ nhật (`cornerRadius = cornerRadiusPx`) mà không kiểm tra `isCircle` (`GradientDrawable.OVAL`). Đồng thời việc khởi tạo `new GradientDrawable()` mỗi lần `unbind()` gây cấp phát bộ nhớ thừa khi cuộn nhanh.

8. **[HIGH] Bỏ sót hoàn toàn trạng thái "Đã xem" (`isWatched`) và Phụ đề (`subtitle`) trong ViewHolder**
   - **Vị trí**:
     - `LeanbackCardItem.kt:21, 25, 42`
     - `LeanbackPosterViewHolder.kt:79-191`, `LeanbackEpisodeViewHolder.kt:82-204`
   - **Nguyên nhân**: Model `LeanbackCardItem.Poster` và `Episode` đã nhận `isWatched` và `subtitle` từ các màn hình, nhưng cả 2 ViewHolder đều chưa vẽ huy hiệu (badge) "Đã xem" và dòng `subtitle`.

9. **[HIGH] Race Condition màu Glow khi tải ảnh bất đồng bộ & thiếu trích xuất màu cho Avatar**
   - **Vị trí**: `LeanbackPosterViewHolder.kt:158-176`, `LeanbackEpisodeViewHolder.kt:173-189`, `LeanbackAvatarViewHolder.kt:71, 119-125`
   - **Nguyên nhân**: Callback `onSuccess` của Coil chưa kiểm tra `if (currentItem?.key != item.key) return` trước khi gán `posterImage`, `currentDominantColor` và `updateFocusVisual`. Ngoài ra `LeanbackAvatarViewHolder` có đọc `LeanbackColorEngine.getCachedColor` nhưng chưa gọi `extractDominantColor` trong `onSuccess`.

---

### Nhóm C: Lỗi Layout, Spacing & Hiệu năng (High / Medium)

10. **[HIGH] `LeanbackNativeGrid.kt` bỏ qua hoàn toàn tham số `horizontalSpacing`**
    - **Vị trí**: `LeanbackNativeGrid.kt:42, 67, 169, 191-222`
    - **Nguyên nhân**: Tham số `horizontalSpacing` được truyền vào nhưng không được sử dụng; dòng 169 gọi `setItemSpacing(verticalSpacingPx)` khiến khoảng cách ngang giữa các cột bị ép bằng khoảng cách dọc (`20.dp`), làm lệch lề phải của lưới poster. Cần tách rõ `setHorizontalSpacing(horizontalSpacingPx)` và `setVerticalSpacing(verticalSpacingPx)`, đồng thời cập nhật trong khối `update`.

11. **[MEDIUM] `LeanbackNativeRow.kt` chưa cập nhật chiều cao hàng & khoảng cách trong khối `update`**
    - **Vị trí**: `LeanbackNativeRow.kt:228-261`
    - **Nguyên nhân**: Khi `cardHeight`, `showLabels`, hoặc `startPadding` thay đổi động từ Settings/State, khối `update` chưa gọi lại `setRowHeight(totalRowHeightPx)`, `setItemSpacing(spacingPx)`, và `setPadding(...)`.

12. **[MEDIUM] `LeanbackUniversalAdapter` & `LeanbackRowAdapter` dùng `notifyDataSetChanged()` thay vì `DiffUtil`**
    - **Vị trí**: `LeanbackUniversalAdapter.kt:101-125`, `LeanbackModernRow.kt:878-889`
    - **Nguyên nhân**: Mọi thay đổi dữ liệu không phải `isAppendOnly` (như cập nhật `progressPercent`, `isWatched`, hoặc đổi bộ lọc) đều gọi `notifyDataSetChanged()`, làm nháy toàn bộ hàng và unbind/rebind lại từ đầu.

13. **[MEDIUM] `LeanbackColorEngine.extractDominantColor(bmp)` chạy đồng bộ trên Main Thread**
    - **Vị trí**: `LeanbackPosterViewHolder.kt:168`, `LeanbackEpisodeViewHolder.kt:181`, `LeanbackModernRow.kt:743`
    - **Nguyên nhân**: Khi nhiều ảnh hoàn tất tải cùng lúc (đặc biệt khi đọc từ Disk Cache), việc quét pixel và tính `Color.colorToHSV` ~196 lần/ảnh ngay trên UI Thread có thể gây rớt khung hình (frame drop).

---

## 4. KẾ HOẠCH KHẮC PHỤC CHI TIẾT (IMPLEMENTATION PLAN - 4 GIAI ĐOẠN)

### Giai đoạn 1: Khắc phục triệt để các lỗi Cướp Focus, Mất Focus & Điều hướng D-Pad (Critical / High)
1. **`LeanbackNativeRow.kt`**:
   - Xóa lệnh gọi `executeRowFocus` bị trùng lặp trong khối `update` (dòng 256-260), chỉ giữ duy nhất cơ chế điều phối qua `LaunchedEffect(isPendingFocus, pendingFocusNonce)`.
   - Cập nhật động `setRowHeight`, `setItemSpacing`, `setPadding`, `windowAlignmentOffset` trong khối `update` khi cấu hình kích thước thay đổi.
2. **`EpisodesSection.kt`, `CollectionSection.kt`, `MoreLikeThisSection.kt`, `TrailerSection.kt`**:
   - Tách bạch trạng thái cuộn khởi tạo (`initialScrollIndex`) với trạng thái ép focus (`isPendingFocus` chỉ bật khi thực sự cần khôi phục focus `restoreEpisodeId != null` hoặc chuyển season `scrollToEpisodeId != null`, và truyền `onClearPendingFocus` để tắt ngay sau khi focus thành công).
   - Dọn sạch toàn bộ Dead Code `lazyListState` và `episodeFocusRequesters` cũ trong `EpisodesSection.kt`.
3. **`CatalogSeeAllScreen.kt`**:
   - Xóa Dead Code `gridState` và `restoreFocusRequester`.
   - Thêm biến đếm `restoreFocusNonce` truyền vào `LeanbackNativeGrid` để khi bấm Back từ màn Detail quay lại, `LeanbackNativeGrid` khôi phục focus chính xác vào đúng poster vừa xem.
4. **`CastDetailScreen.kt`, `TmdbEntityBrowseScreen.kt`, `FolderDetailScreen.kt`**:
   - Giữ `rowKey = "cast_filmography"` cố định trong `CastDetailScreen.kt`, truyền `isPendingFocus = restoreFocusToken > 0`, `pendingFocusNonce = restoreFocusToken`, và thêm `onNavigateUp` về `portraitFocusRequester`.
   - Kết nối điều hướng D-pad Lên/Xuống (`onNavigateUp` / `onNavigateDown`) và khôi phục focus chuẩn cho các hàng `LeanbackNativeRow` trong `TmdbEntityBrowseScreen.kt` và `FolderDetailScreen.kt`.

### Giai đoạn 2: Sửa lỗi Tái sử dụng ViewHolder (Recycling), Spacing & Hiển thị (High)
1. **`LeanbackPosterViewHolder.kt`**:
   - Luôn khởi tạo `titleText` và `subtitleText` trong `create()`, điều khiển `visibility = View.VISIBLE / View.GONE` trong `bind()` để không bao giờ mất tiêu đề khi dùng chung `SharedRecycledViewPool`.
   - Thêm badge hiển thị trạng thái `isWatched` (icon tích xanh/đã xem góc trên bên phải poster).
2. **`LeanbackEpisodeViewHolder.kt`**:
   - Thêm badge hiển thị trạng thái `isWatched` trên ảnh thumbnail tập phim.
3. **`LeanbackBaseViewHolder.kt` & `LeanbackAvatarViewHolder.kt`**:
   - Tạo sẵn `placeholderBackgroundDrawable` tái sử dụng trong `LeanbackBaseViewHolder` (tự động chọn `GradientDrawable.OVAL` nếu `isCircle == true`), loại bỏ lỗi nền chữ nhật trên Avatar và không cấp phát object mới khi `unbind()`.
4. **`LeanbackNativeGrid.kt`**:
   - Quy đổi `horizontalSpacing` ra pixel và gọi `setHorizontalSpacing(horizontalSpacingPx)` + `setVerticalSpacing(verticalSpacingPx)` trong cả `factory` và `update`.

### Giai đoạn 3: Đồng bộ & Chuẩn hóa `LeanbackModernRow.kt` trên Home Screen (Critical / High)
1. **`LeanbackModernRow.kt`**:
   - Đồng bộ toàn bộ cơ chế bảo vệ Focus từ `ui/leanback/container/InteropHorizontalGridView.kt` sang `LeanbackModernRow.kt` (`requestFocus` ủy quyền xuống ViewHolder con, khóa biên `focusSearch`, chuyển giao focus trong `onFocusChanged` và `onChildViewHolderSelected`).
   - Thêm `root.translationZ = if (hasFocus) 12f else 0f` trong `updateFocusVisual` để hào quang và card đang focus luôn nổi lên trên các card lân cận.
   - Bổ sung `vh.updateFocusVisual(true)` trong `onScrollSettled` để bật lại glow ngay khi dừng cuộn nhanh.
   - Luôn tạo `titleText` trong `LeanbackCardViewHolder.create()` và ẩn/hiện theo `showLabels`.

### Giai đoạn 4: Tối ưu Hiệu năng 60fps tuyệt đối với `DiffUtil` & Async Color Extraction (Medium)
1. **`LeanbackUniversalAdapter.kt` & `LeanbackModernRow.kt`**:
   - Thay thế `notifyDataSetChanged()` bằng `DiffUtil.calculateDiff` so sánh theo `item.key` và nội dung item, hỗ trợ payload cập nhật nhanh (`progressPercent`, `isWatched`) mà không làm nháy ảnh hay mất focus.
2. **`LeanbackColorEngine.kt` & các ViewHolder**:
   - Thêm hàm trích xuất màu bất đồng bộ trên `Dispatchers.Default` và kiểm tra `currentItem?.key == item.key` trước khi áp dụng màu hào quang lên ViewHolder đang hiển thị.

---

## 5. TIÊU CHÍ NGHIỆM THU & TRẠNG THÁI (VERIFICATION)
- [x] **Giai đoạn 1 (Critical - Focus & D-Pad)**: Đã hoàn thành.
- [x] **Giai đoạn 2 (High - ViewHolder Recycling, Spacing & Badges)**: Đã hoàn thành.
- [x] **Giai đoạn 3 (Critical/High - Đồng bộ Home Screen `LeanbackModernRow.kt`)**: Đã hoàn thành.
- [x] **Giai đoạn 4 (Medium - Hiệu năng `DiffUtil` & Async Color Engine)**: Đã hoàn thành.
- [x] Chạy `./gradlew compileFullDebugKotlin` đạt **`BUILD SUCCESSFUL`** (Exit code `0`).

