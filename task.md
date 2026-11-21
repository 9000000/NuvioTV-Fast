# Danh Sách Task - Đồng Bộ Commit Mới Từ Nhánh `dev` Sang `tet2` (19/11/2026 – 21/11/2026)

> **Kế hoạch tương ứng**: [PLAN-sync-dev-2026-11-21.md](file:///f:/MyGithub/NuvioTV-Fast/PLAN-sync-dev-2026-11-21.md)
> **Phạm vi**: 155 commits (22 PR/Merge từ `15286b68c..d2eeb3d0e`) · **Xung đột trực tiếp**: 22 files · **Bảo toàn 100% tùy chỉnh `tet2`**

---

## 📋 Task Breakdown

### Phase 1: Sao Lưu & Khởi Chạy Merge An Toàn
- [x] **SYNC21-01**: Tạo nhánh sao lưu `backup-tet2-before-merge-20261121`, cập nhật local `dev` lên `d2eeb3d0e` và khởi chạy `git merge --no-ff origin/dev`
  - **Vai trò**: Git / Release Engineer
  - **Mô tả**: Kiểm tra working tree sạch, tạo backup branch, fast-forward local `dev`, chạy merge để liệt kê đúng 22 tệp xung đột.

### Phase 2: Giải Quyết Xung Đột Build, Image Performance, Home & Player Networking
- [x] **SYNC21-02**: Hòa giải `app/build.gradle.kts`, `NuvioApplication.kt`, `AdvancedSettingsViewModel.kt` và xử lý modify/delete `ContentCard.kt`
  - **Vai trò**: Core / Build & Performance Engineer
  - **Mô tả**:
    1. Giữ nguyên toàn bộ official URLs, APK naming, applicationId, version, dependencies của `tet2` trong `build.gradle.kts`; bổ sung `downloadAudioSyncRuntime` (`sherpa-onnx-1.13.8.aar`), `jniLibs.excludes` và `kotlin("test")` từ `dev`.
    2. Gắn `HardwareBitmapSettingInterceptor` từ `dev` vào `NuvioApplication.kt` trong khi giữ trọn kiến trúc `HomeImageLoadingProfile`, `ArtworkMemoryCache`, `sizedHomeArtworkUrl`, `ArcDns`, `LiveTvStorage` của `tet2`.
    3. Gộp `hardwareBitmapsEnabled` với `animationsEnabled` & `gpuOffscreenCompositingEnabled` trong `AdvancedSettingsViewModel.kt`.
    4. Xác nhận xóa `ContentCard.kt` (`git rm`).
  - **File**: [build.gradle.kts](file:///f:/MyGithub/NuvioTV-Fast/app/build.gradle.kts), [NuvioApplication.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/NuvioApplication.kt), [AdvancedSettingsViewModel.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/settings/AdvancedSettingsViewModel.kt)

- [x] **SYNC21-03**: Hòa giải `ModernHomeRows.kt` & `ModernHomeRowsList.kt`
  - **Vai trò**: Home Leanback UI Engineer
  - **Mô tả**: Giữ trọn kiến trúc Leanback (`LeanbackModernRow`, `ModernInteropHorizontalGridView`, `HomeImageLoadingProfile`, throttle lazy catalog) của `tet2`; đồng bộ chữ ký `resolveCatalogCardMetrics` và `cardImageMemoryCacheKey` với `ModernHomeModels.kt` từ `dev`.
  - **File**: [ModernHomeRows.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeRows.kt), [ModernHomeRowsList.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeRowsList.kt)

- [x] **SYNC21-04**: Hòa giải `TrailerPlayer.kt` & `PlayerPlaybackNetworking.kt`
  - **Vai trò**: Player & Networking Engineer
  - **Mô tả**:
    1. Tích hợp Trailer Subtitles (`buildTrailerMediaItem`, `subtitleTrack`, `subtitleStyle`, `SubtitleView`) từ `dev` vào `TrailerPlayer.kt`, giữ nguyên tối ưu bỏ `AnimatedVisibility` và dùng `playerAlpha` trực tiếp của `tet2`.
    2. Tích hợp `http2Hosts` + `Http2On403Interceptor` từ `dev` vào `PlayerPlaybackNetworking.kt`, giữ nguyên `IptvHeaderProvider`, tối ưu header interceptor, tham số `streamUrl` và `canReusePrewarmFor` của `tet2`.
  - **File**: [TrailerPlayer.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/components/TrailerPlayer.kt), [PlayerPlaybackNetworking.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerPlaybackNetworking.kt)

### Phase 3: Giải Quyết Xung Đột TV Drag Reorder, Cast/Folder Leanback & Đa Ngôn Ngữ
- [x] **SYNC21-05**: Tiếp nhận `TvReorderList` từ `origin/dev` cho 9 tệp không có logic custom (`CatalogOrderScreen`, `MDBListSettingsScreen`, 7 tệp `CollectionManagement/Editor*`) & hòa giải `AddonManagerScreen`, `SettingsScrollIndicators`
  - **Vai trò**: Compose TV UI Engineer
  - **Mô tả**:
    1. Tiếp nhận bản `origin/dev` cho 9 tệp chỉ xóa unused imports trên `tet2`.
    2. Hòa giải `AddonManagerScreen.kt`: nhận `TvReorderList` + ẩn URL + focus restore từ `dev`, giữ nguyên animation xoay icon `RefreshAddonsEntryCard` của `tet2`.
    3. Hòa giải `SettingsScrollIndicators.kt`: nhận 4 tham số `suppress/force` từ `dev`, giữ bỏ `AnimatedVisibility` của `tet2`.
  - **File**: [AddonManagerScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/addon/AddonManagerScreen.kt), [CatalogOrderScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/addon/CatalogOrderScreen.kt), [MDBListSettingsScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/settings/MDBListSettingsScreen.kt), [SettingsScrollIndicators.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/settings/SettingsScrollIndicators.kt) và 7 tệp `ui/screens/collection/Collection*.kt`

- [x] **SYNC21-06**: Hòa giải `CastDetailScreen.kt` & `FolderDetailScreen.kt`
  - **Vai trò**: Detail & Leanback Navigation Engineer
  - **Mô tả**:
    1. Trong `CastDetailScreen.kt`: tích hợp `FilmographyFilterTabs` (Tất cả / Phim lẻ / Phim bộ) từ `dev`, giữ `LeanbackNativeRow` + điều hướng D-Pad mượt giữa Hero, Filter Tabs và Filmography của `tet2`.
    2. Trong `FolderDetailScreen.kt`: tích hợp trailer handoff và `tabFocusState`/`onSaveTabFocusState` từ `dev`, giữ `LeanbackNativeGrid` của `tet2`.
  - **File**: [CastDetailScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/cast/CastDetailScreen.kt), [FolderDetailScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/collection/FolderDetailScreen.kt)

- [x] **SYNC21-07**: Hòa giải `values/strings.xml` & đồng bộ bản dịch `values-vi/strings.xml`
  - **Vai trò**: Localization Engineer
  - **Mô tả**: Gộp 100% keys của `tet2` (TTS, Live TV, TorrServer, Fonts...) và `dev` (Reorder, Audio Sync, Hardware Bitmaps, Trailer Subtitles, Cast Filter, Plugin Delete Confirm), dịch bổ sung đầy đủ tiếng Việt trong `values-vi/strings.xml` và `values-vi/audio_sync_strings.xml`.
  - **File**: [strings.xml](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/res/values/strings.xml), [strings.xml (vi)](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/res/values-vi/strings.xml), [audio_sync_strings.xml (vi)](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/res/values-vi/audio_sync_strings.xml)

### Phase 4: Rà Soát Semantic Conflicts, Biên Dịch & Kiểm Thử Toàn Diện
- [x] **SYNC21-08**: Rà soát các tệp auto-merged nhạy cảm, chạy `compileFullDebugKotlin` + toàn bộ Unit Tests (TTS, AudioSync, ProxyHeaders, Reorder, Settings, Trailers, RouteState) và tạo Merge Commit kèm báo cáo chi tiết
  - **Vai trò**: QA / Verification Engineer
  - **Mô tả**: Xác minh tương tác AudioSync vs Google TTS, `AndroidManifest.xml`, `NuvioMpvSurfaceView.kt`, `HomeViewModelCatalogPipeline.kt`; đảm bảo 100% compile & tests pass trước khi hoàn tất commit merge.

---

# Danh Sách Task - Tích Hợp Chức Năng Đọc Phụ Đề Bằng Google TTS Offline

> **Kế hoạch tương ứng**: [PLAN-subtitle-google-tts-offline.md](file:///f:/MyGithub/NuvioTV-Fast/PLAN-subtitle-google-tts-offline.md)
> **Tham khảo**: `NuvioMobile-Fast` (đã đối chiếu tới commit `802080dd2`) · **Phạm vi**: Chỉ Google TTS Offline (không Online TTS, không Piper ONNX)

---

## 📋 Task Breakdown

### Phase 1: Quyền Hệ Thống, Profile DataStore & Google TTS Offline + Bộ Căn Nhịp
- [x] **TTS-01**: Khai báo `<queries>` `TTS_SERVICE` trong `AndroidManifest.xml` & mở rộng `PlayerSettings` / `PlayerSettingsDataStore`
  - **Vai trò**: Core / Storage Engineer
  - **Mô tả**:
    1. Thêm `<intent><action android:name="android.intent.action.TTS_SERVICE" /></intent>` vào `<queries>` trong `AndroidManifest.xml` cho Android 11+.
    2. Bổ sung 11 trường cấu hình TTS (`ttsEnabled`, `ttsVoiceName`, `ttsAllowNetworkVoiceFallback`, `ttsMovieVolume`, `ttsVoiceVolume`, `ttsBaseSpeed` [mặc định `1.0f`, dải `0.8f..2.0f`], `ttsPitch`, `ttsAutoSpeedEnabled`, `ttsMaxAutoSpeed` [mặc định `2.1f`, dải `1.4f..3.0f`], `ttsWaitForCueCompletion`, `ttsHideSubtitlesWhenEnabled`) cùng Preferences keys & setters theo Profile vào `PlayerSettingsDataStore.kt`.
  - **File**: [AndroidManifest.xml](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/AndroidManifest.xml), [PlayerSettingsDataStore.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/data/local/PlayerSettingsDataStore.kt)
  - **Trạng thái**: Completed

- [x] **TTS-02**: Xây dựng Core TTS (`SubtitleTtsModels`, `SubtitleTtsTextCleaner`, `SubtitleTtsTimingCalculator`, `GoogleOfflineTtsVoiceSelector`, `GoogleOfflineTtsEngine`, `SubtitleTtsCueQueueState`, `SubtitleTtsCoordinator`)
  - **Vai trò**: Audio / TTS Core Engineer
  - **Mô tả**:
    1. Port `SubtitleTtsTextCleaner` (lọc ASS/HTML/SDH/nhạc + `ensureSentenceEndingPunctuation`) và `SubtitleTtsTimingCalculator` (tính `budgetMs`, ước lượng tốc độ theo từ/dấu câu, dải `maxAutoSpeed` tới `3.0f`).
    2. Xây dựng `GoogleOfflineTtsVoiceSelector` lọc giọng Offline (`!isNetworkConnectionRequired`), ưu tiên `vi-VN`/`vi`.
    3. Xây dựng `GoogleOfflineTtsEngine` (`@Singleton`) dùng `TextToSpeech(..., "com.google.android.tts")`, `AudioAttributes(USAGE_MEDIA, CONTENT_TYPE_SPEECH)`, `UtteranceProgressListener` theo `utteranceId` (phân biệt `onDone` vs `onStop`), hỗ trợ tốc độ tới `3.0f`, mở cài đặt tải giọng Offline (`ACTION_INSTALL_TTS_DATA` / `TTS_SETTINGS`).
    4. Xây dựng `SubtitleTtsCueQueueState` & `SubtitleTtsCoordinator` (`@Singleton`) hỗ trợ `waitForCueCompletion` (carry-over `1500ms`, stale `2500ms`), lọc trùng `(cleanText, cueStartMs)`, `previewVoice`, `onPlaybackPaused`, `onSeek`, `stopSession`.
  - **File**: `app/src/main/java/com/nuvio/tv/core/tts/SubtitleTtsModels.kt`, `SubtitleTtsTextCleaner.kt`, `SubtitleTtsTimingCalculator.kt`, `GoogleOfflineTtsVoiceSelector.kt`, `GoogleOfflineTtsEngine.kt`, `SubtitleTtsCueQueueState.kt`, `SubtitleTtsCoordinator.kt`
  - **Trạng thái**: Completed

### Phase 2: Tích Hợp PlayerRuntimeController (ExoPlayer, Libass, MPV, Timeline & Ducking)
- [x] **TTS-03**: Mở rộng `PlayerUiState` / `PlayerEvent` & cơ chế Ducking âm lượng 2 kênh (tương thích `audioAmplificationDb`)
  - **Vai trò**: Player Engine Engineer
  - **Mô tả**:
    1. Thêm `ttsConfig`, `ttsAvailability`, `ttsVoices`, `showTtsOverlay`, `isDirectPassthroughActive`, `shouldHideVisualSubtitles` và đầy đủ `PlayerEvent.On...Tts...` (gồm `ttsMaxAutoSpeed`, `ttsWaitForCueCompletion`, `ttsAllowNetworkFallback`) vào `PlayerUiState.kt`.
    2. Nâng cấp `NuvioMpvSurfaceView.applyAudioOutputLevel(amplificationDb, volumeScale)` và tạo `PlayerRuntimeController.applyEffectiveMovieVolume()` để kết hợp `movieVolume` với `audioAmplificationDb` trên cả ExoPlayer và MPV, đồng thời phát hiện trạng thái HDMI Passthrough.
  - **File**: [PlayerUiState.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerUiState.kt), [NuvioMpvSurfaceView.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/NuvioMpvSurfaceView.kt), [PlayerRuntimeControllerObservers.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerObservers.kt), [PlayerRuntimeControllerPlaybackEvents.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt)
  - **Trạng thái**: Completed

- [x] **TTS-04**: Trích xuất Timeline Phụ đề cho `SubtitleCueTracker` & ẩn phụ đề hiển thị trên cả ExoPlayer, StyledOverlay, Libass, MPV
  - **Vai trò**: Subtitle & Rendering Engineer
  - **Mô tả**:
    1. Mở rộng `SubtitleCueState` mang thêm `SubtitleCuePlaybackContext?` từ: Sidecar (`PlayerSidecarSubtitles.kt`), Embedded/ASS timeline (`EmbeddedSubtitleTimelineLoader` / `PlayerSubtitleCueParser`), và MPV (`sub-start`, `sub-end`, `time-pos`, `sub-delay`, `speed`) ngay trong `syncMpvStyledSubtitles()`.
    2. Quan sát `subtitleCueTracker.state` trong `PlayerRuntimeController` để đẩy cue + timingContext sang `SubtitleTtsCoordinator`.
    3. Ẩn hiển thị phụ đề (`SubtitleView`, `StyledSubtitleOverlay`, `AssSubtitleSurfaceView` / `AssVideoEffectAsync`, `mpv sub-visibility`) khi `shouldHideVisualSubtitles == true` mà vẫn giữ nguyên luồng text cho TTS.
    4. Gắn hook dừng TTS khi Pause, Seek, đổi phụ đề và `releasePlayer()`.
  - **File**: [SubtitleCueTracker.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/SubtitleCueTracker.kt), [PlayerSidecarSubtitles.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerSidecarSubtitles.kt), [PlayerRuntimeControllerStyledSubtitles.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerStyledSubtitles.kt), [PlayerRuntimeControllerInitialization.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt), [PlayerRuntimeControllerMpv.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerMpv.kt), [PlayerRuntimeControllerLifecycle.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerLifecycle.kt), [PlayerScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerScreen.kt)
  - **Trạng thái**: Completed

### Phase 3: Giao Diện Android TV (Player Overlay & Cài Đặt Âm Thanh)
- [x] **TTS-05**: Tạo `SubtitleTtsOverlay.kt` (D-Pad 2 cột) & thêm nút Thuyết minh trên `PlayerControlsOverlay`
  - **Vai trò**: Compose TV / Focus Engineer
  - **Mô tả**:
    1. Thêm `ControlButton` **Thuyết minh** (`RecordVoiceOver`) trên thanh điều khiển `PlayerScreen.kt` cạnh nút Phụ đề/Âm thanh, đổi màu nhấn mạnh khi đang bật.
    2. Xây dựng `SubtitleTtsOverlay.kt` dùng `PlayerOverlayScaffold`: cột trái (Bật/Tắt, Nghe thử, Banner trạng thái + nút tải giọng, Danh sách giọng Offline) và cột phải (các stepper `- / +` D-Pad cho Âm lượng phim, Âm lượng giọng đọc, Tốc độ `0.8x–2.0x`, Cao độ `0.6x–1.5x`, Tự động căn nhịp, Tốc độ tối đa `1.4x–3.0x`, Chờ đọc hết câu, Ẩn phụ đề), thiết lập `focusProperties` rõ ràng giữa 2 cột.
  - **File**: `app/src/main/java/com/nuvio/tv/ui/screens/player/SubtitleTtsOverlay.kt`, [PlayerScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerScreen.kt)
  - **Trạng thái**: Completed

- [x] **TTS-06**: Tích hợp cấu hình TTS vào `Cài đặt ➔ Phát lại ➔ Âm thanh` (`PlaybackAudioSettings`) & Đa ngôn ngữ (`strings.xml`)
  - **Vai trò**: Settings UI & Localization Engineer
  - **Mô tả**:
    1. Đặt mục **Thuyết minh phụ đề (Google TTS Offline)** TRƯỚC dòng `if (!isExoEngine) return` trong `PlaybackAudioSettings.kt` (giữ nguyên `PlaybackSection` để pass `SettingsStructureTest`).
    2. Dùng `SettingsSectionLabel`, `SettingsToggleRow`, `SettingsActionRow`, `SliderSettingsItem` và `SettingsSingleChoiceDialog` (`PlaybackDialog.TTS_VOICE`) cho toàn bộ 11 cài đặt mới khớp với Mobile.
    3. Bổ sung đầy đủ chuỗi tài nguyên ở `res/values/strings.xml` và `res/values-vi/strings.xml`.
  - **File**: [PlaybackSettingsModel.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/settings/PlaybackSettingsModel.kt), [PlaybackSettingsViewModel.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/settings/PlaybackSettingsViewModel.kt), [PlaybackAudioSettings.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/settings/PlaybackAudioSettings.kt), [PlaybackSettingsScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/settings/PlaybackSettingsScreen.kt), [PlaybackSettingsSections.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/settings/PlaybackSettingsSections.kt), `res/values/strings.xml`, `res/values-vi/strings.xml`
  - **Trạng thái**: Completed

### Phase 4: Kiểm Chứng Tự Động & Thủ Công
- [x] **TTS-07**: Viết Unit Tests (`SubtitleTtsTextCleanerTest`, `SubtitleTtsTimingCalculatorTest`, `SubtitleTtsCueQueueStateTest`, `GoogleOfflineTtsVoiceSelectorTest`, `PlayerTtsVolumeDuckingPolicyTest`) & kiểm tra build/test toàn dự án
  - **Vai trò**: QA / Verification Engineer
  - **Mô tả**:
    1. Viết unit tests cho bộ lọc + dấu câu kết thúc, bộ tính ngân sách thời gian, hàng đợi `waitForCueCompletion`, bộ chọn giọng Google Offline (`vi-VN` priority), và công thức âm lượng kết hợp `audioAmplificationDb`.
    2. Chạy `.\gradlew :app:compileFullDebugKotlin :app:testFullDebugUnitTest` và thực hiện checklist test thủ công trên Android TV (ExoPlayer + MPV + Libass + chế độ Offline không Wi-Fi).
  - **File**: `app/src/test/java/com/nuvio/tv/core/tts/SubtitleTtsTextCleanerTest.kt`, `SubtitleTtsTimingCalculatorTest.kt`, `SubtitleTtsCueQueueStateTest.kt`, `GoogleOfflineTtsVoiceSelectorTest.kt`, `app/src/test/java/com/nuvio/tv/ui/screens/player/PlayerTtsVolumeDuckingPolicyTest.kt`
  - **Trạng thái**: Completed

---

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


---

# Danh Sách Task - Tối Ưu Tải Ảnh Khi Cuộn Dọc & Nạp Trước Poster Khi Di Chuyển Ngang

> **Kế hoạch tương ứng**: [PLAN-scroll-image-load-optimization.md](file:///f:/MyGithub/NuvioTV-Fast/PLAN-scroll-image-load-optimization.md)

- [x] **SCROLL-LOAD-01**: Bổ sung cơ chế `isHoldScrolling` trong `LeanbackArtworkLoader` để tạm dừng request mạng/giải mã ngoài RAM và bỏ qua prefetch khi cuộn lướt tốc độ cao
  - **Vai trò**: Image / Performance Engineer
  - **Mô tả**: Thêm cờ `@Volatile var isHoldScrolling: Boolean`. Trong `prefetch()`: nếu đang hold thì return ngay. Trong `loadIntoImageView()`: nếu có trong `findInMemoryCache` thì gán ngay lập tức (0ms); nếu chưa có trong RAM và `isHoldScrolling == true` thì hoãn request và ghi nhận trạng thái deferred để nạp lại khi dừng.
  - **File**: [LeanbackArtworkLoader.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/core/LeanbackArtworkLoader.kt), [LeanbackModernRowAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRowAdapter.kt)

- [x] **SCROLL-LOAD-02**: Tích hợp cờ hold scroll vào `ModernHomeRowsList` và phục hồi nạp ảnh tức thì khi nhả phím
  - **Vai trò**: UI / TV Navigation Engineer
  - **Mô tả**: Trong `startOrExtendVerticalHoldScroll`, kích hoạt `LeanbackArtworkLoader.isHoldScrolling = true` và bỏ qua vòng lặp coroutine `VerticalPrefetchSnapshot`. Trong `endVerticalHoldScroll`, trả cờ về `false` và kích hoạt tải/prefetch lại toàn bộ card đang hiển thị trên hàng dừng lại. Đảm bảo `stepVerticalRow` (bấm từng nấc nhanh) giữ nguyên `isHoldScrolling = false` và tải bình thường.
  - **File**: [ModernHomeRowsList.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeRowsList.kt)

- [x] **SCROLL-LOAD-03**: Kích hoạt tải thêm danh mục sớm (Eager Pagination) và mở rộng nạp trước ảnh poster ngang (Deep Horizontal Prefetch)
  - **Vai trò**: Leanback Grid / Catalog Pipeline Engineer
  - **Mô tả**:
    1. Trong `ModernInteropHorizontalGridView`: Nâng ngưỡng kích hoạt `onLoadMore` lên `target >= totalCount - 12` và gọi ngay trong `dispatchKeyEvent` khi bấm phím sang phải (`towardEndKey`) để tải trang tiếp theo ngầm từ sớm thay vì chờ cuộn tới cuối hàng.
    2. Trong `HomePosterLoading.kt`: Nâng `horizontalPrefetchAhead` lên 8 (Low RAM) và 12 (Standard).
    3. Trong `dispatchKeyEvent` khi di chuyển sang phải: gọi ngay lệnh prefetch ngang cho các poster tiếp theo (`target + 1 .. target + aheadCount`).
  - **File**: [ModernInteropHorizontalGridView.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/ModernInteropHorizontalGridView.kt), [HomePosterLoading.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/core/image/HomePosterLoading.kt), [LeanbackModernRowAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRowAdapter.kt)

- [x] **SCROLL-LOAD-04**: Kiểm chứng tự động (Unit Tests) & kiểm tra build
  - **Vai trò**: QA / Verification Engineer
  - **Mô tả**: Viết unit test xác thực cờ `isHoldScrolling`, kiểm tra ngưỡng horizontal prefetch/loadMore và chạy `:app:compileFullDebugKotlin` + `:app:testFullDebugUnitTest`.
  - **File**: [LeanbackCoreOptimizationTest.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/test/java/com/nuvio/tv/ui/leanback/LeanbackCoreOptimizationTest.kt), [HomePrefetchTriggerTest.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/test/java/com/nuvio/tv/ui/screens/home/HomePrefetchTriggerTest.kt)
