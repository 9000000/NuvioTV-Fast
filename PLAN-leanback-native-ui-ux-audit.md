# KẾ HOẠCH TOÀN DIỆN: RÀ SOÁT, SỬA LỖI & TỐI ƯU GIAO DIỆN UX/UI ANDROID LEANBACK NATIVE (TV 60FPS)

> **Mục tiêu**: Đảm bảo toàn bộ hệ thống giao diện Android Leanback Native (HorizontalGridView, VerticalGridView, NativeSidebarView, Modern/Classic Row, Grid See-All, Detail Sections) đạt chuẩn **60fps mượt mà**, loại bỏ hoàn toàn hiện tượng **mất focus/focus trap**, tối ưu bộ nhớ cache màu và thống nhất trải nghiệm điều hướng D-Pad TV.

---

## 📌 I. HIỆN TRẠNG & CÁC VẤN ĐỀ PHÁT HIỆN QUA AUDIT

Qua quá trình rà soát mã nguồn chuyên sâu trên toàn bộ codebase `NuvioTV-Fast`, đã phát hiện **5 nhóm vấn đề chính** (xếp theo mức độ nghiêm trọng):

### 🚨 Nhóm 1: Lỗi Focus & Điều Hướng D-Pad (Critical)
1. **Lệch pha hành vi mở Sidebar qua D-Pad Left giữa Modern Home và Classic/Detail**:
   - Trong [`InteropHorizontalGridView.kt`](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/container/InteropHorizontalGridView.kt#L330-L345): Người dùng phải bấm D-Pad Left **2 lần** trong vòng 700ms (`(now - lastEdgeLeftPressTime) < 700L`) mới kích hoạt `onNavigateLeftOnFirstItem` mở sidebar.
   - Trong khi đó tại [`LeanbackModernRow.kt`](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt#L800-L835): Người dùng chỉ cần nhả phím và bấm Left 1 lần dứt khoát khi đang ở item 0 là mở Sidebar. Sự bất nhất này khiến người dùng TV cảm giác bấm lúc ăn lúc không.
2. **Nguy cơ giật lệch Keyline Scroll khi thả phím D-Pad**:
   - [`InteropHorizontalGridView.kt`](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/container/InteropHorizontalGridView.kt#L274-L283) và [`InteropVerticalGridView.kt`](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/container/InteropVerticalGridView.kt#L275-L284) gọi `stopScroll()` ngay khi bắt sự kiện `ACTION_UP` của phím D-Pad.
   - Việc `stopScroll()` đột ngột ngắt ngang thuật toán smooth snap-to-alignment tự nhiên của Leanback, khiến các thẻ dừng lửng lơ giữa màn hình, không bám đúng đường căn chuẩn (keyline alignment).
3. **Thiếu Focus-Guard khi nạp Data / Submit List**:
   - `LeanbackUniversalAdapter` chưa có cơ chế bảo vệ focus khi chuyển đổi từ placeholder items sang dữ liệu thật (trong khi `LeanbackRowAdapter` trong `LeanbackModernRow.kt` đã có đoạn xử lý giữ focus an toàn ở lines 1629–1660). Khi API trả về dữ liệu chậm, item bị thay thế có thể làm focus nhảy bất thường hoặc rơi khỏi hàng.
4. **Cơ chế nhảy Focus ở `NativeSidebarView.kt`**:
   - Trong [`NativeSidebarView.kt`](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/components/NativeSidebarView.kt#L106-L125), `onAttachedToWindow()` tự động bắn 3 lần request focus (`0ms`, `45ms`, `100ms`) nếu `visibility == VISIBLE`. Điều này có nguy cơ cướp mất focus của nội dung chính (Home content) khi màn hình vừa khởi tạo.

---

### ⚡ Nhóm 2: Hiệu Năng & Giật Lag 60fps (High)
1. **`LocalLeanbackRecycledViewPool` hoàn toàn bị bỏ quên (Chưa từng được Provide)**:
   - File [`LeanbackPoolManager.kt`](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/pool/LeanbackPoolManager.kt#L26) định nghĩa `LocalLeanbackRecycledViewPool = staticCompositionLocalOf { null }`.
   - Kết quả quét toàn bộ repo cho thấy: **Không có bất kỳ màn hình nào** gọi `CompositionLocalProvider(LocalLeanbackRecycledViewPool provides ...)`.
   - Hậu quả: Toàn bộ hàng loạt `LeanbackNativeRow` và `LeanbackNativeGrid` trên app đều dùng `effectivePool == null`. Mỗi row tự khởi tạo một RecyclerView pool riêng; khi người dùng cuộn dọc qua 10-20 catalog rows, hàng trăm ViewHolder bị `inflate` liên tục từ đầu thay vì tái sử dụng, gây ra hiện tượng sụt khung hình (jank/dropped frames).
2. **Lãng phí Software Layer & Overdraw từ `PlayStoreGlowView`**:
   - Trong `LeanbackBaseViewHolder` ([`LeanbackPosterViewHolder.kt`](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/holder/LeanbackPosterViewHolder.kt#L70), [`LeanbackEpisodeViewHolder.kt`](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/holder/LeanbackEpisodeViewHolder.kt#L75), [`LeanbackAvatarViewHolder.kt`](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/holder/LeanbackAvatarViewHolder.kt#L55)), mỗi thẻ đều tạo một instance `PlayStoreGlowView` với `setLayerType(LAYER_TYPE_SOFTWARE, null)`.
   - Tại [`LeanbackModernRow.kt`](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt#L1694-L1707), thẻ vừa tạo `PlayStoreGlowView` trong cây view, nhưng adapter lại luôn gán `glowView.visibility = View.GONE` vì Modern Home dùng vòng viền ngoài `ModernRowFloatingFocusRingView`. Đây là View rác chiếm bộ nhớ và phân bổ bitmap software không cần thiết.
3. **Trùng lặp và phân mảnh Cache màu Dominant Color**:
   - `LeanbackColorEngine` trong `ui.leanback.core` lưu cache màu theo `item.key`. Nhưng ở nhiều màn hình (như [`CatalogSeeAllScreen.kt`](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/CatalogSeeAllScreen.kt#L205)), `key` chứa index (`"${catalogRow.stableKey()}_$index"`). Do đó nếu cùng một bộ phim xuất hiện ở nhiều hàng khác nhau, key khác nhau sẽ dẫn đến cache miss, buộc app phải decode lại bitmap và phân tích histogram màu liên tục trên background thread.
   - Trong khi đó, `LeanbackModernRow.kt` lại tự tạo thêm một `LruCache<String, Int>(256)` riêng biệt.
4. **Nhảy chiều cao hàng (Layout Thrashing) do tính toán Subtitle động**:
   - Tại [`LeanbackNativeRow.kt`](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/LeanbackNativeRow.kt#L84-L98): `totalRowHeightDp` phụ thuộc vào `hasSubtitles` của list items hiện tại (`26.dp` vs `44.dp`). Khi danh sách cập nhật từ rỗng/placeholder sang có subtitle, toàn bộ chiều cao của `AndroidView` thay đổi đột ngột làm Compose recompose và layout lại toàn bộ danh sách cha.

---

### 🎨 Nhóm 3: Tính Nhất Quán UX/UI & Thẩm Mỹ (Medium)
1. **Màu sắc Sidebar bị Hardcode**:
   - [`NativeSidebarView.kt`](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/components/NativeSidebarView.kt#L37) hardcode `accentOrange = Color.parseColor("#E65100")` thay vì lấy màu Accent từ `NuvioTheme` / `AppTheme` / theme tùy chỉnh của người dùng.
2. **`GridHomeContent.kt` vẫn dùng Compose `LazyVerticalGrid` thay vì `LeanbackNativeGrid`**:
   - [`GridHomeContent.kt`](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/GridHomeContent.kt#L440) đang dùng `LazyVerticalGrid` nặng nề của Compose. Khi có hàng trăm phim, người dùng dùng layout dạng lưới sẽ gặp hiện tượng giật lag, khác biệt rõ rệt về độ mượt so với `CatalogSeeAllScreen` (đã dùng `LeanbackNativeGrid`).
3. **Độ nảy và hiệu ứng Focus Scale chưa đồng bộ**:
   - Thẻ Poster ở `LeanbackPosterViewHolder` dùng scale `1.08f` kèm gradient glow, trong khi `LeanbackModernRow` dùng scale `1.10f` kèm floating focus ring với animation physics riêng biệt. Cần thống nhất quy chuẩn hoặc cung cấp cấu hình mượt mà xuyên suốt app.

---

## 🛠️ II. KẾ HOẠCH TRIỂN KHAI CHI TIẾT (ACTION PLAN THEO TỪNG GIAI ĐOẠN)

### 🔹 Giai đoạn 1: Chuẩn Hóa Focus, D-Pad & Loại Bỏ Hoàn Toàn Focus Traps
- **Nhiệm vụ 1.1**: Chuẩn hóa logic D-Pad Left mở Sidebar:
  - Cập nhật [`InteropHorizontalGridView.kt`](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/container/InteropHorizontalGridView.kt) theo cơ chế nhả phím an toàn (`hasReleasedKeyAfterArrivingAt0` & debounce hợp lý) tương tự `LeanbackModernRow`, cho phép 1 lần bấm dứt khoát là mở Sidebar mượt mà.
- **Nhiệm vụ 1.2**: Sửa lỗi `stopScroll()` trên `ACTION_UP`:
  - Trong cả `InteropHorizontalGridView` và `InteropVerticalGridView`, loại bỏ lời gọi `stopScroll()` làm đứt đoạn keyline alignment. Để Leanback tự do settle vào item đích theo đúng chuẩn TV.
- **Nhiệm vụ 1.3**: Tích hợp Focus Guard khi nạp danh sách vào `LeanbackUniversalAdapter`:
  - Chuyển `updateConfig` từ `safeNotifyDataSetChanged()` sang `notifyItemRangeChanged()` để tránh hủy focus đột ngột.
  - Khi `submitItems` lúc RecyclerView đang cuộn dở (`scrollState != SCROLL_STATE_IDLE`), hoãn việc cập nhật vào `pendingItems` và chỉ apply khi cuộn dừng (`onScrollSettled`).
- **Nhiệm vụ 1.4**: Tinh chỉnh Focus Request tại `NativeSidebarView.kt`:
  - Ngăn việc tự động chiếm focus khi sidebar chỉ vừa hiển thị dạng collapsed hoặc overlay chưa active.

---

### 🔹 Giai đoạn 2: Tối Ưu Hiệu Năng 60fps & Quản Lý Bộ Nhớ
- **Nhiệm vụ 2.1**: Kích hoạt `LocalLeanbackRecycledViewPool` toàn cục:
  - Cung cấp `rememberSharedLeanbackPool(maxPerType = 30)` tại gốc các màn hình lớn (`MainActivity.kt`, `NuvioNavHost.kt`, `ClassicHomeContent.kt`, `CatalogSeeAllScreen.kt`).
  - Chia tách ViewType theo kích thước để tránh layout thrashing:
    - `VIEW_TYPE_POSTER_PORTRAIT`
    - `VIEW_TYPE_POSTER_LANDSCAPE`
    - `VIEW_TYPE_EPISODE`
    - `VIEW_TYPE_AVATAR`
- **Nhiệm vụ 2.2**: Dọn dẹp View lãng phí & Software Layer:
  - Trong `LeanbackModernRow.kt`, loại bỏ hoàn toàn việc tạo `PlayStoreGlowView` thừa trong `cardContainer` khi đang dùng `ModernRowFloatingFocusRingView`.
  - Giữ layer type phần cứng mặc định cho thẻ, chỉ kích hoạt software layer trên thẻ thực sự được focus nếu cần shadow phức tạp.
- **Nhiệm vụ 2.3**: Đồng nhất Color Extraction Engine:
  - Chuẩn hóa key lưu cache màu dựa trên `imageUrl` thay vì `item.key` để tái sử dụng kết quả trích xuất màu trên mọi hàng và màn hình.
  - Đồng bộ `LeanbackColorEngine` dùng chung giữa `ui.leanback` và `LeanbackModernRow`.
- **Nhiệm vụ 2.4**: Cố định kích thước hàng tại `LeanbackNativeRow.kt`:
  - Tránh việc thay đổi `totalRowHeightDp` bất ngờ khi items nạp subtitle; cấp slot nhãn cố định khi `showLabels == true`.

---

### 🔹 Giai đoạn 3: Đồng Bộ Trải Nghiệm Giao Diện UX/UI
- **Nhiệm vụ 3.1**: Đồng bộ Theme Color cho `NativeSidebarView`:
  - Cho phép truyền màu Accent từ `NuvioTheme` vào `NativeSidebarView`, đảm bảo sidebar đổi màu đồng bộ khi người dùng đổi chủ đề giao diện trong Settings.
- **Nhiệm vụ 3.2**: Hiện đại hóa `GridHomeContent.kt`:
  - Thay thế `LazyVerticalGrid` bằng `LeanbackNativeGrid` tương tự như `CatalogSeeAllScreen`, giúp chế độ Grid Home đạt chuẩn 60fps trên Android TV Box cấu hình yếu.
- **Nhiệm vụ 3.3**: Kiểm tra và hoàn thiện các màn hình Detail/Browse:
  - Rà soát các component: `CastDetailScreen`, `FolderDetailScreen`, `SearchDiscoverSection`, `CastSection`, `CollectionSection`, `EpisodesSection`, `MoreLikeThisSection`, `TrailerSection`, đảm bảo không còn lỗi padding, clip to padding hoặc thiếu callback click/long-click.

---

## 🧪 III. KẾ HOẠCH KIỂM THỬ & NGHIỆM THU (VERIFICATION STRATEGY)

| Hạng mục kiểm thử | Kịch bản / Thao tác thực hiện | Tiêu chí đạt chuẩn |
| :--- | :--- | :--- |
| **1. Focus Edge & Sidebar** | Từ item đầu tiên của bất kỳ hàng nào (Modern Home, Classic Home, Detail), nhấn phím D-Pad Left | Mở Native Sidebar mượt mà, không bị khựng, không cần bấm đúp gấp |
| **2. Fast D-Pad Scroll** | Giữ lì phím D-Pad Right/Down qua 30-50 thẻ liên tục | Thẻ trượt mượt mà, khi thả phím thẻ luôn dừng đúng tâm keyline, không lệch lửng lơ |
| **3. RecycledViewPool** | Cuộn dọc nhanh qua toàn bộ các hàng catalog trên Classic Home | Không phát sinh drop frames (Jank < 2%), ViewHolders được tái sử dụng liên tục |
| **4. Color Cache Hit** | Mở một bộ phim xuất hiện ở cả "Trending" và "See All" | Màu sắc phát sáng/vòng viền hiển thị tức thì, không phân tích lại histogram |
| **5. Data Refresh** | Thay đổi bộ lọc hoặc nạp thêm dữ liệu khi danh sách đang hiển thị | Không bị mất focus, thẻ được cập nhật nội dung êm ái không nhấp nháy trắng |
| **6. Theme Switch** | Chuyển đổi Theme màu sắc trong Settings | `NativeSidebarView` đổi màu sắc Accent ngay lập tức |

---

## 🎯 IV. LỘ TRÌNH ĐỀ XUẤT THỰC THI (NEXT STEPS)

- **Bước 1**: Xác nhận bản kế hoạch với người dùng.
- **Bước 2**: Thực thi **Giai đoạn 1** (Sửa các lỗi Focus, D-Pad và Focus Guard quan trọng nhất).
- **Bước 3**: Thực thi **Giai đoạn 2** (Kích hoạt RecycledViewPool toàn cục, dọn dẹp Software Layer & thống nhất Cache màu).
- **Bước 4**: Thực thi **Giai đoạn 3** (Cập nhật `NativeSidebarView` theme & chuyển đổi `GridHomeContent` sang Native Grid).
- **Bước 5**: Kiểm tra build, lint và xác nhận tính toàn vẹn của ứng dụng.

