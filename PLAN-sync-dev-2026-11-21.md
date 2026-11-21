# PLAN-sync-dev-2026-11-21: Kế Hoạch Đồng Bộ Toàn Bộ Commit Mới Từ Nhánh `dev` (`origin/dev`) Sang `tet2` (19/11/2026 – 21/11/2026)

> [!IMPORTANT]
> **Mục tiêu cốt lõi:** Cập nhật toàn bộ **155 commits mới nhất** (gồm **22 PR/Merge** từ `2026-11-19` đến `2026-11-21 04:19`) từ nhánh `origin/dev` (`d2eeb3d0e`, trùng khớp 100% với `upstream/dev`) sang nhánh hiện tại `tet2` (`88276be2f`).
> - Xử lý chính xác **22 tệp xung đột trực tiếp** (21 xung đột nội dung + 1 xung đột modify/delete `ContentCard.kt`) và rà soát **154 tệp auto-merged**.
> - **Bảo toàn 100% toàn bộ tùy chỉnh trên `tet2`** (Google TTS Offline, Live TV & Config Server/EPG, Home Poster Loading & Leanback UI, NativeSidebarView, TorrServer, Custom Fonts, Trakt PKCE, Official Backend URLs...).
> - **Tiếp nhận các cải tiến vượt trội từ `dev`** (TV Drag Reorder `TvReorderList`, Audio-Subtitle Auto Sync, HTTP/2 retry trên lỗi 403 cho proxyHeaders, Trailer Subtitles, Khôi phục tập đang xem sau process death, Giữ vị trí phát khi đổi nguồn, VC-1/Tunneled fallback, Hardware Bitmap setting, CloudStream `.cs3` lifecycle crash fix, Filmography type filter...) kèm báo cáo so sánh chi tiết trước khi sửa.

> [!WARNING]
> **Cảnh Báo Kỹ Thuật Quan Trọng Cần Lưu Ý Trước Khi Thực Thi:**
> 1. **PR #4150 (`feat/audio-sync-tester`) thêm dependency `sherpa-onnx-1.13.8.aar` vào `app/build.gradle.kts`:**
>    - Task Gradle `downloadAudioSyncRuntime` sẽ tải file `sherpa-onnx-1.13.8.aar` từ GitHub (có kiểm tra SHA-256) và đóng gói thư viện native ONNX Runtime (dùng cho **Đồng bộ phụ đề theo âm thanh / Silero VAD + ASR**, **HOÀN TOÀN TÁCH BIỆT** với Google TTS Offline của `tet2`).
>    - **Hệ quả:** Lần build đầu tiên cần kết nối mạng tải `.aar` từ GitHub và kích thước APK sẽ tăng thêm ~15–25MB mỗi ABI (cùng file `res/raw/silero_vad_v5_16k.bin`).
>    - **Đề xuất:** Giữ trọn vẹn tính năng Audio-Subtitle Sync từ `dev` vì đây là tính năng lớn được tích hợp sâu vào `PlaybackSubtitleSettings`, `SubtitleSelectionOverlay` và `PlayerRuntimeController` (46 files + 18 unit tests). Tuy nhiên nếu bạn muốn giữ APK siêu nhẹ (bỏ Audio Sync), vui lòng phản hồi trước khi chạy `/orchestrate`.
> 2. **Nhánh local `dev` đang tụt hậu:** Nhánh `dev` cục bộ đang dừng ở `15286b68c` (tụt 155 commits so với `origin/dev` / `upstream/dev`). Cần fast-forward `dev` lên `d2eeb3d0e` (hoặc merge trực tiếp từ `origin/dev`) sau khi tạo nhánh sao lưu `backup-tet2-before-merge-20261121`.
> 3. **Xung đột Modify/Delete với `ContentCard.kt`:** `tet2` đã gỡ bỏ `ContentCard.kt` vì chuyển toàn bộ sang kiến trúc `LeanbackNativeRow` / `LeanbackModernRowAdapter`, trong khi `origin/dev` sửa 8 dòng decode size trên `ContentCard.kt`. Khuyến nghị giữ trạng thái **đã xóa** trên `tet2` và kiểm chứng 0 nơi tham chiếu `ContentCard`.

---

## 1. Hiện Trạng Git & Thông Số Phạm Vi Đồng Bộ

| Thông số | Giá trị thực tế quan sát (từ `git` & `git merge-tree`) |
| :--- | :--- |
| **Nhánh hiện tại** | `tet2` (HEAD: `88276be2f` — *feat: add offline Google TTS engine for subtitle text-to-speech playback*, Working tree sạch) |
| **Nhánh nguồn đồng bộ** | `origin/dev` & `upstream/dev` (HEAD: `d2eeb3d0e` — *fix(android): mark wifi as optional*, `2026-11-21 04:19:39 +0300`) |
| **Nhánh local `dev`** | `15286b68c` (cần fast-forward lên `d2eeb3d0e`) |
| **Điểm hợp nhất gốc (`merge-base`)** | `15286b68c48b1afc5632aa309ed3d4afc3b5a4d7` (*Merge PR #4712*, ngày `2026-11-18 18:10:05 +0100`) |
| **Tổng commits mới trên `origin/dev`** | **155 commits** (22 first-parent PR merges/commits từ `2026-11-19` đến `2026-11-21`) |
| **Tổng quy mô thay đổi từ `origin/dev`** | **176 files changed**, `+20,938 insertions`, `-2,184 deletions` |
| **Số commits riêng mới trên `tet2` (từ 19/11)** | **20 commits** (TTS Offline, Live TV Config Server, Poster Loading Optimization, Leanback, NativeSidebarView, Trakt PKCE...) |
| **Kết quả mô phỏng `git merge-tree`** | **22 files CONFLICT trực tiếp** (21 content + 1 modify/delete), **154 files AUTO-MERGED sạch sẽ** |

---

## 2. Báo Cáo Chi Tiết 22 PR/Merge (155 Commits) Từ `origin/dev` (19/11/2026 – 21/11/2026)

### 🎧 Nhóm 1: Audio-Subtitle Auto Sync — Tự Động Đồng Bộ Phụ Đề Bằng Âm Thanh (PR #4150)
- **Merge commit:** `c66bafc3c` (*Merge PR #4150 from DavidVamaiotu/feat/audio-sync-tester*)
- **Nội dung:**
  - Thêm toàn bộ gói `ui/screens/player/audiosync/` (24 files + gói `asr/` 8 files + `AudioSyncFfmpegDecoder.kt` + `res/raw/silero_vad_v5_16k.bin` + 18 unit tests): lấy mẫu âm thanh PCM từ ExoPlayer/MPV (`AudioSyncTaps.wrapAudioSink`, `NuvioMpvSurfaceView`), phát hiện lời thoại bằng Silero VAD + nhận dạng lời nói Sherpa-ONNX để căn chỉnh tự động độ lệch phụ đề khi không có phụ đề nhúng tham chiếu.
  - Thêm `AudioSyncSettingsItems.kt` gắn vào `PlaybackSubtitleSettings.kt`, trạng thái đồng bộ trên `SubtitleSelectionOverlay.kt`, chuỗi `audio_sync_strings.xml` (EN/PL) và giấy phép `audio_sync_NOTICE.md`.
- **Đánh giá & Tương thích với `tet2`:**
  - **Không xung đột với Google TTS Offline** (`AudioSyncTaps.wrapAudioSink` bọc `baseAudioSink` trước `PlaybackSpeedAwareAudioSink`, hoàn toàn độc lập với `TextToSpeech` và `PlayerTtsVolumeDuckingPolicy`).
  - Cần giữ `downloadAudioSyncRuntime` + `sherpaOnnxAar` trong `app/build.gradle.kts` để biên dịch đầy đủ.

### 🔀 Nhóm 2: TV Drag Reorder — Kéo Thả Sắp Xếp Danh Sách Trên Android TV (PR #4725)
- **Merge commit:** `abf2deb3b` (*Merge PR #4725 from halibiram/feature/tv-catalog-reorder-drag*, kèm `ec32284f5`, `a23a60201`, `3a1c32735`, `3abb3cdde`, `bbf87698d`, `67cbb11c3`...)
- **Nội dung:**
  - Thêm component mới [TvReorderList.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/components/TvReorderList.kt) hỗ trợ ảo hóa (virtualized), kéo thả mượt mà bằng phím D-Pad cho 5 màn hình:
    1. `CatalogOrderScreen.kt` (hỗ trợ kéo thả + ghim đầu trang + Follow addons)
    2. `AddonManagerScreen.kt` (kéo thả sắp xếp thứ tự Addon + ẩn URL + khôi phục focus sau xóa)
    3. `CollectionManagementScreen.kt` (kéo thả bộ sưu tập)
    4. `CollectionEditorScreen.kt` & các file con (kéo thả thư mục/nguồn + cảnh báo chưa lưu khi thoát)
    5. `MDBListSettingsScreen.kt` (kéo thả thứ tự điểm đánh giá MDBList)
  - Bổ sung tham số `suppressBackward`/`suppressForward`/`forceBackward`/`forceForward` cho `SettingsVerticalScrollIndicators`.
- **Đánh giá & So sánh với `tet2`:**
  - **Code mới của `dev` vượt trội rõ rệt** về UX D-Pad trên Android TV so với nút mũi tên Lên/Xuống cũ.
  - Trên `tet2`, 7/10 file Collection/Catalog/MDBList chỉ có thay đổi dọn import thừa (không có logic custom), nên **tiếp nhận trọn vẹn `TvReorderList` từ `dev`**.
  - Riêng `AddonManagerScreen.kt` (có animation xoay icon Refresh khi `isRefreshingAddons` trên `tet2`) và `SettingsScrollIndicators.kt` (đã bỏ `AnimatedVisibility` để tăng tốc trên TV yếu trên `tet2`) sẽ được **hòa giải giữ trọn cải tiến của cả hai bên**.

### 🖼️ Nhóm 3: Tối Ưu Bộ Nhớ Ảnh Home & Cài Đặt Hardware Bitmaps (PR #4730)
- **Merge commit:** `aac6d5942` (`0845ea3b7`, `1ffb58f37`)
- **Nội dung:**
  - Thêm [HardwareBitmapSettingInterceptor.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/core/image/HardwareBitmapSettingInterceptor.kt) + tùy chọn **"Giải mã ảnh phần cứng (Hardware Bitmaps)"** trong `NetworkSettingsScreen.kt` (Advanced Settings) & `AdvancedSettingsViewModel.kt` & `ImagePerformancePreferences.kt`.
  - Sửa kích thước decode ảnh và khớp `MemoryCache.Key` trong `ContentCard.kt`, `ModernHomeRows.kt`, `ModernHomeRowsList.kt`.
- **Đánh giá & So sánh với `tet2`:**
  - **RẤT QUAN TRỌNG:** `tet2` vừa triển khai kiến trúc tải poster chuyên sâu hơn nhiều (`HomeImageLoadingProfile`, `LeanbackArtworkLoader`, `ArtworkMemoryCache`, `sizedHomeArtworkUrl` TMDB downscaler, `SavedCopyOnErrorFetcher`, `ModernInteropHorizontalGridView`).
  - **Quyết định hòa giải:**
    - ✅ **Tiếp nhận từ `dev`:** Thêm `HardwareBitmapSettingInterceptor` vào `NuvioApplication.kt` và mục toggle vào `AdvancedSettingsViewModel.kt` / `NetworkSettingsScreen.kt`. (Đã xác minh an toàn 100%: `LeanbackArtworkLoader` đặt tường minh `.allowHardware(false)` và `HardwareBitmapSettingInterceptor` tôn trọng extra này nên **không gây crash `getPixels()`** trên `LeanbackColorEngine`).
    - ✅ **Giữ nguyên của `tet2`:** Giữ trọn bộ `HomeImageLoadingProfile`, `ArtworkMemoryCache`, `sizedHomeArtworkUrl`, `ModernHomeRows` & `ModernHomeRowsList` phiên bản Leanback tối ưu của `tet2`, chỉ đồng bộ thêm hàm helper `cardImageMemoryCacheKey` nếu còn tham chiếu.

### 🎬 Nhóm 4: Phụ Đề Trailer & Handoff Trailer Mượt Mà (PR #3977, #4741, #4738, #4744 + `253eaf6d4`, `acb970c42`)
- **Merge commits:** `e7b5cdfe8`, `19d283953`, `1981d7ff4`, `640ba7eb0`, `253eaf6d4`, `acb970c42`
- **Nội dung:**
  - Hiển thị phụ đề trên Trailer (`TrailerSubtitleTrack`, `TrailerPlayer.kt`, toggle trong `LayoutDetailSettings.kt`, gắn phụ đề ngay lần tải đầu).
  - Nối tiếp trailer mượt từ `FolderDetailScreen` sang `MetaDetailsScreen` (`5f5c19199`), giữ Home hiển thị trong lúc Details fade-in (`4d8cd878e`), tạm dừng trailer khi mở trang Cast/Network (`253eaf6d4`), tránh chớp backdrop khi handoff (`acb970c42`).
- **Đánh giá:** **CẢI TIẾN UX XUẤT SẮC.** Hòa giải trên `TrailerPlayer.kt` và `FolderDetailScreen.kt` để vừa có Trailer Subtitles + Handoff, vừa giữ nguyên tối ưu bỏ `AnimatedVisibility`/fade alpha trên `TrailerPlayer` và `LeanbackNativeGrid` trên `FolderDetailScreen` của `tet2`.

### 🛠️ Nhóm 5: Hotfix Player & CloudStream Từ `main` / `hotfix-1.1.1` (`58f15a350`, `4cb3e3492`, `d2eeb3d0e`)
- **Commits:** `d2eeb3d0e`, `f4ce0aa64`, `8c8028085`, `1dd7a70e8`
- **Nội dung:**
  - `d2eeb3d0e`: Đánh dấu `<uses-feature android:name="android.hardware.wifi" android:required="false" />` trong `AndroidManifest.xml` (hỗ trợ Android TV Box chỉ dùng LAN Ethernet không bị chặn trên Store).
  - `f4ce0aa64`: Giữ nguyên vị trí đang xem khi đổi nguồn phát (source switching).
  - `8c8028085`: Khôi phục đúng tập đang xem sau khi Android kill app nền (`PlayerStreamRouteState`, `LaunchIntentReplay`, lưu progress khi MPV pause).
  - `1dd7a70e8`: Thêm stub `com.lagradost.cloudstream3.MainActivity` để chặn crash `NoClassDefFoundError` cho các plugin `.cs3` như `WatchParty.cs3`.
- **Đánh giá:** **BẮT BUỘC TIẾP NHẬN (TOÀN BỘ AUTO-MERGE SẠCH).** Rất giá trị cho độ ổn định trên Android TV.

### 🌐 Nhóm 6: Tự Động Thử Lại HTTP/2 Khi Bị 403 Với `proxyHeaders` (PR #4672)
- **Merge commit:** `5b38442d7` (`PlayerPlaybackNetworking.kt`, `ProxyHeadersProtocolTest.kt`)
- **Nội dung:** Giữ phát mặc định HTTP/1.1, nhưng nếu stream có `proxyHeaders` gặp HTTP `403`, tự động thử lại 1 lần qua HTTP/2 (ALPN) và cache host trong bộ nhớ (`http2Hosts`).
- **Đánh giá:** **CẢI TIẾN MẠNG RẤT HỮU ÍCH.** Kết hợp sạch với `IptvHeaderProvider.mergeWithDefaults`, `ArcDns`/`IPv4FirstDns`, `canReusePrewarmFor` và xử lý header tùy chỉnh của `tet2`.

### 🎥 Nhóm 7: Fallback Nguồn Cho Lỗi VC-1 & Tunneled Decoder (PR #4740)
- **Merge commit:** `432540035` (`44549e9a0`)
- **Nội dung:** Thêm cờ `tunneledFirstFrameAssumed` để khi stream tunneled hoặc VC-1 lỗi giải mã mà chưa thực sự ra hình ổn định, player tự động chuyển sang nguồn phát tiếp theo thay vì dừng báo lỗi.
- **Đánh giá:** Auto-merge sạch, cải thiện đáng kể tỷ lệ phát thành công tự động.

### 🏠 Nhóm 8: Sửa Khởi Động Hero Sources & Sắp Xếp Lịch Chiếu Cùng Ngày (PR #4752, #4733)
- **Merge commits:** `09330465c` (`a0ec0aeae`), `b3b4481dd` (`b37084be7`)
- **Nội dung:**
  - Đọc `heroCatalogSelections.first()` trước khi tải catalog đầu tiên trong `HomeViewModelCatalogPipeline.kt` để Hero không bị nhảy nhầm nguồn catalog khi cold start.
  - Sắp xếp chính xác các tập sắp chiếu trong cùng ngày theo giờ phát sóng (`ContinueWatchingUpcomingSortTest.kt`).
- **Đánh giá:** Auto-merge sạch, hoạt động ăn khớp hoàn toàn với `loadAllCatalogsPipeline` đã tối ưu drip-load của `tet2`.

### 🎭 Nhóm 9: Bộ Lọc Phim/Series Trong Trang Diễn Viên & Xác Nhận Xóa Repo Plugin (PR #4702, #4727, #4714)
- **Merge commits:** `a73bacbe9`, `197bfc393`, `1391e6bdc`
- **Nội dung:**
  - Thêm tab lọc **Tất cả / Phim lẻ / Phim bộ** (`FilmographyFilterTabs`) trong `CastDetailScreen.kt`.
  - Thêm hộp thoại xác nhận trước khi xóa repository plugin trong `PluginScreen.kt`.
  - Sửa focus avatar cho ngôn ngữ RTL (`AvatarPickerGrid.kt`).
- **Đánh giá:** Tích hợp tab lọc của `dev` kết hợp giữ nguyên `LeanbackNativeRow` và điều hướng D-Pad Lên/Xuống tùy chỉnh trong `CastDetailScreen.kt` của `tet2`.

### 🌍 Nhóm 10: Bản Dịch Đa Ngôn Ngữ (PR #4722, #4720, #4731)
- **Merge commits:** `ff7fbd747` (Tiếng Việt!), `033cf9fe7` (Tiếng Tây Ban Nha), `dcdafb9cb` (Tiếng Ba Lan).
- **Nội dung:** Cập nhật chuỗi dịch tiếng Việt (`values-vi/strings.xml`, `values-vi/strings_servers.xml`) và bổ sung chuỗi mới cho Reorder, Filmography filter, Audio Sync, Hardware Bitmaps, Trailer Subtitles, Plugin delete confirm.

---

## 3. Danh Sách Tùy Chỉnh Cốt Lõi Của `tet2` Bắt Buộc Giữ Nguyên 100%

Trong quá trình giải quyết conflict và hậu kiểm, tuyệt đối bảo tồn toàn bộ các thành phần sau trên `tet2`:

1. **Đọc Phụ Đề Bằng Google TTS Offline (mới thêm `88276be2f`):**
   - Toàn bộ gói `com.nuvio.tv.core.tts.*` (11 cài đặt DataStore theo Profile, bộ lọc giọng theo ngôn ngữ phụ đề ưu tiên fallback `vi`, Speech Completion Watchdog + Dynamic Carry-Over, cache voice trên `ttsExecutor`, bộ đếm bước nhảy `nextSteppedOption`).
   - `SubtitleTtsOverlay.kt`, `PlayerTtsVolumeDuckingPolicy.kt`, tích hợp `showTtsOverlay` trong `PlayerScreen.kt` / `PlayerRuntimeController*`, mục cài đặt TTS trong `PlaybackAudioSettings.kt`, và `<queries> TTS_SERVICE` trong `AndroidManifest.xml`.
2. **Live TV & Web Config Server / EPG (`6d9900c9f` và các commit trước):**
   - Toàn bộ `features/livetv/` (EPG Room DB, XMLTV, M3U/Xtream/Stalker, Live TV Config Server, `LiveTvStorage.init(this)` trong `NuvioApplication`, `IptvHeaderProvider.mergeWithDefaults` trong `PlayerPlaybackNetworking`, `backBufferMs = 0` bắt buộc cho Live TV).
3. **Tối Ưu Tải Poster & Kiến Trúc Leanback UI (`fe327fb1e`..`27ba3ebfc`):**
   - `HomeImageLoadingProfile` (phân hạng RAM thấp/chuẩn), `ArtworkMemoryCache`, `SavedCopyOnErrorFetcher`, `sizedHomeArtworkUrl` TMDB downscaler, `onTrimMemory`/`onLowMemory` trong `NuvioApplication`.
   - `LeanbackNativeRow`, `LeanbackNativeGrid`, `LeanbackModernRowAdapter`, `ModernInteropHorizontalGridView`, `LeanbackArtworkLoader`, `LeanbackColorEngine`, `PlayStoreGlowView`.
   - `ModernHomeRows.kt` & `ModernHomeRowsList.kt` bản tối ưu Leanback + throttle lazy catalog + drip-load.
   - `NativeSidebarView` trong `MainActivity.kt`.
   - `CastDetailScreen.kt` (dùng `LeanbackNativeRow` + D-Pad `portraitFocusRequester`) và `FolderDetailScreen.kt` (dùng `LeanbackNativeGrid`).
4. **Cấu Hình Build, Mạng, Xác Thực & Hiệu Năng TV:**
   - `app/build.gradle.kts`: toàn bộ `official*Url` buildConfigFields, `applicationVariants` đặt tên `NuvioTV-<version>-<abi>.apk`, debug `applicationId = "com.nuvio.tv.debug"`, `lint { disable += ... }`, `androidx.leanback`, `okhttp.dnsoverhttps`, `coil:3.3.0`, giữ `versionCode`/`versionName` nhánh `tet2`.
   - `AdvancedSettingsViewModel.kt`: giữ `animationsEnabled` (`ThemeDataStore`) và `gpuOffscreenCompositingEnabled`.
   - `AddonManagerScreen.kt`: giữ hiệu ứng xoay icon khi `isRefreshingAddons` + đồng bộ trạng thái refresh.
   - `SettingsScrollIndicators.kt`: giữ bỏ `AnimatedVisibility` (render trực tiếp `if (...) Icon(...)` tối ưu TV yếu).
   - `TrailerPlayer.kt`: giữ tối ưu bỏ `AnimatedVisibility` & bỏ `animateFloatAsState` cho `playerAlpha` (tránh giật khung hình trên Android TV).
   - TorrServer integration, Trakt PKCE (`39945f6c6`), 5 custom subtitle fonts, và toàn bộ chuỗi tiếng Việt custom (TTS, Live TV, TorrServer, Fonts, Advanced Settings).

---

## 4. Kế Hoạch Giải Quyết Chi Tiết Toàn Bộ 22 Tệp Xung Đột (Conflict Resolution Blueprint)

### Cụm A: Build, Ứng Dụng, Ảnh & Màn Hình Home (6 tệp)

#### 📄 1. `app/build.gradle.kts`
- **Xung đột:**
  - `tet2`: Có `officialSupabaseUrl`/`officialTrailerApiUrl`..., `versionCode`/`versionName` riêng, `applicationVariants` rename APK, `lint` config, debug `applicationId`, bỏ filter `baselineProfile`, thêm `androidx.leanback`, `okhttp.dnsoverhttps`, `coil:3.3.0`.
  - `origin/dev`: Thêm imports (`URI`, `MessageDigest`, `Files`, `StandardCopyOption`), bump `versionCode`/`versionName` upstream, `packaging.jniLibs.excludes` cho `libsherpa-onnx-c-api.so` & `cxx-api.so`, task `downloadAudioSyncRuntime` tải `sherpa-onnx-1.13.8.aar`, `implementation(files(sherpaOnnxAar).builtBy(downloadAudioSyncRuntime))`, `testImplementation(kotlin("test"))`.
- **Giải pháp (Hòa giải — Giữ `tet2` + Nhận runtime Audio Sync từ `dev`):**
  - ✅ Giữ 100% toàn bộ cấu hình custom, version, official URLs, APK naming, applicationId và dependencies của `tet2`.
  - ✅ Bổ sung `excludes` jniLibs, task `downloadAudioSyncRuntime`, `implementation(files(sherpaOnnxAar)...)` và `testImplementation(kotlin("test"))` từ `dev`.

#### 📄 2. `app/src/main/java/com/nuvio/tv/NuvioApplication.kt`
- **Xung đột:**
  - `tet2`: Có `ArcDns.init`, `LiveTvStorage.init`, `onTrimMemory`/`onLowMemory`, `sizedHomeArtworkUrl` Mapper, `SavedCopyOnErrorFetcher`, `ArtworkMemoryCache`, `imageDiskCacheBytes`, `maxBitmapSize`, `loadingProfile.bitmapDecodeParallelism`.
  - `origin/dev`: Thêm `add(HardwareBitmapSettingInterceptor { imagePerformancePreferences.hardwareBitmapsEnabled })` trong `.components { ... }` và bỏ 1 dòng comment cũ ở `.memoryCache`.
- **Giải pháp (Hòa giải tối ưu):**
  - ✅ Giữ 100% toàn bộ kiến trúc RAM/Disk/Network/LiveTV của `tet2`.
  - ✅ Thêm `HardwareBitmapSettingInterceptor` từ `dev` làm interceptor đầu tiên trong `.components { ... }`. (An toàn tuyệt đối vì `LeanbackArtworkLoader` và `ClassicFocusGradientBackdrop` đã đặt `.allowHardware(false)` tường minh).

#### 📄 3. `app/src/main/java/com/nuvio/tv/ui/components/ContentCard.kt` *(CONFLICT: modify/delete)*
- **Xung đột:**
  - `tet2`: Đã xóa tệp này (không còn bất kỳ tham chiếu nào trên toàn bộ mã nguồn `tet2`).
  - `origin/dev`: Sửa 8 dòng chọn `baseCardWidth` thay vì `maxRequestCardWidth` khi chưa expand poster.
- **Giải pháp (Giữ `tet2`):**
  - ✅ Xóa tệp này (`git rm app/src/main/java/com/nuvio/tv/ui/components/ContentCard.kt`). Lý do: `tet2` đã chuyển hoàn toàn sang `LeanbackModernRowAdapter` / `LeanbackArtworkLoader` (tự decode đúng kích thước mục tiêu + TMDB downscaler `sizedHomeArtworkUrl` ưu việt hơn), và các file mới của `dev` không hề thêm tham chiếu mới tới `ContentCard`.

#### 📄 4. `app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeRows.kt`
- **Xung đột:**
  - `tet2`: Tệp đã tinh gọn hoàn toàn sang `LeanbackModernRow` & `LeanbackModernRowAdapter` (`HomeImageLoadingProfile`).
  - `origin/dev`: Cập nhật hàm `imageUrlAndKey` trong `ModernRowSection` (Compose cũ) dùng `cardImageMemoryCacheKey(...)` và bỏ tham số `expandEnabled` khỏi `resolveCatalogCardMetrics(...)`.
- **Giải pháp (Giữ `tet2` + Đồng bộ signature nếu bị gọi từ file khác):**
  - ✅ Giữ nguyên cấu trúc Leanback của `tet2`.
  - ✅ Kiểm tra mọi nơi gọi `resolveCatalogCardMetrics` / `cardImageMemoryCacheKey` (từ `ModernHomeModels.kt` auto-merged) để đảm bảo chữ ký hàm khớp 100% khi biên dịch.

#### 📄 5. `app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeRowsList.kt`
- **Xung đột:**
  - `tet2`: Tích hợp `ModernInteropHorizontalGridView`, `HomeImageLoadingProfile` vertical/horizontal prefetch, throttle chủ động kích hoạt lazy catalog, quản lý focus Leanback.
  - `origin/dev`: Đổi key `LaunchedEffect` từ `effectiveExpandEnabled` sang `alwaysShowLandscapeClearlogo` và đổi cách tạo `cacheKey` dùng `cardImageMemoryCacheKey(...)` trong vòng lặp vertical prefetch của Compose.
- **Giải pháp (Giữ `tet2` + Kiểm tra đồng bộ cacheKey):**
  - ✅ Giữ toàn bộ cơ chế Leanback Interop, throttle lazy catalog và `HomeImageLoadingProfile` của `tet2`.
  - ✅ Kiểm tra lời gọi `resolveCatalogCardMetrics` (nếu `ModernHomeModels.kt` auto-merged đã bỏ tham số `expandEnabled`) để cập nhật cho khớp chữ ký biên dịch và đồng bộ cache key với `LeanbackArtworkLoader`.

#### 📄 6. `app/src/main/java/com/nuvio/tv/ui/screens/settings/AdvancedSettingsViewModel.kt`
- **Xung đột:**
  - `tet2`: Thêm `animationsEnabled` (`ThemeDataStore`), `gpuOffscreenCompositingEnabled` (`LayoutPreferenceDataStore`), kèm event `SetAnimationsEnabled`, `SetGpuOffscreenCompositingEnabled`.
  - `origin/dev`: Thêm `hardwareBitmapsEnabled` (`ImagePerformancePreferences`), event `SetHardwareBitmapsEnabled` (xóa memory cache khi đổi) và inject `@ApplicationContext appContext`.
- **Giải pháp (Hòa giải gộp 100%):**
  - ✅ Gộp đủ cả 3 trường state (`animationsEnabled`, `gpuOffscreenCompositingEnabled`, `hardwareBitmapsEnabled`), đủ constructor dependencies và xử lý đủ tất cả events.

---

### Cụm B: Trailer & Mạng Trình Phát (2 tệp)

#### 📄 7. `app/src/main/java/com/nuvio/tv/ui/components/TrailerPlayer.kt`
- **Xung đột:**
  - `tet2`: Bỏ `AnimatedVisibility` và thay `animateFloatAsState` bằng `val playerAlpha = if (isPlaying && hasRenderedFirstFrame) 1f else 0f` để không giật khung hình trên Android TV.
  - `origin/dev`: Thêm phụ đề cho trailer (`buildTrailerMediaItem`, `subtitleTrack`, `subtitleStyle`, `subtitleTypeface`, cấu hình `SubtitleView` trong `PlayerView` + gắn phụ đề ngay lần tải đầu PR #4741).
- **Giải pháp (Hòa giải — Giữ tối ưu `tet2` + Nhận Trailer Subtitles từ `dev`):**
  - ✅ Tiếp nhận trọn vẹn `buildTrailerMediaItem`, `subtitleTrack`, `subtitleStyle`, `subtitleTypeface` và cấu hình `SubtitleView` của `dev`.
  - ✅ Giữ nguyên tối ưu hiệu năng của `tet2`: bỏ `AnimatedVisibility` (dùng `if (trailerPlayer != null && isPlaying)`) và dùng `playerAlpha` trực tiếp không animation.

#### 📄 8. `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerPlaybackNetworking.kt`
- **Xung đột:**
  - `tet2`: Có `IptvHeaderProvider.mergeWithDefaults(streamUrl, ...)`, tối ưu interceptor lọc từng header (`Authorization`, `Referer`, `User-Agent`, `Origin`, `customHeaders`), tham số `streamUrl` cho `createHttpDataSourceFactory`/`createDataSourceFactory`, và `canReusePrewarmFor(...)` (kiểm tra `extensionCookieJar`).
  - `origin/dev`: Thêm `http2Hosts` và `Http2On403Interceptor` — tự động thử lại 1 lần qua HTTP/2 nếu stream mang `proxyHeaders` bị HTTP `403`.
- **Giải pháp (Hòa giải gộp 100%):**
  - ✅ Giữ toàn bộ `IptvHeaderProvider`, tối ưu header interceptor, chữ ký `streamUrl`, `canReusePrewarmFor` của `tet2`.
  - ✅ Bổ sung `http2Hosts` và `Http2On403Interceptor` từ `dev` ở cuối `createHttpClient(...)` (áp dụng khi `proxyHeaders.isNotEmpty()`). Đảm bảo `ProxyHeadersProtocolTest` của `dev` pass 100%.

---

### Cụm C: TV Drag Reorder, Addons, Collections, Cast, MDBList & Scroll Indicators (13 tệp)

#### 📄 9. `app/src/main/java/com/nuvio/tv/ui/screens/addon/AddonManagerScreen.kt`
- **Xung đột:**
  - `tet2`: Thêm trạng thái `isRefreshing` và hiệu ứng xoay vòng (`infiniteTransition` + `rotationZ`) cho `RefreshAddonsEntryCard`, chỉ đổi `refreshAddonsSubtitle` sau khi refresh hoàn tất thành công.
  - `origin/dev`: Tích hợp `TvReorderList` kéo thả sắp xếp danh sách Addon, ẩn URL addon cho gọn, sửa focus restore sau khi xóa addon (`AddonManagerFocusRestoreTest`).
- **Giải pháp (Hòa giải — Code `dev` tốt hơn cho danh sách + Giữ animation refresh của `tet2`):**
  - ✅ Tiếp nhận toàn bộ phần sắp xếp `TvReorderList`, ẩn URL và focus restore từ `dev` (trải nghiệm vượt trội hơn nút lên/xuống cũ).
  - ✅ Giữ nguyên nâng cấp `RefreshAddonsEntryCard` có hiệu ứng xoay và `LaunchedEffect(uiState.isRefreshingAddons)` từ `tet2`.

#### 📄 10. `app/src/main/java/com/nuvio/tv/ui/screens/addon/CatalogOrderScreen.kt`
- **Xung đột:** `tet2` chỉ xóa 3 dòng import thừa; `origin/dev` nâng cấp toàn diện lên `TvReorderList` kéo thả + ghim đầu trang + Follow addons.
- **Giải pháp (Thay thế bằng code mới của `dev` — Báo cáo rõ):**
  - ✅ **Tiếp nhận trọn vẹn bản `origin/dev`** (không mất bất kỳ tính năng nào của `tet2` vì `tet2` chỉ xóa import thừa, trong khi bản `dev` nâng cấp lớn UX kéo thả trên TV).

#### 📄 11. `app/src/main/java/com/nuvio/tv/ui/screens/settings/MDBListSettingsScreen.kt`
- **Xung đột:** `tet2` chỉ xóa 1 dòng import thừa; `origin/dev` nâng cấp toàn diện danh sách xếp hạng MDBList lên `TvReorderList` kéo thả + sửa focus ring nút move.
- **Giải pháp (Thay thế bằng code mới của `dev` — Báo cáo rõ):**
  - ✅ **Tiếp nhận trọn vẹn bản `origin/dev`** (không mất tùy chỉnh nào của `tet2`, đồng bộ hoàn toàn với `MDBListSettingsViewModel` và `MDBListSettingsViewModelTest`).

#### 📄 12–18. Nhóm 7 tệp Collection Editor & Management
*(Gồm: `CollectionManagementScreen.kt`, `CollectionEditorScreen.kt`, `CollectionEditorControls.kt`, `CollectionEditorFolderContent.kt`, `CollectionEditorCatalogPicker.kt`, `CollectionEditorGenreEmojiPickers.kt`, `CollectionEditorTmdbPicker.kt`)*
- **Xung đột:**
  - Qua kiểm tra `git diff $base..tet2`, trên `tet2` **CẢ 7 TỆP NÀY CHỈ XÓA CÁC DÒNG IMPORT THỪA** (`0 insertions`, chỉ xóa unused imports!).
  - Trên `origin/dev`: Nâng cấp toàn diện bằng `TvReorderList` kéo thả, xác nhận khi có thay đổi chưa lưu (`unsaved changes`), tối ưu điều hướng focus và trap ngang (`CollectionFocusAndScrollTest`, `CollectionEditorViewModel*Test`).
- **Giải pháp (Thay thế bằng code mới của `dev` — Báo cáo rõ):**
  - ✅ **Tiếp nhận trọn vẹn cả 7 tệp từ `origin/dev`** (không có bất kỳ logic tùy chỉnh nào của `tet2` bị mất, đạt tương thích 100% với ViewModel và unit tests mới của `dev`).

#### 📄 19. `app/src/main/java/com/nuvio/tv/ui/screens/cast/CastDetailScreen.kt`
- **Xung đột:**
  - `tet2`: Chuyển `FilmographyRow` sang `LeanbackNativeRow` tốc độ cao, kích thước thẻ landscape `260.dp x 146.dp`, thêm `portraitFocusRequester` và điều hướng D-Pad Lên/Xuống mượt giữa `HeroSection` và `FilmographyRow`.
  - `origin/dev`: Thêm bộ lọc `FilmographyFilterTabs` (**Tất cả / Phim lẻ / Phim bộ** — PR #4702) kèm `key(filmographyFilter)` và `filmographyUpFocusRequester` để bấm D-Pad Lên từ hàng phim nhảy vào tab lọc.
- **Giải pháp (Hòa giải gộp 100% ưu điểm cả hai):**
  - ✅ Tiếp nhận tính năng mới `FilmographyFilterTabs` (`allCredits`, `movieCredits`, `seriesCredits`, `visibleCredits`) từ `dev`.
  - ✅ Giữ nguyên `LeanbackNativeRow` tốc độ cao của `tet2` bên trong `key(filmographyFilter)`.
  - ✅ Nối mạch D-Pad chuẩn: bấm **Xuống** từ `HeroSection` → nếu có `showFilmographyFilter` thì nhảy vào tab đang chọn (`filmographyUpFocusRequester`), ngược lại nhảy thẳng xuống `firstPosterFocusRequester`; bấm **Lên** từ `LeanbackNativeRow` → nếu có `showFilmographyFilter` nhảy lên tab lọc, ngược lại nhảy lên `portraitFocusRequester` (khi tiểu sử mở rộng/bị cắt).

#### 📄 20. `app/src/main/java/com/nuvio/tv/ui/screens/collection/FolderDetailScreen.kt`
- **Xung đột:**
  - `tet2`: Chuyển lưới `TabbedGridContent` từ Compose `LazyVerticalGrid` + `GridContentCard` sang `LeanbackNativeGrid` (4 cột landscape / 6 cột portrait) hiệu năng cao.
  - `origin/dev`: Thêm handoff trailer từ folder sang trang chi tiết (PR #4738) + sửa giữ vị trí cuộn & khôi phục focus theo từng tab (`tabFocusState`, `onSaveTabFocusState`, `FollowLayoutContent`).
- **Giải pháp (Hòa giải — Giữ LeanbackNativeGrid của `tet2` + Nhận Trailer Handoff & Tab Focus State của `dev`):**
  - ✅ Giữ nguyên `LeanbackNativeGrid` của `tet2` trong `TabbedGridContent`.
  - ✅ Tích hợp phần nối tiếp trailer (PR #4738) và truyền đầy đủ `tabFocusState` + `onSaveTabFocusState` vào `FollowLayoutContent` để không mất vị trí focus khi chuyển tab hay quay lại từ Details.

#### 📄 21. `app/src/main/java/com/nuvio/tv/ui/screens/settings/SettingsScrollIndicators.kt`
- **Xung đột:**
  - `tet2`: Bỏ `AnimatedVisibility` (fadeIn/fadeOut) chuyển sang `if (state.canScrollBackward) Icon(...)` để giảm chi phí recomposition/animation trên Android TV yếu.
  - `origin/dev`: Thêm 4 tham số tùy chọn `suppressBackward`, `suppressForward`, `forceBackward`, `forceForward` vào `SettingsVerticalScrollIndicators` phục vụ cho `TvReorderList`.
- **Giải pháp (Hòa giải gộp 100%):**
  - ✅ Thêm đủ 4 tham số từ `dev` với biểu thức điều kiện `(state.canScrollBackward || forceBackward) && !suppressBackward` và `(state.canScrollForward || forceForward) && !suppressForward`.
  - ✅ Giữ cách render trực tiếp không `AnimatedVisibility` nhẹ nhàng của `tet2`.

---

### Cụm D: Tài Nguyên Đa Ngôn Ngữ (1 tệp conflict + 1 tệp cần rà soát)

#### 📄 22. `app/src/main/res/values/strings.xml` *(và rà soát `values-vi/strings.xml`)*
- **Xung đột:**
  - `tet2`: Có toàn bộ chuỗi tài nguyên cho Google TTS Offline (`tts_*`, `player_tts_*`), Live TV & EPG, TorrServer, Custom Fonts, Animations/GPU Offscreen Compositing.
  - `origin/dev`: Thêm chuỗi cho `TvReorderList` (`catalog_order_pinned*`, `cd_reorder`, `collections_pinned`, `collections_editor_unsaved_*`), `cast_detail_filter_all`, `plugin_repo_delete_confirm_*`, `autosync_status_*`, `layout_trailer_subtitles*`, `advanced_hardware_bitmaps*`.
- **Giải pháp:**
  - ✅ Gộp **100% tất cả các key của cả hai phía** trong `values/strings.xml` (đảm bảo không trùng key, hợp lệ XML).
  - ✅ Kiểm tra `values-vi/strings.xml` sau auto-merge: giữ toàn bộ chuỗi tiếng Việt của `tet2` (TTS, Live TV, TorrServer, Fonts...), giữ bản dịch cập nhật từ PR #4722 của `dev`, và **dịch bổ sung tiếng Việt** cho các key mới của `dev` (Hardware Bitmaps, Trailer Subtitles, Cast Filter All, Plugin Delete Confirm, Unsaved Collection Dialog, Reorder, AutoSync Status) để giao diện tiếng Việt đồng nhất 100%.

---

## 5. Rà Soát Rủi Ro Semantic Conflict Trong 154 Tệp Auto-Merged

Các tệp sau được Git auto-merge sạch nhưng cần kiểm tra thủ công sau khi merge để phòng lỗi biên dịch hoặc hành vi ngầm:

1. **Tương tác `AudioSync` (PR #4150) với `Google TTS Offline` (`tet2`):**
   - `PlayerRuntimeControllerInitialization.kt`: `AudioSyncTaps.wrapAudioSink(baseAudioSink)` bọc trước `PlaybackSpeedAwareAudioSink` → không ảnh hưởng `PlayerTtsVolumeDuckingPolicy` hay `SubtitleTtsCoordinator`.
   - `PlayerRuntimeControllerLifecycle.kt`: Đảm bảo gọi cả `AudioSyncFallback.release(this)` lẫn `subtitleTtsCoordinator.stopSession(token)`.
   - `NuvioMpvSurfaceView.kt`: Kiểm tra các method mới của `AudioSync` không làm ảnh hưởng `applyAudioOutputLevel`, `setTtsHideSubtitles` và `getSubtitleCueTimingContext`.
2. **Tương tác `SettingsStructureTest` & `SettingsIconsTest`:**
   - `dev` thêm `audioSyncSettingsItems` vào `PlaybackSubtitleSettings.kt`, `layout_trailer_subtitles` vào `LayoutDetailSettings.kt`, và `advanced_hardware_bitmaps` vào `NetworkSettingsScreen.kt` (không thay đổi enum `PlaybackSection`). Cần chạy `SettingsStructureTest` và `SettingsIconsTest` để xác nhận pass 100%.
3. **Tương tác `HomeViewModelCatalogPipeline.kt`:**
   - `dev` thêm `currentHeroCatalogKeys = layoutPreferenceDataStore.heroCatalogSelections.first()` đầu `loadAllCatalogsPipeline` → kiểm tra không gây chặn/trễ bất thường với cơ chế drip-load & eager catalog của `tet2`.
4. **Tương tác `AndroidManifest.xml`:**
   - Auto-merge thêm `<uses-feature android:name="android.hardware.wifi" android:required="false" />` — kiểm tra vẫn giữ nguyên `<queries><intent><action android:name="android.intent.action.TTS_SERVICE" /></intent></queries>` của `tet2`.
5. **Tương tác `ModernHomeModels.kt` vs `ModernHomeRows.kt` / `ModernHomeRowsList.kt`:**
   - Kiểm tra chữ ký `resolveCatalogCardMetrics` và `cardImageMemoryCacheKey` sau khi `ModernHomeModels.kt` auto-merge.

---

## 6. Lộ Trình Thực Thi Từng Bước (Phase-by-Phase Execution)

### Phase 1: Sao Lưu & Khởi Chạy Merge An Toàn
1. Kiểm tra `git status` đảm bảo working tree sạch.
2. Tạo nhánh sao lưu an toàn trước khi thao tác:
   ```powershell
   git branch backup-tet2-before-merge-20261121
   ```
3. Cập nhật nhánh local `dev` fast-forward lên `origin/dev` (`d2eeb3d0e`):
   ```powershell
   git fetch origin dev:dev
   ```
4. Bắt đầu merge vào `tet2`:
   ```powershell
   git merge --no-ff origin/dev
   ```

### Phase 2: Giải Quyết Xung Đột Cụm A & B (Build, Application, Home, Trailer, Networking)
1. Xử lý `app/build.gradle.kts`, `NuvioApplication.kt`, `git rm ContentCard.kt`.
2. Xử lý `ModernHomeRows.kt`, `ModernHomeRowsList.kt`, `AdvancedSettingsViewModel.kt`.
3. Xử lý `TrailerPlayer.kt`, `PlayerPlaybackNetworking.kt`.

### Phase 3: Giải Quyết Xung Đột Cụm C & D (TV Reorder, Addons, Collections, Cast, Folder, Strings)
1. Tiếp nhận bản `origin/dev` cho 9 tệp không có logic custom trên `tet2`: `CatalogOrderScreen.kt`, `MDBListSettingsScreen.kt`, và 7 tệp `CollectionManagement/Editor*.kt`.
2. Hòa giải chi tiết 4 tệp có tùy chỉnh `tet2`: `AddonManagerScreen.kt`, `CastDetailScreen.kt`, `FolderDetailScreen.kt`, `SettingsScrollIndicators.kt`.
3. Hòa giải `values/strings.xml` và bổ sung bản dịch tiếng Việt đầy đủ trong `values-vi/strings.xml`.

### Phase 4: Rà Soát Semantic Conflicts & Hoàn Tất Merge
1. Kiểm tra các tệp auto-merged trọng yếu (`AndroidManifest.xml`, `NuvioMpvSurfaceView.kt`, `PlayerRuntimeController*.kt`, `HomeViewModelCatalogPipeline.kt`, `ModernHomeModels.kt`).
2. Biên dịch kiểm tra toàn bộ dự án (`compileFullDebugKotlin` + `compileFullDebugUnitTestKotlin`).

### Phase 5: Kiểm Chứng Tự Động & Thủ Công (Verification Plan)
1. **Kiểm chứng biên dịch & Unit Tests tự động:**
   ```powershell
   .\gradlew :app:compileFullDebugKotlin :app:testFullDebugUnitTest --tests "com.nuvio.tv.core.tts.*" --tests "com.nuvio.tv.ui.screens.player.PlayerTtsVolumeDuckingPolicyTest" --tests "com.nuvio.tv.ui.screens.player.ProxyHeadersProtocolTest" --tests "com.nuvio.tv.ui.screens.player.PlayerStreamRouteStateTest" --tests "com.nuvio.tv.ui.screens.player.audiosync.*" --tests "com.nuvio.tv.ui.screens.player.autosync.*" --tests "com.nuvio.tv.ui.components.TvReorderListLayoutTest" --tests "com.nuvio.tv.ui.screens.addon.*" --tests "com.nuvio.tv.ui.screens.collection.*" --tests "com.nuvio.tv.ui.screens.home.ContinueWatchingUpcomingSortTest" --tests "com.nuvio.tv.ui.screens.settings.*" --tests "com.nuvio.tv.data.trailer.TrailerSubtitleTest" --tests "com.nuvio.tv.LaunchIntentReplayTest"
   ```
   *(Lưu ý: Lần đầu chạy Gradle sẽ thực thi task `downloadAudioSyncRuntime` tải `sherpa-onnx-1.13.8.aar` từ GitHub).*
2. **Checklist kiểm chứng thủ công trên Android TV sau khi build:**
   - [ ] **Trang chủ & Poster:** Leanback Modern Home mượt, poster tải nhanh, không crash trên Android <= 11 khi bật/tắt Hardware Bitmaps trong Cài đặt Nâng cao.
   - [ ] **Google TTS Offline:** Mở bảng Thuyết minh trên Player không mất focus, chọn giọng chỉ hiện đúng ngôn ngữ phụ đề ưu tiên, đọc phụ đề + chờ đọc xong câu phản hồi nhanh.
   - [ ] **Live TV & EPG:** Phát kênh IPTV ổn định (`backBufferMs = 0`), header tự động qua `IptvHeaderProvider`, Web Config Server hoạt động bình thường.
   - [ ] **TV Drag Reorder:** Kéo thả sắp xếp được trên Addon Manager (giữ icon Refresh xoay), Catalog Order, Collections, MDBList.
   - [ ] **Cast & Folder:** Tab lọc Phim lẻ/Phim bộ trên CastDetail điều hướng D-Pad mượt qua `LeanbackNativeRow`; FolderDetail dùng `LeanbackNativeGrid` và giữ đúng focus khi quay lại.
   - [ ] **Trailer Subtitles & Player:** Trailer hiện phụ đề, không giật alpha; đổi nguồn giữ nguyên vị trí đang xem; Audio Sync hoạt động độc lập với TTS.
3. **Hoàn tất Merge Commit** với thông điệp đầy đủ và báo cáo chi tiết từng tệp thay đổi/thay thế.
