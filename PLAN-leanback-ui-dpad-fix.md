# PLAN: Rà Soát & Khắc Phục Toàn Diện Lỗi Giao Diện & Điều Hướng D-Pad (Android Leanback Native)

## 📌 Bối cảnh & Vấn đề Cần Giải Quyết

Sau khi nâng cấp toàn bộ hệ thống hiển thị sang chuẩn **Android Leanback Native**, qua quá trình rà soát thực tế đã phát hiện 2 nhóm lỗi trải nghiệm người dùng lớn:

1. **Lỗi Điều Hướng D-Pad & Thanh Điều Hướng (Sidebar)**:
   - **Hiện tượng**: Khi người dùng nhấn D-Pad sang trái (`DPAD_LEFT`) ở nhiều mục hoặc màn hình (như Màn hình con, Chi tiết phim, Tìm kiếm, Lưới thư mục, Xem tất cả), thanh điều hướng Sidebar bị mở ra ngoài ý muốn, làm gián đoạn trải nghiệm xem.
   - **Nguyên nhân gốc rễ**: `MainActivity.kt` cung cấp `LocalOpenSidebar` toàn cục vô điều kiện cho mọi route trong `NuvioNavHost`. Các màn hình con (không phải Root Screen) cũng nhận được `openSidebar != null`. Đồng thời, các callback `onNavigateLeftOnFirstItem` và `onNavigateLeftOnFirstColumn` ở nhiều component tự động gọi `openSidebar()`.

2. **Lỗi Viền Sáng & Hiệu Ứng Focus (Glow / Focus Ring) Không Chuẩn Như Màn Home**:
   - **Hiện tượng**: Viền sáng ở các mục mới nâng cấp bị lỗi, bị cắt cụt (hard clipping), mờ nhạt hoặc không chuẩn nét như trên màn Home (`LeanbackModernRow`).
   - **Nguyên nhân gốc rễ**:
     - `LeanbackUniversalAdapter.kt` bị hardcode `borderWidthPx = 2` (raw 2px thay vì 2dp theo mật độ màn hình) và `glowMarginPx = 16` (raw 16px).
     - Trong khi đó, `glowSpreadPx` lại đặt là 28dp (tương đương 42-56px). Do `glowMarginPx < glowSpreadPx`, vùng vẽ Skia Gaussian Bloom bị cắt cụt ở biên 16px tạo thành vệt viền hộp chữ nhật sắc cạnh thay vì tỏa sáng êm dịu (atmospheric dispersion).
     - `LeanbackBaseViewHolder` không nâng `translationZ` khi thẻ nhận focus, dẫn đến việc thẻ bên phải/dưới đè lên viền sáng của thẻ đang focus.
     - Với thẻ Avatar tròn (`LeanbackAvatarViewHolder`), viền focus vẫn dùng `GradientDrawable` bo góc chữ nhật thay vì `GradientDrawable.OVAL`, khiến viền focus bị méo 4 góc.

---

## 🎯 Mục Tiêu Đạt Được

1. **Triệt tiêu 100% hiện tượng mở Sidebar ngoài ý muốn**:
   - Sidebar **CHỈ** được phép mở ở các màn hình Root (Home, LiveTV) khi người dùng chủ động nhấn sang trái từ cột đầu tiên.
   - Mọi màn hình con (`Detail`, `SeeAll`, `FolderDetail`, `CastDetail`, `Search Discover`, `Player`) **tuyệt đối không bao giờ** mở Sidebar khi nhấn sang trái.
2. **Chuẩn hóa Hiệu ứng Viền & Glow chuẩn 1:1 như Màn Home**:
   - Tái tạo chính xác thuật toán viền sáng của `LeanbackModernRow`: `glowSpreadDp = 18f`, `glowMarginPx = (glowSpreadPx * 1.35f).toInt()`.
   - `borderWidthPx` tính theo đơn vị `2.0 dp` thực tế trên màn hình TV (sắc nét, tinh tế).
   - `root.translationZ = 12f` khi focus để thẻ nổi lên trên, viền sáng tỏa đều 4 phía không bao giờ bị thẻ lân cận đè lên.
   - Avatar tròn có viền focus hình `OVAL` hoàn hảo.
3. **Bảo toàn 100% tính nguyên vẹn giao diện & độ mượt 60fps**.

---

## 🗺️ Dependency Chains (Chuỗi Phụ Thuộc)

```
[Phase 1: Sửa Cơ Chế Cấp Phát Sidebar & D-Pad Boundaries]
    │
    ├──> 1.1 Khóa LocalOpenSidebar ở các màn hình phụ trong MainActivity
    ├──> 1.2 Rà soát & chuẩn hóa onNavigateLeft trong LeanbackNativeRow & LeanbackNativeGrid
    └──> 1.3 Điều chỉnh logic biên D-Pad Left trong InteropHorizontalGridView & InteropVerticalGridView
    │
[Phase 2: Chuẩn Hóa Lõi Viền Sáng & Hiệu Ứng Focus (Leanback Core Engine)]
    │
    ├──> 2.1 Chuẩn hóa glowSpreadDp (18f) và tính động glowMarginPx (spread * 1.35f)
    ├──> 2.2 Đưa borderWidthPx về 2dp chuẩn displayMetrics
    ├──> 2.3 Thêm root.translationZ = 12f khi focus để triệt tiêu overdraw đè viền
    └──> 2.4 Cập nhật focusBorderDrawable hình OVAL cho LeanbackAvatarViewHolder
    │
[Phase 3: Rà Soát Toàn Bộ Các Màn Hình Đã Nâng Cấp]
    │
    ├──> 3.1 SearchScreen & SearchDiscoverSection (xử lý điều hướng sang trái về bộ lọc/bàn phím)
    ├──> 3.2 CatalogRowSection & ClassicHomeContent
    ├──> 3.3 FolderDetailScreen & CatalogSeeAllScreen
    └──> 3.4 EpisodesSection, CastSection, MoreLikeThisSection, TrailerSection, CollectionSection
    │
[Phase 4: Kiểm Thử Tự Động & Xác Minh Toàn Diện]
    ├──> 4.1 Biên dịch Gradle compileFullDebugKotlin
    └──> 4.2 Đóng gói APK assembleFullDebug & Nghiệm thu
```

---

## 📋 Chi Tiết Từng Giai Đoạn Triển Khai (Phase-by-Phase Breakdown)

### 🟡 Phase 1: Khắc Phục Triệt Để Lỗi Mở Sidebar Trái Ngoài Ý Muốn

#### 1.1. Khóa `LocalOpenSidebar` trong `MainActivity.kt`
- **Vấn đề**: `LocalOpenSidebar` được cấp phát toàn cục cho toàn bộ `NuvioNavHost`, khiến các màn hình con nhận được callback mở sidebar.
- **Giải pháp**:
  ```kotlin
  // MainActivity.kt - ModernSidebarScaffold
  val showSidebar = currentRoute in rootRoutes
  
  CompositionLocalProvider(
      LocalSidebarExpanded provides isSidebarExpanded,
      LocalContentFocusRequester provides contentFocusRequester,
      LocalOpenSidebar provides if (showSidebar) {
          {
              isSidebarExpanded = true
              sidebarCollapsePending = false
          }
      } else null // Màn hình con nhận null -> không bao giờ mở sidebar
  )
  ```

#### 1.2. Chuẩn hóa `onNavigateLeft` trên các Hàng & Lưới
- **`SearchDiscoverSection.kt`**: Xóa bỏ `openSidebar()` khi nhấn trái ở cột 0. Nhấn trái ở cột 0 sẽ giữ focus hoặc chuyển mượt về Filter Pickers / Search Controls phía trên/trái.
- **`FolderDetailScreen.kt`**: Không truyền callback mở sidebar, nhấn trái ở cột 0 sẽ dừng cuộn (`stopScroll()`) hoặc điều hướng trong màn hình.
- **`CatalogRowSection.kt`**: Chỉ cho phép mở sidebar khi `isRootHomeScreen == true` và người dùng cố tình nhấn sang trái từ phần tử đầu tiên (đã có debounce 300ms).

#### 1.3. Cải tiến D-Pad Boundary trong `InteropHorizontalGridView` & `InteropVerticalGridView`
- Đảm bảo khi `onNavigateLeftOnFirstItem` là `null` hoặc trả về `false`, view sẽ kiểm tra xem phía bên trái có phần tử nào trong View Hierarchy hay không (`focusSearch(FOCUS_LEFT)`). Nếu không có, nó sẽ dừng cuộn êm ái mà không bị giật hoặc kích hoạt sự kiện ngoài ý muốn.

---

### 🟡 Phase 2: Chuẩn Hóa Lõi Viền Sáng (Glow & Border) Chuẩn 1:1 Như Màn Home

#### 2.1. Cân đối Tỷ lệ Glow Spread & Margin (`LeanbackUniversalAdapter` & `ViewHolders`)
- Trong `LeanbackUniversalAdapter.kt`:
  - Thay vì giá trị hardcode `borderWidthPx = 2` và `glowMarginPx = 16`, tính toán động theo displayMetrics:
    ```kotlin
    val dp = parent.context.resources.displayMetrics.density
    val glowSpreadPx = 18f * dp
    val glowMarginPx = (glowSpreadPx * 1.35f).toInt()
    val borderWidthPx = (2f * dp).toInt().coerceAtLeast(2)
    ```
- Trong `LeanbackPosterViewHolder.kt` & `LeanbackEpisodeViewHolder.kt`:
  - Đổi `glowSpreadPx = 18f * dp` (thay vì 28f * dp gây quá khổ và cắt cụt).
  - Đảm bảo `glowView.layoutParams` luôn rộng hơn card một khoảng `glowMarginPx * 2` để ánh sáng Skia Gaussian Bloom tan biến mượt mà (fade to 0 alpha) trước khi chạm viền View.

#### 2.2. Xử lý Trật tự Lớp Vẽ (Z-Order Elevation) Khi Thẻ Nhận Focus
- Trong `LeanbackBaseViewHolder.kt`:
  - Khi thẻ nhận focus (`hasFocus == true`):
    ```kotlin
    root.translationZ = 12f
    ```
  - Khi thẻ mất focus (`hasFocus == false`):
    ```kotlin
    root.translationZ = 0f
    ```
  - **Tác dụng**: Thẻ được phóng to (scale 1.08f) cùng vầng hào quang viền sáng sẽ luôn nổi lên trên cùng (Top Layer), ngăn chặn hoàn toàn việc các thẻ bên cạnh vẽ đè lên viền sáng.

#### 2.3. Sửa Lỗi Viền Focus Của Thẻ Avatar Tròn
- Trong `LeanbackAvatarViewHolder.kt`:
  - Cập nhật `focusBorderDrawable` dùng `GradientDrawable.OVAL`.
  - Giữ viền sáng tròn ôm sát ảnh đại diện của Diễn viên / Đạo diễn trong `CastSection`.

---

### 🟡 Phase 3: Rà Soát Giao Diện Toàn Diện Trên Mọi Màn Hình

1. **Màn hình Chi tiết (`MetaDetailsScreen`)**:
   - `EpisodesSection`: Kiểm tra thumbnail 16:9, viền sáng tập đang chọn, marquee tiêu đề, thanh tiến trình xem tiếp.
   - `CastSection`: Kiểm tra avatar tròn, viền focus OVAL, glow đồng màu.
   - `MoreLikeThisSection`, `TrailerSection`, `CollectionSection`: Kiểm tra scale 1.08f, viền sáng cyan / màu chủ đạo.
2. **Màn hình Tìm Kiếm (`SearchScreen` & `SearchDiscoverSection`)**:
   - Kiểm tra di chuyển từ ô tìm kiếm sang danh sách kết quả và ngược lại.
   - Đảm bảo nhấn trái trong lưới Discover không kích hoạt Sidebar, nhấn Back quay lại bộ lọc mượt mà.
3. **Màn hình Bộ sưu tập & Thư mục (`FolderDetailScreen`)**:
   - Kiểm tra lưới poster trong thư mục, viền sáng đồng nhất với Home, chuyển tab mượt.
4. **Màn hình Xem Tất Cả (`CatalogSeeAllScreen`)**:
   - Kiểm tra cuộn dọc 2D, viền sáng thẻ ở mọi cột.

---

## 🧪 Kế Hoạch Xác Minh & Kiểm Thử (Verification Plan)

### 1. Kiểm thử Tự động (Automated Verification)
- Chạy biên dịch toàn bộ mã nguồn Kotlin:
  ```powershell
  ./gradlew compileFullDebugKotlin
  ```
  *Yêu cầu*: 0 lỗi, cảnh báo tối thiểu.
- Chạy đóng gói APK:
  ```powershell
  ./gradlew assembleFullDebug
  ```
  *Yêu cầu*: Build SUCCESSFUL, sinh file APK hoàn chỉnh.

### 2. Kiểm thử Điều Hướng D-Pad & Trải Nghiệm Thủ Công (Manual Checklist)
- [ ] **Màn hình Chi tiết Phim**:
  - Chọn tập phim đầu tiên (EP 1) -> Nhấn D-Pad Trái -> Xác nhận: **Không mở Sidebar**, focus giữ nguyên ở EP 1.
  - Chọn diễn viên đầu tiên -> Nhấn D-Pad Trái -> Xác nhận: **Không mở Sidebar**.
  - Kiểm tra viền focus avatar diễn viên: Hình tròn `OVAL` hoàn hảo, không có góc chữ nhật.
- [ ] **Màn hình Tìm Kiếm & Khám Phá (Discover)**:
  - Chọn thẻ đầu tiên cột 0 trong lưới Discover -> Nhấn D-Pad Trái -> Xác nhận: **Không mở Sidebar**.
- [ ] **Màn hình Thư mục (Folder Detail)**:
  - Chọn thẻ bất kỳ ở cột 0 -> Nhấn D-Pad Trái -> Xác nhận: **Không mở Sidebar**.
- [ ] **Chất lượng Viền Sáng (Glow & Focus Ring)**:
  - Viền sáng thẻ có độ dày chuẩn 2dp, nét căng, màu sắc sống động (Electric Cyan / Dynamic Hue).
  - Không có hiện tượng viền sáng bị cắt cụt dạng hộp (no hard clipping).
  - Thẻ đang focus nổi lên trên (elevation), không bị các thẻ lân cận đè lên.

---

> [!WARNING]
> Không được can thiệp vào kích thước danh sách, padding tổng thể của màn hình hoặc thay đổi logic nghiệp vụ data fetching. Mọi sửa đổi phải hoàn toàn thuộc về tầng tương tác View/Focus và rendering.

