# PLAN-sync-dev-2026-11-19: Kế hoạch Đồng bộ Toàn bộ Commit Mới từ Nhánh `dev` (`origin/dev`) sang `tet2`

> [!IMPORTANT]
> **Mục tiêu cốt lõi:** Cập nhật toàn bộ **40 commits mới nhất** (từ `2026-11-17 02:32:00` đến `2026-11-19`) từ nhánh `origin/dev` (`15286b68c`, trùng khớp 100% với `upstream/dev` trên GitHub) vào nhánh hiện tại `tet2` (`18ce7f446`), **xử lý toàn diện 15 tệp xung đột trực tiếp và các thay đổi trong 93 tệp auto-merged**, **giữ nguyên 100% toàn bộ các tùy chỉnh đã thêm trên `tet2`**, đồng thời **tiếp nhận các cải tiến vượt trội từ `dev` (Hệ thống Ratings đa nguồn MdbList/Simkl/Trakt, Season Poster Tabs, Background Trailer Continuation qua Details, Live Backbuffer Override trên ExoPlayer, Subtitle Label)** kèm báo cáo chi tiết từng tệp trước khi thực thi.

> [!WARNING]
> **Phát hiện Quan trọng từ Mô phỏng Merge (`git merge-tree --write-tree tet2 origin/dev`):**
> 1. **Tổng số file ảnh hưởng:** Có tổng cộng **108 files** thay đổi giữa điểm hợp nhất `fc5c10c3d` và `origin/dev` (3999 dòng thêm, 457 dòng bớt).
> 2. **Phạm vi Xung đột Trực tiếp (15 files):** Có đúng **15 tệp** xảy ra CONFLICT (14 tệp code/UI/resource và 1 tệp add/add unit test). Cả 15 tệp đều đã được bóc tách mã nguồn và có chiến lược giải quyết cụ thể bảo tồn toàn bộ tính năng của `tet2`.
> 3. **Nhánh local `dev` đang bị tụt hậu:** Nhánh `dev` cục bộ đang dừng ở commit `a3532b819` (tụt 40 commit so với `origin/dev`). Cần fast-forward nhánh `dev` lên `15286b68c` trước khi merge vào `tet2`.
> 4. **Working Tree Unstaged Changes:** Tệp [build.gradle.kts](file:///f:/MyGithub/NuvioTV-Fast/app/build.gradle.kts) hiện đang có thay đổi chưa commit (`versionCode = 1201 -> 1202`, `versionName = "1.5.9" -> "1.6.0"`). Cần commit hoặc stash an toàn trước khi chạy lệnh merge.

---

## 1. Hiện trạng Git & Thông số Phạm vi Đồng bộ

| Thông số | Giá trị thực tế quan sát |
| :--- | :--- |
| **Nhánh hiện tại** | `tet2` (Commit HEAD: `18ce7f446` — *feat: add SIMKL authentication models, repository, and tests*) |
| **Nhánh nguồn đồng bộ** | `origin/dev` / `upstream/dev` (Commit HEAD: `15286b68c` — *Merge pull request #4712 from skoruppa/feat/hero-trailer-target-no-expand*) |
| **Điểm hợp nhất gốc (`merge-base`)** | `fc5c10c3d2d5def54bb21e452291cb7b281b07b3` (*Merge pull request #4596 from ysosrs123/pr/silo-server*, ngày 17-11-2026 02:32:00) |
| **Số commits mới trên `origin/dev`** | **40 commits** (13 Pull Requests/Merges) tác động **108 files** |
| **Số commits riêng trên `tet2`** | **12 commits** (kể từ merge commit `ba661122a` ngày 17-11-2026) |
| **Trạng thái mô phỏng 3-way Merge** | **15 files CONFLICT trực tiếp**, **93 files AUTO-MERGED sạch sẽ** |

---

## 2. Báo cáo Chi tiết 40 Commits Mới từ `origin/dev` (17/11/2026 – 19/11/2026)

Các commit mới từ upstream được phân bổ thành 7 nhóm tính năng/cải tiến lớn:

### 🌟 Nhóm 1: Hệ thống Đánh giá Đa Nguồn (Tracking Ratings System - PR #4683)
- **Commits:** `761d18801`, `6f9754b46`, `6055873d4`, `c4b07d284`, `62888d688`, `19811758d`
- **Nội dung:**
  - Thêm hệ thống xếp hạng sao cho phim và từng tập (`RatingStars.kt`, `RatingPanel.kt`, `RatingActionButton.kt`, `RatingViewModel.kt`).
  - Cho phép chọn nguồn xếp hạng (Rating Source Picker trong `TrackingSettingsScreen.kt` & `LayoutCardSettings.kt`).
  - Hỗ trợ đồng bộ, đọc và lưu rating hai chiều qua MdbList (`MdbListUserRatings.kt`, `MdbListUserRatingDecoder.kt`) và Simkl (`SimklRatings.kt`, `SimklSyncRepository.kt`).
  - Bổ sung tài nguyên chuỗi đa ngôn ngữ: `strings_ratings.xml` (en, vi, fr, el, iw, nl, sk).
- **Đánh giá & Tác động lên `tet2`:** **TÍNH NĂNG MỚI RẤT TỐT.** Tích hợp mượt mà với module Simkl mà `tet2` vừa phát triển (`SimklAuthRepository`), nâng cao trải nghiệm người dùng xem điểm đánh giá và xếp hạng trực tiếp trên TV.

### 🖼️ Nhóm 2: Season Poster Tabs trên Chi tiết Phim (PR #4675)
- **Commits:** `0a35557e0`, `d3c068a77`
- **Nội dung:**
  - Hiển thị poster đại diện từng mùa ngay trên các tab mùa (Season Tabs) trong `EpisodesSection.kt` và `MetaDetailsScreen.kt`.
  - Sử dụng `SmoothDownscaleTransformation.kt` để downscale poster sắc nét mà không tốn nhiều bộ nhớ RAM/GPU.
- **Đánh giá & Tác động lên `tet2`:** **CẢI TIẾN GIAO DIỆN XUẤT SẮC.** Cần tích hợp cẩn thận vào `EpisodesSection.kt` của `tet2` vì `tet2` đã chuyển đổi hàng tập sang `LeanbackNativeRow` và có cơ chế focus D-pad down riêng.

### 🎬 Nhóm 3: Background Trailer Layer & Handoff mượt mà từ Home sang Details (PR #4706, #4712, #4704)
- **Commits:** `18fe69375`, `051e3f6fd`, `4aa25bbdb`, `7dd9fcbfe`, `bac5e9548`, `4ad1e8bc4`, `15286b68c`
- **Nội dung:**
  - Thêm `BackgroundTrailerController.kt` và `BackgroundTrailerLayer.kt`: cho phép trailer đang phát ở Home tiếp tục phát liền mạch khi người dùng bấm vào xem trang chi tiết (`MetaDetailsScreen`), không bị ngắt quãng hoặc giật hình.
  - Thêm vị trí phát trailer `FocusedPosterTrailerPlaybackTarget.HERO_MEDIA_NO_EXPAND`: cho phép phát trailer ở Hero mà vẫn giữ poster ở dạng thu gọn (không bắt buộc mở rộng card).
  - Sửa lỗi ẩn thông tin chi tiết Hero khi trailer phát fullscreen và giữ poster có focus mở rộng chuẩn xác.
- **Đánh giá & Tác động lên `tet2`:** **TÍNH NĂNG MẠNH MẼ.** Giúp trải nghiệm xem trailer điện ảnh, cao cấp hơn. Cần kết hợp khéo léo với hệ thống Leanback và tối ưu GPU compositing của `tet2`.

### ⚡ Nhóm 4: Tối ưu Buffer & Live Player Override (PR #4708, #4709, #4697)
- **Commits:** `2cd82e788`, `362b91140`, `48557a894`, `a15263778`, `12c125ef9`, `67bbb3c3f`, `d1b9ba4ce`, `5b371fcd0`
- **Nội dung:**
  - `BitrateAwareLoadControl`: Hỗ trợ live override backbuffer và budget ngay trên instance `ExoPlayer` đang chạy qua reflection và handler posting (`applyBackBufferToInternalPlayer`, `applyBudgetToActiveLoadingStates`).
  - Thiết bị Low-RAM giữ nguyên back buffer đã cấu hình trừ khi phát hiện chuyển đổi DV7 (Dolby Vision Profile 7).
  - Gỡ bỏ trần giới hạn `minBufferMs / 2` đối với back buffer trong Performance Mode.
  - Subtitle Label: Hiển thị nhãn subtitle (Stremio subtitle label) làm tiêu đề chính và ID làm mô tả phụ trong `SubtitleSelectionOverlay.kt`.
  - Cache tra cứu tên ngôn ngữ (`LanguageUtils.kt`) tránh resolve lặp lại hàng trăm lần khi mở menu phụ đề.
  - Cập nhật native C++ JNI `dovi_bridge.cpp`: đóng gói kết quả xử lý và đếm số lần gọi/thành công chuyển đổi RPU.
- **Đánh giá & Tác động lên `tet2`:** **RẤT GIÁ TRỊ.** Code mới xử lý live override chuyên sâu hơn. `tet2` sẽ tích hợp tính năng này đồng thời bảo vệ các cơ chế riêng của `tet2` (`BufferBudgetControl`, `PlaybackBufferReserve`, Live TV zero backbuffer).

### 🎯 Nhóm 5: Tiếp tục Xem (Continue Watching) Bảo toàn Focus (PR #4707)
- **Commits:** `0042e28e7`, `f0df1830c`
- **Nội dung:**
  - Trong `HomeScreen.kt`, khi một card Tiếp tục Xem được xem xong và đẩy lên đầu hàng, con trỏ focus sẽ bám theo đúng danh tính card đó thay vì nhảy lung tung hoặc rơi về vị trí cũ.
- **Đánh giá & Tác động lên `tet2`:** **CẢI TIẾN UX TỐT.** Tự động merge sạch sẽ với `HomeScreen.kt`.

### 🛡️ Nhóm 6: Tối ưu Sidebar & Sửa Lỗi Profiles/Sync (PR #4691, #4692, #4694)
- **Commits:** `520a0568f`, `9ce49f451`, `30b2aeadf`, `03705cdd1`, `96d42a4d7`, `8675a97a0`
- **Nội dung:**
  - Khắc phục mất focus khi sidebar mở trong lúc màn hình Home vẫn đang tải ngầm (`SIDEBAR_FOCUS_RECOVERY_ATTEMPTS = 15`).
  - Giữ thông tin đăng nhập Jellyfin và Emby độc lập cho từng profile.
  - Đồng bộ cài đặt Ambient Mode trong profile sync.
- **Đánh giá & Tác động lên `tet2`:** Giữ phiên làm việc tách biệt cho profile. Ở `MainActivity.kt`, cần ghép logic focus recovery với `NativeSidebarView` của `tet2`.

### 🌐 Nhóm 7: Bản dịch & Tinh chỉnh Giao diện Khác (PR #4688, #4689, #4690, #4693, #4698, #4703, #4710)
- **Commits:** `51ebccc1c`, `87b344693`, `07241ff06`, `28b075d35`, `791e24967`, `499ba1974`, `2b02219d5`, `56f2ecd47`,...
- **Nội dung:**
  - Ẩn sự xuất hiện của khách mời gameshow/talk show khỏi danh sách phim đóng của diễn viên (`CastDetailScreen.kt`).
  - Tăng thời gian chờ trước/sau khi cuộn chữ mô tả Hero (`AutoScrollingText.kt`).
  - Sửa gradient Hero cho ngôn ngữ đọc từ phải sang trái (RTL layout).
  - Cập nhật bản dịch tiếng Pháp, Thổ Nhĩ Kỳ, Hebrew, và tiếng Việt.

---

## 3. Danh sách Tùy chỉnh Cốt lõi của `tet2` Bắt buộc Giữ nguyên 100%

Trong quá trình giải quyết conflict, các thành phần sau trên `tet2` **tuyệt đối không được làm suy giảm hay mất mát**:

1. **Live TV & EPG Engine:** Toàn bộ package `features/livetv/` (EPG Room DB, XMLTV parser, M3U/Xtream/Stalker, Live TV Guide 3 cột, PlayStoreGlowView bloom cho channel tile focus, giải mã UTF-8 playlist, lưu playlist đã chọn, quản lý favorites toàn cục).
2. **Leanback & Modern UI Performance:** Hệ thống Native Android Leanback cho toàn bộ trang Home và Details:
   - `LeanbackNativeRow.kt`, `LeanbackNativeGrid.kt`, `LeanbackUniversalAdapter.kt`.
   - `ModernInteropHorizontalGridView.kt`, `ModernRowFloatingFocusRingView.kt`, `LeanbackModernRowAdapter.kt`.
   - `LeanbackArtworkLoader.kt`, `LeanbackColorEngine.kt`, `PlayStoreGlowView.kt`.
   - `NativeSidebarView.kt` trong `MainActivity.kt`.
3. **Player Buffer & Audio Sizing Architecture:**
   - `ReservableLoadControl.kt` và thumbnail read-ahead reserve.
   - Cơ chế RAM tier sizing (`getAutoBufferMb`, `sizedBudgetMb`) trong `NuvioExoPlayerPerformanceHelper.kt`.
   - Giữ `backBufferMs = 0` bắt buộc cho Live TV trong `PlayerRuntimeControllerInitialization.kt` để tránh đầy RAM / giật lag khi chuyển kênh IPTV.
   - Cơ chế `BufferBudgetControl` và `pressureCapBytes`.
4. **Phụ đề Nâng cao & Phông chữ Tùy chỉnh:**
   - 5 font nhúng (`PhimMoi`, `Inter`, `Open Sans`, `DM Sans`, `Oswald`) và trình chọn phông chữ từ bộ nhớ ngoài.
   - Toàn bộ chuỗi ngôn ngữ tiếng Việt tương ứng trong `values-vi/strings.xml`.
5. **Simkl Authentication:**
   - Module xác thực Simkl mới thêm tại commit `18ce7f446` (`SimklAuthModels.kt`, `SimklAuthRepository.kt`, `SimklAuthRepositoryTest.kt`).
6. **Network & Headers Customization:**
   - `sanitizeHeaderMap(headers)` và audio delay hints trong `StreamMapper.kt`.
   - `ArcDns` / `IPv4FirstDns` trong `MediaServerModule.kt`.

---

## 4. Kế hoạch Giải quyết Chi tiết 15 Tệp Xung đột (Conflict Resolution Strategy)

Dưới đây là phương án xử lý chi tiết từng tệp, phân định rõ ràng những gì giữ lại và những gì tích hợp mới:

### 📄 File 1: `app/src/main/java/com/nuvio/tv/MainActivity.kt`
- **Xung đột:**
  - `tet2`: Dùng `NativeSidebarView` (`AndroidView(factory = { ctx -> NativeSidebarView(ctx)... })`) thay thế Compose Sidebar để đạt hiệu năng tối đa trên Android TV; loại bỏ các biến state Compose sidebar không cần thiết.
  - `origin/dev`: Thêm `@Inject lateinit var backgroundTrailerController: BackgroundTrailerController`, `LocalBackgroundTrailerController provides backgroundTrailerController`, và hàm `@Composable private fun SidebarFocusRecoveryEffect(...)` với `SIDEBAR_FOCUS_RECOVERY_ATTEMPTS = 15`.
- **Phương án giải quyết:**
  - ✅ **Tiếp nhận từ `dev`:** Thêm `backgroundTrailerController` injection và cung cấp `LocalBackgroundTrailerController provides backgroundTrailerController` vào `CompositionLocalProvider` (bắt buộc để các màn hình con dùng Background Trailer).
  - ✅ **Giữ nguyên của `tet2`:** Giữ `NativeSidebarView` và khối `AndroidView` render Native Sidebar tốc độ cao.
  - ✅ **Hòa giải:** Giữ `SidebarFocusRecoveryEffect` hoặc định tuyến focus recovery vào `nativeSidebarRef?.requestSidebarFocus(targetRoute)`.

### 📄 File 2: `app/src/main/java/com/nuvio/tv/core/player/BitrateAwareLoadControl.kt`
- **Xung đột:**
  - `tet2`: Kế thừa `PlaybackBufferReserve, BufferBudgetControl`, có các biến và phương thức: `bufferAllocator`, `pressureCapBytes`, `uncappedBudgetBytes`, `setPressureCapBytes()`, `allocatorTargetBytes()`, `effectiveBudgetBytes()`, và reserve thumbnail seek.
  - `origin/dev`: Bổ sung tham số `player: ExoPlayer? = null` vào `setBackBufferDurationOverrideMs` và `setBudgetBytesOverride`, thực hiện reflection trực tiếp vào `ExoPlayerImplInternal.backBufferDurationUs` và cập nhật `PlayerLoadingState.targetBufferBytes` trên playback looper thread.
- **Phương án giải quyết:**
  - ✅ **Kết hợp cả hai:**
    - Giữ trọn vẹn interface `BufferBudgetControl` và `PlaybackBufferReserve` cùng các phương thức tính toán budget/pressure cap của `tet2`.
    - Đưa tham số `player: ExoPlayer? = null` và toàn bộ cơ chế reflection (`applyBackBufferToInternalPlayer`, `applyBudgetToActiveLoadingStates`) từ `dev` vào.
    - Trong `currentTargetBufferBytes()`, tính toán theo `effectiveBudgetBytes()`.

### 📄 File 3: `app/src/main/java/com/nuvio/tv/data/mapper/StreamMapper.kt`
- **Xung đột:**
  - `tet2`: Bổ sung header sanitization `sanitizeHeaderMap(headers)` và trích xuất audio delay (`toStreamAudioDelayLanguage`, `toStreamAudioDelayMs`).
  - `origin/dev`: Bổ sung ánh xạ trường `label = dto.label?.trim()?.takeIf { it.isNotEmpty() }` cho đối tượng `Subtitle`.
- **Phương án giải quyết:**
  - ✅ **Tiếp nhận từ `dev`:** Thêm `label = dto.label?.trim()?.takeIf { it.isNotEmpty() }` vào khối tạo `Subtitle`.
  - ✅ **Giữ nguyên của `tet2`:** Giữ nguyên toàn bộ logic xử lý `sanitizeHeaderMap` và audio delay hints.

### 📄 File 4: `app/src/main/java/com/nuvio/tv/ui/components/TrailerPlayer.kt`
- **Xung đột:**
  - `tet2`: Dùng `playerAlpha` trực tiếp (1f/0f) thay vì `animateFloatAsState` để loại bỏ giật khung hình trên Android TV; xử lý re-attach player trong `update` của `AndroidView`.
  - `origin/dev`: Bổ sung trailer handoff đa màn hình (`incomingHandoff`, `seamlessHandoff`, `resolvedPool?.pendingHandoffFor(trailerUrl)`), truyền tọa độ view và góc zoom letterbox khi chuyển từ Home sang Details.
- **Phương án giải quyết:**
  - ✅ **Tiếp nhận từ `dev`:** Đưa vào `incomingHandoff`, `seamlessHandoff` và việc khởi tạo `hasRenderedFirstFrame` từ `seamlessHandoff` để trailer không bị chớp đen khi chuyển màn hình.
  - ✅ **Giữ nguyên của `tet2`:** Giữ tối ưu rendering trực tiếp, đảm bảo `view.player = trailerPlayer` và `resizeMode` ổn định trong `update`.

### 📄 File 5: `app/src/main/java/com/nuvio/tv/ui/screens/detail/EpisodeOptionsOverlay.kt`
- **Xung đột:**
  - Chỉ xung đột dòng import (`isEpisodeReleaseAired` trên `tet2` vs `TrackingMediaReference`, `TrackingRatingState` trên `dev`).
- **Phương án giải quyết:**
  - ✅ Gộp đầy đủ các import của cả hai phía.

### 📄 File 6: `app/src/main/java/com/nuvio/tv/ui/screens/detail/EpisodesSection.kt`
- **Xung đột:**
  - `tet2`: Chuyển đổi danh sách tập sang `LeanbackNativeRow` (`val leanbackEpisodes = remember(...)`), thêm `isFocusEnabled: Boolean = true`, và bộ xử lý phím D-pad Down trong `SeasonTabsRow`.
  - `origin/dev`: Thêm tính năng Season Poster Tabs (`seasonPosters`, `SmoothDownscaleTransformation`), bổ sung tham số `ratingMedia: (Video) -> TrackingMediaReference? = { null }`, và custom `seasonBringIntoViewSpec`.
- **Phương án giải quyết:**
  - ✅ **Tiếp nhận từ `dev`:** Bổ sung `ratingMedia` callback và tích hợp hiển thị poster mùa trong `SeasonTabsRow`.
  - ✅ **Giữ nguyên của `tet2`:** Giữ nguyên việc hiển thị danh sách tập bằng `LeanbackNativeRow` cực mượt, giữ `isFocusEnabled` và bộ điều hướng D-pad Down tùy chỉnh.

### 📄 File 7: `app/src/main/java/com/nuvio/tv/ui/screens/detail/MetaDetailsScreen.kt`
- **Xung đột:**
  - `tet2`: Quản lý cuộn ban đầu đến tập tiếp theo theo từng mùa độc lập (`initialEpisodeScrollDoneBySeason = remember(meta.id) { mutableStateMapOf<Int, Boolean>() }`), điều hướng `onSeasonTabsNavigateDown`.
  - `origin/dev`: Thêm giải quyết poster mùa (`seasonPosterStyle = remember(seasonPostersEnabled, meta, seasons)...`), chuyển giao trailer cho background layer, tích hợp Rating Panel & Star Rating.
- **Phương án giải quyết:**
  - ✅ **Tiếp nhận từ `dev`:** Tích hợp `seasonPosterStyle`, hệ thống Rating (sao đánh giá tập và phim), Background Trailer Layer.
  - ✅ **Giữ nguyên của `tet2`:** Giữ cơ chế nhớ vị trí cuộn tập theo từng mùa (`initialEpisodeScrollDoneBySeason`) và điều hướng `onSeasonTabsNavigateDown`.

### 📄 File 8: `app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeContent.kt`
- **Xung đột:**
  - `tet2`: Xử lý nút Back cho fullscreen trailer (`fullscreenTrailerPlaying`), tính toán alpha mờ dần cho hàng content khi phát video hero (`stableTrailerContentAlphaLambda`).
  - `origin/dev`: Xử lý nút Back thông minh: nếu đang ở item > 0 thì bấm Back sẽ quay về item 0 đầu hàng; xử lý `fullscreenHeroTrailerPlaying` và `stopHeroTrailer`; cung cấp `LocalBackgroundTrailerController`.
- **Phương án giải quyết:**
  - ✅ **Kết hợp cả hai:**
    - Trong `shouldInterceptBack` và `BackHandler`: ưu tiên đóng trailer nếu trailer đang phát (cả `fullscreenTrailerPlaying` và `fullscreenHeroTrailerPlaying`). Nếu không có trailer và `itemIndex > 0`, bấm Back sẽ cuộn về item đầu hàng như `dev`.
    - Giữ `LocalBackgroundTrailerController` để truyền dữ liệu phát trailer sang trang chi tiết.

### 📄 File 9: `app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeHero.kt`
- **Xung đột:**
  - `tet2`: Kiểm tra `if (animationsEnabled)` để bỏ qua Crossfade khi tắt hiệu ứng (tối ưu TV yếu); điều khiển `alpha = if (isTrailerPlayingFullScreen()) 0f else 1f` và `forceOffscreen` compositing.
  - `origin/dev`: Gắn `onGloballyPositioned` để lấy tọa độ `heroMediaRect` phục vụ handoff trailer mượt sang trang chi tiết; sửa lỗi tráng màu nền (scrim gradient) khi ở layout RTL.
- **Phương án giải quyết:**
  - ✅ **Tiếp nhận từ `dev`:** Thêm `onGloballyPositioned` đo tọa độ `heroMediaRect` và gradient RTL.
  - ✅ **Giữ nguyên của `tet2`:** Giữ bọc điều kiện `if (animationsEnabled)` và logic kiểm soát `alpha` khi trailer phát toàn màn hình.

### 📄 File 10: `app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeRows.kt`
- **Xung đột:**
  - `tet2`: Tệp này trên `tet2` đã được tinh gọn (296 dòng) vì toàn bộ logic hiển thị hàng đã chuyển sang `LeanbackModernRow` và `LeanbackModernRowAdapter`.
  - `origin/dev`: Đổi hằng số `FocusedPosterTrailerPlaybackTarget.HERO_MEDIA` thành `HERO_MEDIA_NO_EXPAND`.
- **Phương án giải quyết:**
  - ✅ **Giữ nguyên của `tet2`:** Bảo toàn cấu trúc Leanback 296 dòng của `tet2`. Đảm bảo giá trị enum `HERO_MEDIA_NO_EXPAND` mới được hỗ trợ đầy đủ.

### 📄 File 11: `app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeRowsList.kt`
- **Xung đột:**
  - `tet2`: Nhận tham số `trailerContentAlpha: () -> Float` và gắn `.graphicsLayer { alpha = trailerContentAlpha() }`.
  - `origin/dev`: Đã bỏ tham số `trailerContentAlpha` vì giao diện mở rộng poster mới không cần làm mờ cả danh sách hàng.
- **Phương án giải quyết:**
  - ✅ **Thống nhất:** Loại bỏ tham số `trailerContentAlpha` đồng bộ với `ModernHomeContent.kt` để code gọn gàng, giảm bớt 1 lớp graphicsLayer render đè lên danh sách hàng.

### 📄 File 12: `app/src/main/java/com/nuvio/tv/ui/screens/player/NuvioExoPlayerPerformanceHelper.kt`
- **Xung đột:**
  - `tet2`: Dùng `ReservableLoadControl` (hỗ trợ thumbnail seek reserve), thuật toán RAM tier auto buffer, và quản lý connection pool size.
  - `origin/dev`: Bỏ trần tính toán back buffer (`BACK_BUFFER_TARGET_SHARE_NUM / DEN`) để cho phép dùng trọn vẹn số ms back buffer người dùng cài đặt (`resolvedBackBufferMs = backBufferMs.coerceAtLeast(0)`).
- **Phương án giải quyết:**
  - ✅ **Tiếp nhận từ `dev`:** Không gò ép back buffer theo trần cố định khi người dùng chủ động cấu hình lớn.
  - ✅ **Giữ nguyên của `tet2`:** Giữ nguyên `ReservableLoadControl`, RAM tier auto-sizing, và các cơ chế buffer an toàn.

### 📄 File 13: `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt`
- **Xung đột:**
  - `tet2`: Có dòng quan trọng: `configuredBackBufferMs = if (isLiveTv) 0 else bufferSettings.backBufferDurationMs` và hạ về 0 nếu là Live TV hoặc Low-RAM DV7.
  - `origin/dev`: Bỏ việc hạ backbuffer về 0 khi khởi tạo VOD trên low-RAM (chỉ hạ về 0 tại first frame nếu stream thực tế là DV7).
- **Phương án giải quyết:**
  - ✅ **Quyết định tối ưu:**
    - Đối với **Live TV**: **BẮT BUỘC giữ `backBufferMs = 0`** của `tet2` (vì Live TV giữ back buffer sẽ gây tràn heap và giật đơ khi chuyển kênh).
    - Đối với **VOD**: Áp dụng code mới của `dev` (giữ back buffer ban đầu để tua lùi được ngay, chỉ hạ về 0 ở first frame nếu stream thực sự là DV7 trên thiết bị RAM thấp).

### 📄 File 14: `app/src/main/res/values-vi/strings.xml`
- **Xung đột:**
  - `tet2`: Đã bổ sung bản dịch tiếng Việt cho 5 phông chữ phụ đề (`sub_font_phimmoi`, `sub_font_inter`,...) và độ dày nét viền chữ `sub_outline_width` ("Độ dày viền").
  - `origin/dev`: Thêm `<string name="sub_outline_width">Độ rộng viền</string>`.
- **Phương án giải quyết:**
  - ✅ Giữ toàn bộ chuỗi phông chữ phụ đề của `tet2` và sử dụng chuỗi dịch viền chuẩn nhất.

### 📄 File 15: `app/src/test/java/com/nuvio/tv/core/player/BitrateAwareLoadControlTest.kt`
- **Xung đột:**
  - Cả hai nhánh đều tạo mới tệp này (Add/Add conflict).
  - `tet2`: Test kiểm tra quản lý trần bộ nhớ (`budgetMb`) và ưu tiên thời gian khi buffer.
  - `origin/dev`: Test kiểm tra live override back buffer và budget tác động trực tiếp lên `ExoPlayerImplInternal` và `PlayerLoadingState`.
- **Phương án giải quyết:**
  - ✅ **Hợp nhất hoàn hảo:** Đưa toàn bộ các test cases của cả hai bên vào trong một class duy nhất. Đảm bảo 100% test cases đều pass!

---

## 5. Kế hoạch Thực thi Từng Bước (Step-by-Step Execution Plan)

### Bước 1: Chuẩn bị & Đồng bộ Nhánh Nguồn
1. Stash hoặc commit tạm thay đổi chưa lưu trên working tree ([build.gradle.kts](file:///f:/MyGithub/NuvioTV-Fast/app/build.gradle.kts)).
2. Tạo nhánh sao lưu an toàn trước khi merge:
   ```powershell
   git branch backup-tet2-before-merge-20261119
   ```
3. Fast-forward nhánh local `dev` lên khớp hoàn toàn với `origin/dev` (`15286b68c`):
   ```powershell
   git checkout dev
   git merge --ff-only origin/dev
   git checkout tet2
   ```

### Bước 2: Kích hoạt `git merge dev`
1. Thực hiện lệnh merge nhánh `dev` vào `tet2`:
   ```powershell
   git merge dev
   ```
2. Git sẽ tự động gộp 93 files và dừng lại ở 15 files conflict đã dự báo trước.

### Bước 3: Phẫu thuật Giải quyết 15 Files Xung đột
1. Lần lượt mở và sửa 15 files conflict theo đúng chiến lược đã định tại **Mục 4**:
   - `MainActivity.kt`
   - `BitrateAwareLoadControl.kt`
   - `StreamMapper.kt`
   - `TrailerPlayer.kt`
   - `EpisodeOptionsOverlay.kt`
   - `EpisodesSection.kt`
   - `MetaDetailsScreen.kt`
   - `ModernHomeContent.kt`
   - `ModernHomeHero.kt`
   - `ModernHomeRows.kt`
   - `ModernHomeRowsList.kt`
   - `NuvioExoPlayerPerformanceHelper.kt`
   - `PlayerRuntimeControllerInitialization.kt`
   - `values-vi/strings.xml`
   - `BitrateAwareLoadControlTest.kt`
2. Kiểm tra lại toàn bộ file xem còn sót bất kỳ dấu hiệu xung đột `<<<<<<<`, `=======`, `>>>>>>>` nào không.

### Bước 4: Kiểm tra & Sửa lỗi Xung đột Ngầm (Post-Merge Code Audit)
1. Kiểm tra constructor và interface binding trong các file DI (`TrackingModule.kt`, `MediaServerModule.kt`,...).
2. Kiểm tra tính tương thích giữa `SimklAuthRepository` mới trên `tet2` và `SimklRatings.kt` mới từ `dev`.
3. Khôi phục lại thay đổi version trong [build.gradle.kts](file:///f:/MyGithub/NuvioTV-Fast/app/build.gradle.kts) (`versionCode = 1202`, `versionName = "1.6.0"`).

### Bước 5: Biên dịch & Kiểm thử Toàn diện (Verification & Quality Gate)
1. **Chạy Unit Tests:**
   ```powershell
   ./gradlew testDebugUnitTest
   ```
   Đặc biệt xác nhận các test sau vượt qua:
   - `BitrateAwareLoadControlTest`
   - `SimklAuthRepositoryTest`
   - `SimklRatingsTest`
   - `MdbListUserRatingsTest`
   - `LanguageLookupCacheTest`
   - `ModernHomeIdentityRelocationTest`
2. **Kiểm tra Biên dịch Code (Compile Check):**
   ```powershell
   ./gradlew compileDebugKotlin
   ```
3. **Kiểm tra Lint/Resource:** Đảm bảo không có lỗi thiếu string hoặc duplicate XML key.

### Bước 6: Hoàn tất Merge Commit & Báo cáo
1. `git add .` và tạo commit hoàn tất:
   ```powershell
   git commit -m "Merge branch 'dev' into tet2: sync 40 commits (2026-11-17 to 2026-11-19) with Ratings system, Season poster tabs, Background trailer handoff, and preserved custom Leanback/TorrServer/LiveTV/Audio configurations"
   ```
2. Xuất báo cáo tổng kết chi tiết cho người dùng.
