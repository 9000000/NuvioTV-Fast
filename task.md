# Danh Sách Task - Tối Ưu Cơ Chế Tải Catalog & Prefetch Poster Màn Hình Home

> **Kế hoạch tương ứng**: [PLAN-poster-loading-optimization.md](file:///f:/MyGithub/NuvioTV-Fast/PLAN-poster-loading-optimization.md)

---

## 📋 Task Breakdown

### Phase 1: Tầng Catalog & Sửa Bug Giam Hàng Lazy
- [x] **TASK-01**: Tạo cấu hình phân hạng phần cứng `HomeImageLoadingProfile` theo RAM thiết bị
  - **Vai trò**: Core / Performance Engineer
  - **Mô tả**: Định nghĩa `HomeImageLoadingProfile` (Low-RAM `<= 2.3GB` vs Standard `> 2.3GB`) chứa toàn bộ hằng số không magic-number cho `okHttpMaxRequests`, `okHttpMaxRequestsPerHost`, `connectionPoolMaxIdle`, `bitmapDecodeParallelism`, `catalogConcurrency`, `eagerCatalogRows`, `dripAheadRows`, `verticalPrefetchRows`, `verticalPrefetchItems`, `horizontalPrefetchAhead`, `horizontalPrefetchBehind`.
  - **File**: [HomePosterLoading.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/core/image/HomePosterLoading.kt)
  - **Trạng thái**: Completed (`fe327fb1e`)

- [x] **TASK-02**: Sửa bug giam hàng lazy, nâng concurrency và bổ sung Background Drip-Load cho Catalog
  - **Vai trò**: ViewModel / Data Pipeline Engineer
  - **Mô tả**:
    1. Sửa `loadCatalogPipeline` để mỗi hàng lazy tải xong đều flush UI ngay (không chờ `pendingCatalogLoads == 0`).
    2. Điều chỉnh `catalogLoadSemaphore`, `eagerCatalogLoadCount`, và rút ngắn debounce sau lần render đầu ở `scheduleUpdateCatalogRows` & `observeModernHomePresentationPipeline`.
    3. Thêm `scheduleBackgroundDripLazyCatalogs()` tự động nạp cuốn chiếu các hàng lazy tiếp theo sau khi hàng eager hoàn tất (có giới hạn cửa sổ + kiểm tra `OpenFilesGuard`).
    4. Cache in-memory `customPosterUrlPattern` & `customPosterEnabledScreens` trong `CatalogRepositoryImpl` thay vì gọi `.first()` mỗi catalog.
  - **File**: [HomeViewModel.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/HomeViewModel.kt), [HomeViewModelCatalogPipeline.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/HomeViewModelCatalogPipeline.kt), [HomeViewModelPresentationPipeline.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/HomeViewModelPresentationPipeline.kt), [CatalogRepositoryImpl.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/data/repository/CatalogRepositoryImpl.kt)
  - **Trạng thái**: Completed (`4bcd3a384`)

### Phase 2: Tầng Kích Hoạt Lazy Catalog Ở UI
- [x] **TASK-03**: Thay `collectLatest + delay(240)` bằng Throttle chủ động & mở rộng cửa sổ nhìn trước Lazy Catalog
  - **Vai trò**: UI / TV Navigation Engineer
  - **Mô tả**:
    1. Trong `ModernHomeRowsList.kt`: nâng `prefetchAheadForLazy` lên theo profile (`3–4` hàng), thay `collectLatest + delay(240)` bằng cơ chế throttle không bị hủy liên tục khi cuộn nhanh, đồng thời gọi kích hoạt sớm trong `stepVerticalRow` / hold-scroll.
    2. Trong `ClassicHomeContent.kt`: nâng `prefetchAhead` lên `2–3` hàng và áp dụng cơ chế throttle tương tự.
  - **File**: [ModernHomeRowsList.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeRowsList.kt), [ClassicHomeContent.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/ClassicHomeContent.kt)
  - **Trạng thái**: Completed (`2b9d6c81e`)

### Phase 3: Tầng Prefetch Poster & Lọc Rác Placeholder
- [x] **TASK-04**: Chặn hoàn toàn URL `placeholder://empty` đi vào Coil Pipeline
  - **Vai trò**: Image / Leanback Engineer
  - **Mô tả**: Kiểm tra `isPlaceholder()` ngay đầu `LeanbackArtworkLoader.loadIntoImageView`, `LeanbackArtworkLoader.prefetch`, `LeanbackArtworkLoader.resolveNormalizedFallbackUrl`, `LeanbackCardViewHolder.bind` và `LeanbackPosterViewHolder.bind` để loại bỏ request rác vào Coil.
  - **File**: [LeanbackArtworkLoader.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/core/LeanbackArtworkLoader.kt), [LeanbackModernRowAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRowAdapter.kt), [LeanbackPosterViewHolder.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/holder/LeanbackPosterViewHolder.kt)
  - **Trạng thái**: Completed (`1101a6d0f`)

- [x] **TASK-05**: Sửa lỗi "chết đứng" & mở rộng Vertical Poster Prefetch (Modern + Classic)
  - **Vai trò**: UI / Image Prefetch Engineer
  - **Mô tả**:
    1. Trong `ModernHomeRowsList.kt`: theo dõi cả `lastVisibleRowIndex` lẫn trạng thái dữ liệu của các hàng kế tiếp để tự prefetch ngay khi hàng lazy/eager vừa load xong JSON; nâng phạm vi lên `2–3` hàng × `6–8` poster tính từ `focusedItemByRow[row.key] ?: 0`.
    2. Trong `ClassicHomeContent.kt`: bổ sung Vertical Poster Prefetch sử dụng chung `LeanbackArtworkLoader.prefetch` với kích thước card chuẩn của Classic Home.
  - **File**: [ModernHomeRowsList.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeRowsList.kt), [ClassicHomeContent.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/ClassicHomeContent.kt)
  - **Trạng thái**: Completed (`2b9d6c81e`)

- [x] **TASK-06**: Sửa điểm neo Horizontal Poster Prefetch theo mép phải hiển thị
  - **Vai trò**: Leanback Engineer
  - **Mô tả**: Trong `LeanbackRowAdapter` và `LeanbackUniversalAdapter`, tính điểm neo từ `lastVisibleAdapterPosition` (hoặc vị trí bound xa nhất về phía phải) thay vì `focusedPosition`, tải trước `4–6` poster ngoài màn hình về phía phải (và `2` poster khi cuộn ngược trái) theo `HomeImageLoadingProfile` + kích hoạt prefetch ban đầu khi hàng active nhận dữ liệu.
  - **File**: [LeanbackModernRowAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRowAdapter.kt), [LeanbackUniversalAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/adapter/LeanbackUniversalAdapter.kt)
  - **Trạng thái**: Completed (`1101a6d0f`, `f61e18e12`)

### Phase 4: Mạng OkHttp, Decode Parallelism, Fallback & Color Engine
- [x] **TASK-07**: Áp dụng Tiered OkHttp/Decode config, Negative Cache cho Custom Poster Fallback và tối ưu thời điểm trích màu
  - **Vai trò**: Core / Performance Engineer
  - **Mô tả**:
    1. Kết nối `HomeImageLoadingProfile` vào `NuvioApplication.newImageLoader()` (`Dispatcher`, `ConnectionPool`, `bitmapFactoryMaxParallelism`).
    2. Thêm negative cache TTL ngắn hạn trong `CustomPosterFallbackInterceptor` cho custom poster URL lỗi để fallback ngay sang `rawPosterUrl`.
    3. Giới hạn `LeanbackColorEngine.extractDominantColorAsync` chỉ chạy tự động cho card đang focus hoặc vị trí đầu hàng; các card khác trích on-demand khi focus.
  - **File**: [NuvioApplication.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/NuvioApplication.kt), [CustomPosterFallbackInterceptor.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/core/image/CustomPosterFallbackInterceptor.kt), [LeanbackModernRowAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRowAdapter.kt), [LeanbackPosterViewHolder.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/holder/LeanbackPosterViewHolder.kt)
  - **Trạng thái**: Completed (`fe327fb1e`, `1101a6d0f`)

### Phase 5: Kiểm Chứng & Nghiệm Thu
- [x] **TASK-08**: Viết Unit Tests & Chạy Kiểm Thử Xác Minh (Automated Verified; ADB Profiling sẵn sàng đo trên TV thật)
  - **Vai trò**: QA / Verification Engineer
  - **Mô tả**: Bổ sung unit test cho `HomePosterLoading`, `CustomPosterFallbackInterceptor`, `LeanbackArtworkLoader`, `HomeViewModelCatalogPipeline` (lazy flush + drip-load) và `HomePrefetchTriggerTest`; chạy `:app:compileFullDebugKotlin` & `:app:testFullDebugUnitTest` (135/136 passed, 1 lỗi pre-existing trên HEAD ở `HomeEnrichmentRetryTest.kt:115`).
  - **File**: [HomePosterLoadingTest.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/test/java/com/nuvio/tv/core/image/HomePosterLoadingTest.kt), [CustomPosterFallbackInterceptorTest.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/test/java/com/nuvio/tv/core/image/CustomPosterFallbackInterceptorTest.kt), [LeanbackCoreOptimizationTest.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/test/java/com/nuvio/tv/ui/leanback/LeanbackCoreOptimizationTest.kt), [HomeCatalogLazyPipelineTest.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/test/java/com/nuvio/tv/ui/screens/home/HomeCatalogLazyPipelineTest.kt), [HomePrefetchTriggerTest.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/test/java/com/nuvio/tv/ui/screens/home/HomePrefetchTriggerTest.kt)
  - **Trạng thái**: Automated Tests Verified (`d22fee2ca`) | Manual ADB Profiling on TV Box pending device run


---

# Danh Sách Task - Sidebar bo tròn + Blur như bản gốc

> **Kế hoạch tương ứng**: [PLAN-sidebar-rounded-blur.md](file:///f:/MyGithub/NuvioTV-Fast/PLAN-sidebar-rounded-blur.md)

- [x] **SIDEBAR-01**: `NativeSidebarView` chế độ floating dùng nền trong suốt + `outlineProvider` bo tròn (`panelCornerRadiusPx`) + `clipToOutline`
  - **Vai trò**: Android View / UI Engineer
  - **File**: [NativeSidebarView.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/components/NativeSidebarView.kt)
- [x] **SIDEBAR-02**: Thêm cờ `sidebarTranslucent = modernSidebarBlurEnabled || SDK_INT < S`; bọc `AndroidView` trong Box Compose: `clip(RoundedCornerShape(panelRadius))` → `hazeEffect` (chỉ khi blur, 26dp, noise 0.04, delayed progress) → tint `#161618` (0.65 khi translucent / 0.97 / AMOLED 1.0) → border AMOLED
  - **Vai trò**: Compose / UI Engineer
  - **File**: [MainActivity.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/MainActivity.kt) (`ModernSidebarScaffold`, ≈ L1787-L1887)
- [x] **SIDEBAR-03**: `CollapsedSidebarPill` thêm tham số `translucent` → alpha 0.65/0.96 theo `sidebarTranslucent` (blur vẫn theo `blurEnabled`)
  - **Vai trò**: Compose / UI Engineer
  - **File**: [MainActivity.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/MainActivity.kt) (`CollapsedSidebarPill`, ≈ L1923)
- [x] **SIDEBAR-04**: Build `compileFullDebugKotlin` + kiểm thử thủ công 10 kịch bản trong plan (không thêm hiệu ứng scale)
  - **Vai trò**: QA
