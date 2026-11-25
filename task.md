# Danh Sách Task - Tự Động Chuyển Kênh Âm Thanh Về Stereo 2.0 Khi Bật Thuyết Minh (`tts-auto-stereo-2-0`)

> **Kế hoạch tương ứng**: [PLAN-tts-auto-stereo-2-0.md](file:///f:/MyGithub/NuvioTV-Fast/PLAN-tts-auto-stereo-2-0.md)
> **Phạm vi**: Khi bật thuyết minh Google TTS (`ttsEnabled && ttsAutoStereoDownmix`), tự động tắt Passthrough và trộn âm thanh về PCM Stereo 2.0 tại chỗ trên cả **ExoPlayer** (mọi bộ giải mã MediaCodec/FFmpeg) lẫn **MPV** để nghe rõ thuyết minh + hoạt động Audio Ducking; khi tắt thuyết minh thì tự động khôi phục nguyên trạng 100% kênh âm thanh gốc mà không ghi đè cài đặt người dùng.

---

## 📋 Task Breakdown (`TTS20`)

### Phase 1: Chính Sách Hợp Nhất & Bộ Trộn DSP Stereo 2.0
- [x] **TTS20-01**: Tạo `TtsAudioStereoPolicy.kt` hợp nhất logic ép Stereo 2.0 cho TTS & Bluetooth cùng toàn bộ quy tắc khôi phục kênh gốc
  - **Vai trò**: Media3 Audio Architecture Engineer
  - **Mô tả**: Xây dựng pure-function policy tính toán cấu hình đầu ra hiệu lực (`forcePcm`, `forceStereoDownmix`, `audioOutputChannels`, `forceOpticalPassthrough`, `disableTunneling`, `requiresRebuildForSoftwareDecoder`) dựa trên trạng thái TTS, Bluetooth, `SurroundResolveResult` và `PlayerSettings`.
  - **File**: [TtsAudioStereoPolicy.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/TtsAudioStereoPolicy.kt)

- [x] **TTS20-02**: Tạo `TtsStereoDownmixAudioProcessor.kt` (ITU-R BS.775 PCM 16-bit/Float → Stereo 2.0, zero-overhead khi tắt)
  - **Vai trò**: Android Audio DSP Engineer
  - **Mô tả**: Kế thừa `BaseAudioProcessor`, chỉ kích hoạt khi `enabled && channelCount > 2`; trộn chuẩn xác 3ch/4ch/5ch/5.1/7.1 xuống 2.0 có chống clipping và 0 cấp phát rác trong `queueInput`.
  - **File**: [TtsStereoDownmixAudioProcessor.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/TtsStereoDownmixAudioProcessor.kt)

### Phase 2: Tích Hợp ExoPlayer Hot-Switch & Khôi Phục Kênh Gốc
- [x] **TTS20-03**: Nâng cấp `PlaybackSpeedAwareAudioSink.kt` thêm cờ `ttsForceStereoPcm` độc lập với Bluetooth & tốc độ phát
  - **Vai trò**: ExoPlayer AudioSink Engineer
  - **Mô tả**: Bổ sung `setTtsForceStereoPcm(enabled)` / `isTtsForceStereoPcm()`, chặn passthrough khi TTS yêu cầu 2.0 và mở khóa lại passthrough gốc khi tắt TTS (nếu Bluetooth tắt và speed = 1.0x).
  - **File**: [PlaybackSpeedAwareAudioSink.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/PlaybackSpeedAwareAudioSink.kt)

- [x] **TTS20-04**: Nối dây `ttsStereoDownmixAudioProcessor`, `applyEffectiveAudioStereoModeInPlace`, guard Tunneling/SurroundResolve & `observeSubtitleTts` trên ExoPlayer
  - **Vai trò**: ExoPlayer Runtime Controller Engineer
  - **Mô tả**: Gắn `ttsStereoDownmixAudioProcessor` vào `SubtitleOffsetRenderersFactory.buildAudioSink`, giữ nguyên `AudioCapabilities` thật của HDMI/TV khi mở phim lúc TTS bật để khôi phục passthrough tức thì khi tắt; đồng bộ `isDirectPassthroughActive` cho ducking và chặn bộ tự dò HDMI phá ngang khi TTS đang bật.
  - **File**: [PlayerRuntimeController.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeController.kt), [PlayerRuntimeControllerInitialization.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt), [PlayerRuntimeControllerAudioDelayRoutes.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerAudioDelayRoutes.kt), [PlayerRuntimeControllerSurroundResolve.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerSurroundResolve.kt), [PlayerRuntimeControllerObservers.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerObservers.kt)

### Phase 3: Tích Hợp MPV Hot-Switch & Khôi Phục Kênh Gốc
- [x] **TTS20-05**: Nâng cấp `NuvioMpvSurfaceView.kt` & `PlayerRuntimeControllerMpv.kt` lưu/khôi phục `audio-channels` & `audio-spdif` cho TTS + Bluetooth
  - **Vai trò**: libmpv Playback Engineer
  - **Mô tả**: Lưu giá trị `audio-channels` và `audio-spdif` gốc trước khi ép `"stereo"` / `""`, thực hiện `ao-reload` giữ nguyên pause/delay/gain/ducking, và khôi phục chính xác kênh gốc khi tắt TTS (và không có Bluetooth).
  - **File**: [NuvioMpvSurfaceView.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/NuvioMpvSurfaceView.kt), [PlayerRuntimeControllerMpv.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerMpv.kt)

### Phase 4: Cài Đặt Tùy Chọn & Giao Diện Người Dùng (Mặc Định Bật)
- [x] **TTS20-06**: Thêm `ttsAutoStereoDownmix` (mặc định `true`) vào DataStore, Model, Overlay TTS, Cài đặt Phát lại và chuỗi EN/VI
  - **Vai trò**: Settings & Compose TV UI Engineer
  - **Mô tả**: Bổ sung công tắc *"Tự động chuyển âm thanh về 2.0"* trong `SubtitleTtsOverlay` và `PlaybackAudioSettings`, không làm thay đổi cài đặt 5.1/7.1 gốc đã lưu trong `PlayerSettingsDataStore`.
  - **File**: [SubtitleTtsModels.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/core/tts/SubtitleTtsModels.kt), [PlayerSettingsDataStore.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/data/local/PlayerSettingsDataStore.kt), [PlayerUiState.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerUiState.kt), [PlayerRuntimeControllerPlaybackEvents.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt), [PlayerScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerScreen.kt), [SubtitleTtsOverlay.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/SubtitleTtsOverlay.kt), [PlaybackAudioSettings.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/settings/PlaybackAudioSettings.kt), [PlaybackSettingsViewModel.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/settings/PlaybackSettingsViewModel.kt), [strings.xml](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/res/values/strings.xml), [strings.xml (vi)](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/res/values-vi/strings.xml)

### Phase 5: Kiểm Thử Tự Động & Nghiệm Thu
- [x] **TTS20-07**: Viết Unit Tests (`TtsStereoDownmixAudioProcessorTest`, `TtsAudioStereoPolicyTest`, `PlaybackSpeedAwareAudioSinkTtsTest`) và xác minh biên dịch cả 2 flavor
  - **Vai trò**: QA & Verification Engineer
  - **Mô tả**: Kiểm chứng toàn bộ ma trận downmix DSP, chuyển đổi nóng TTS/Bluetooth/Tunneling/Surround, khôi phục kênh gốc khi tắt TTS, và biên dịch sạch `compileFullDebugKotlin` + `compilePlaystoreDebugKotlin`.

---

# Danh Sách Task - Đồng Bộ Nhánh `upstream/dev` → `tet2` Ngày 25-11-2026 (`sync-dev-2026-11-25`)

> **Kế hoạch tương ứng**: [PLAN-sync-dev-2026-11-25.md](file:///f:/MyGithub/NuvioTV-Fast/PLAN-sync-dev-2026-11-25.md)
> **Phạm vi**: Hợp nhất 209 commit (9 PR + 1 version bump: `11859c5d9` → `77cc0bb5f`) từ `upstream/dev` sang `tet2`, giải quyết 8 file `CONFLICT`, bảo toàn 100% tùy chỉnh riêng trên `tet2`, nâng cấp huy hiệu Rewatch lên Leanback Episode Card, đồng bộ Rewatch Prompt cho TorrServer, sửa test độc quyền `HomeCatalogLazyPipelineTest` và bổ sung bản dịch Tiếng Việt cho Simkl Rewatch.

---

## 📋 Task Breakdown (`SYNC1125`)

### Phase 1: Sao Lưu An Toàn & Đồng Bộ Nhánh Cơ Sở
- [x] **SYNC1125-01**: Tạo nhánh dự phòng `backup-tet2-before-merge-20261125` và fast-forward nhánh `dev` cục bộ lên `upstream/dev` (`77cc0bb5f`)
  - **Vai trò**: Git Release Architect
  - **Mô tả**: Đảm bảo điểm khôi phục tức thì tại `88c11eb98` trước khi chạy `git merge --no-ff upstream/dev`, đồng thời đồng bộ nhánh `dev` local khớp 100% với `origin/dev` và `upstream/dev`.

### Phase 2: Giải Quyết Phẫu Thuật 8 Tệp Xung Đột (`CONFLICT`)
- [x] **SYNC1125-02**: Hợp nhất nhóm cấu hình Build, Activity & Luồng phát (`app/build.gradle.kts`, `MainActivity.kt`, `PlayerRuntimeControllerPlaybackEvents.kt`, `StreamScreen.kt`)
  - **Vai trò**: Android Core & Playback Integration Engineer
  - **Mô tả**: Giữ `applicationId = "com.nuvio.tv.fast"` & `versionCode = 1218` (`1.7.6`); gộp import và đặt `RewatchPromptOverlay` cạnh `AmbientModeHost` (chặn Ambient bật đè khi đang hiện câu hỏi Rewatch); thay `80f` bằng `TRACKING_COMPLETION_BAR_PERCENT` kết hợp giữ `|| isLiveTv`; giữ nhánh `isTorrServerStream` cùng `progressPercent` và `RewatchPromptOverlay(isLocalHost = true)`.
  - **File**: [app/build.gradle.kts](file:///f:/MyGithub/NuvioTV-Fast/app/build.gradle.kts), [MainActivity.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/MainActivity.kt), [PlayerRuntimeControllerPlaybackEvents.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt), [StreamScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/stream/StreamScreen.kt)

- [x] **SYNC1125-03**: Hợp nhất `EpisodesSection.kt` (giữ `LeanbackNativeRow` 60fps + nối dây Rewatch) và `SimklAuthRepositoryTest.kt` (giữ cả OAuth2 v2 PIN tests & Account Plan tests)
  - **Vai trò**: TV UI & Data Sync Engineer
  - **Mô tả**: Loại bỏ `EpisodeCard` Compose chết trong `EpisodesSection.kt`, giữ nguyên `LeanbackNativeRow` của `tet2`, đưa `rewatchedEpisodes` vào `remember(...)` của `leanbackEpisodes` và giữ trọn kết nối tới `EpisodeOptionsOverlay`; gộp đầy đủ 2 bộ test trong `SimklAuthRepositoryTest.kt`.
  - **File**: [EpisodesSection.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/detail/EpisodesSection.kt), [SimklAuthRepositoryTest.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/test/java/com/nuvio/tv/data/simkl/SimklAuthRepositoryTest.kt)

- [x] **SYNC1125-04**: Hợp nhất tài nguyên ngôn ngữ `values-hu/strings.xml` (theo chuẩn hóa PR #4806) và `values-vi/strings.xml` (giữ 100% chuỗi TorrServer/TTS/LiveTV/Ambient + nhận chuỗi Usenet & Blu-ray disc titles)
  - **Vai trò**: Localization & Resource Specialist
  - **Mô tả**: Áp dụng bản chuẩn hóa Tiếng Hungary của upstream (khôi phục `p2p_consent_*` và xóa lỗi gõ nhầm từ cũ); nối đầy đủ các chuỗi mới của upstream vào `values-vi/strings.xml` mà không làm mất bất kỳ chuỗi tùy chỉnh nào của `tet2`.
  - **File**: [strings.xml (hu)](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/res/values-hu/strings.xml), [strings.xml (vi)](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/res/values-vi/strings.xml)

### Phase 3: Nâng Cấp Đồng Bộ Kiến Trúc & Vá Lỗi Ngữ Nghĩa Chủ Động
- [x] **SYNC1125-05**: Nâng cấp `LeanbackCardItem.Episode` & `LeanbackEpisodeViewHolder` hiển thị huy hiệu Rewatch (`↻`) và tích hợp `preparePlaybackForStream` vào `TorrServer`
  - **Vai trò**: Android Leanback & TorrServer Engineer
  - **Mô tả**: Thêm `isRewatched: Boolean = false` vào `LeanbackCardItem.Episode` và vẽ `rewatchBadgeView` cạnh `watchedBadgeView` trong `LeanbackEpisodeViewHolder`; truyền `progressPercent` vào `StreamScreenViewModel.resolveTorrServerPlayback` để hỗ trợ câu hỏi Simkl Rewatch khi phát qua TorrServer.
  - **File**: [LeanbackCardItem.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/model/LeanbackCardItem.kt), [LeanbackEpisodeViewHolder.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/holder/LeanbackEpisodeViewHolder.kt), [StreamScreenViewModel.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/stream/StreamScreenViewModel.kt)

- [x] **SYNC1125-06**: Cập nhật constructor `HomeViewModel` trong `HomeCatalogLazyPipelineTest.kt` và bổ sung bản dịch Tiếng Việt cho 28 chuỗi Simkl Rewatch & Watched Threshold mới
  - **Vai trò**: QA & Localization Engineer
  - **Mô tả**: Truyền `simklSyncRepository = mockk(relaxed = true)` và `simklRewatchWriter = mockk(relaxed = true)` vào `HomeCatalogLazyPipelineTest.kt`; dịch đầy đủ 28 chuỗi mới của PR #3653 sang Tiếng Việt trong `values-vi/strings.xml` và quét xác nhận 0 trùng lặp khóa XML.
  - **File**: [HomeCatalogLazyPipelineTest.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/test/java/com/nuvio/tv/ui/screens/home/HomeCatalogLazyPipelineTest.kt), [strings.xml (vi)](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/res/values-vi/strings.xml)

### Phase 4: Kiểm Thử Tự Động, Nghiệm Thu & Hoàn Tất Commit Merge
- [x] **SYNC1125-07**: Biên dịch xác minh toàn diện (`full`, `playstore`, `unitTest`, `androidTest`) và chạy toàn bộ Unit Test Suite mới + tùy chỉnh
  - **Vai trò**: Quality Inspector & Verification Engineer
  - **Mô tả**: Chạy `:app:compileFullDebugKotlin :app:compilePlaystoreDebugKotlin :app:compileFullDebugUnitTestKotlin :app:compileFullDebugAndroidTestKotlin` và `:app:testFullDebugUnitTest` cho Simkl/Rewatch, Tracking, Debrid, Usenet, Blu-ray DTS-HD, Home, Leanback, Trailer 1080p, TTS và Live TV.

- [x] **SYNC1125-08**: Tạo commit merge sạch và lập báo cáo nghiệm thu chi tiết từng file xung đột & nâng cấp
  - **Vai trò**: Release Coordinator
  - **Mô tả**: Commit kết quả hợp nhất với thông điệp chuẩn hóa và xuất báo cáo đầy đủ gửi người dùng.

---

# Danh Sách Task - Đồng Bộ Nhánh `upstream/dev` → `tet2` (24-11-2026 Đến 25-11-2026) (`sync-dev-2026-11-25`)

> **Kế hoạch tương ứng**: [PLAN-sync-dev-2026-11-25.md](file:///f:/MyGithub/NuvioTV-Fast/PLAN-sync-dev-2026-11-25.md)
> **Phạm vi**: Hợp nhất 209 commit (9 PR + 1 version bump: `11859c5d9` → `77cc0bb5f`) từ `upstream/dev` sang `tet2`; giải quyết 8 file `CONFLICT`; bảo toàn 100% tùy chỉnh riêng trên `tet2` (Trailer 1080p, Home Loading Boost, Leanback Native 60fps, Ambient Mode, Live TV, Google TTS, TorrServer) và nâng cấp đồng bộ huy hiệu Rewatch lên Leanback + TorrServer + Tiếng Việt.

---

## 📋 Task Breakdown (`SYNC1125`)

### Phase 1: Sao Lưu An Toàn & Khởi Chạy Merge
- [ ] **SYNC1125-01**: Tạo nhánh dự phòng `backup-tet2-before-merge-20261125` và đồng bộ nhánh `dev` cục bộ lên `77cc0bb5f`
  - **Vai trò**: Git Release Architect
  - **Mô tả**: Đảm bảo có điểm khôi phục tức thì tại `88c11eb98` trước khi can thiệp và cập nhật tham chiếu nhánh `dev` cục bộ khớp với `upstream/dev` (`77cc0bb5f`).

- [ ] **SYNC1125-02**: Thực thi lệnh hợp nhất `git merge --no-ff upstream/dev` vào nhánh `tet2`
  - **Vai trò**: Git Release Architect
  - **Mô tả**: Khởi chạy merge không fast-forward và đối chiếu danh sách 8 file `CONFLICT` thực tế với kết quả mô phỏng `git merge-tree`.

### Phase 2: Phẫu Thuật Giải Quyết 8 File Xung Đột Trực Tiếp (`CONFLICT`)
- [ ] **SYNC1125-03**: Giải quyết xung đột nhóm Cấu hình, Màn hình & Trình phát (`build.gradle.kts`, `MainActivity.kt`, `EpisodesSection.kt`, `PlayerRuntimeControllerPlaybackEvents.kt`, `StreamScreen.kt`)
  - **Vai trò**: Core Android & Playback Integration Engineer
  - **Mô tả**: Giữ `versionCode = 1218` / `1.7.6` cho `com.nuvio.tv.fast`; tích hợp `RewatchPromptOverlay` cùng bảo vệ `AmbientModeHost` trong `MainActivity.kt`; giữ `LeanbackNativeRow` trong `EpisodesSection.kt` kèm dữ liệu `rewatchedEpisodes`; kết hợp `TRACKING_COMPLETION_BAR_PERCENT` với cờ bảo vệ `|| isLiveTv` trong `PlayerRuntimeControllerPlaybackEvents.kt`; hợp nhất nhánh `TorrServer` với `progressPercent` trong `StreamScreen.kt`.
  - **File**: [build.gradle.kts](file:///f:/MyGithub/NuvioTV-Fast/app/build.gradle.kts), [MainActivity.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/MainActivity.kt), [EpisodesSection.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/detail/EpisodesSection.kt), [PlayerRuntimeControllerPlaybackEvents.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt), [StreamScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/stream/StreamScreen.kt)

- [ ] **SYNC1125-04**: Giải quyết xung đột nhóm Tài nguyên Đa ngôn ngữ & Unit Test (`values-hu/strings.xml`, `values-vi/strings.xml`, `SimklAuthRepositoryTest.kt`)
  - **Vai trò**: Localization & QA Integration Engineer
  - **Mô tả**: Áp dụng trọn vẹn bản hiệu đính Tiếng Hungary PR #4806 trong `values-hu/strings.xml`; gộp đầy đủ các khối chuỗi `TorrServer`, `TTS` của `tet2` với khối `Usenet`, `Disc Titles` của upstream trong `values-vi/strings.xml`; giữ nối tiếp cả 4 bài test OAuth2 v2 của `tet2` lẫn 9 bài test Rewatch Plan của upstream trong `SimklAuthRepositoryTest.kt`.
  - **File**: [strings.xml (hu)](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/res/values-hu/strings.xml), [strings.xml (vi)](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/res/values-vi/strings.xml), [SimklAuthRepositoryTest.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/test/java/com/nuvio/tv/data/simkl/SimklAuthRepositoryTest.kt)

### Phase 3: Nâng Cấp Tương Thích Kiến Trúc Tùy Chỉnh `tet2` & Bản Địa Hóa
- [ ] **SYNC1125-05**: Bổ sung huy hiệu Đã Xem Lại (`isRewatched` / Refresh Icon) cho thẻ tập phim Android Leanback Native
  - **Vai trò**: Android TV Leanback UI Engineer
  - **Mô tả**: Thêm thuộc tính `isRewatched: Boolean = false` vào `LeanbackCardItem.Episode` và dựng `rewatchBadgeView` (biểu tượng xoay tròn đặt bên trái dấu tích `watchedBadgeView`) trong `LeanbackEpisodeViewHolder.kt` để đồng bộ tính năng PR #3653 lên hàng tập phim Leanback 60fps của `tet2`.
  - **File**: [LeanbackCardItem.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/model/LeanbackCardItem.kt), [LeanbackEpisodeViewHolder.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/holder/LeanbackEpisodeViewHolder.kt), [EpisodesSection.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/detail/EpisodesSection.kt)

- [ ] **SYNC1125-06**: Hỗ trợ hộp thoại Simkl Rewatch khi phát qua TorrServer & Cập nhật constructor `HomeViewModel` trong `HomeCatalogLazyPipelineTest`
  - **Vai trò**: Playback & Test Architecture Engineer
  - **Mô tả**: Truyền `progressPercent` và gọi `preparePlaybackForStream(progressPercent)` trong `StreamScreenViewModel.resolveTorrServerPlayback`; bổ sung `simklSyncRepository` và `simklRewatchWriter` vào lời gọi khởi tạo `HomeViewModel` trong bài test riêng `HomeCatalogLazyPipelineTest.kt`.
  - **File**: [StreamScreenViewModel.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/stream/StreamScreenViewModel.kt), [StreamScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/stream/StreamScreen.kt), [HomeCatalogLazyPipelineTest.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/test/java/com/nuvio/tv/ui/screens/home/HomeCatalogLazyPipelineTest.kt)

- [ ] **SYNC1125-07**: Dịch bổ sung 28 chuỗi Tiếng Việt cho tính năng Simkl Rewatch & Watched Threshold và quét trùng lặp khóa XML
  - **Vai trò**: Vietnamese Localization Specialist
  - **Mô tả**: Bổ sung bản dịch Tiếng Việt tự nhiên cho 28 chuỗi mới của PR #3653 trong `values-vi/strings.xml` và xác minh không có bất kỳ khóa `<string>` / `<plurals>` nào bị trùng tên.
  - **File**: [strings.xml (vi)](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/res/values-vi/strings.xml)

### Phase 4: Kiểm Chứng Toàn Diện & Đóng Gói Commit Merge
- [ ] **SYNC1125-08**: Biên dịch kiểm tra cả `full`, `playstore`, `androidTest`, AAPT2 resources và chạy bộ Unit Tests toàn hệ thống
  - **Vai trò**: QA & Verification Inspector
  - **Mô tả**: Chạy `:app:compileFullDebugKotlin :app:compilePlaystoreDebugKotlin :app:compileFullDebugAndroidTestKotlin :app:mergeFullDebugResources` và chạy `testFullDebugUnitTest` cho toàn bộ suite Simkl Rewatch, Tracking, Home, Player BDAV/Scrobble, Usenet, Debrid, Trailer 1080p, Leanback, TTS, Live TV.

- [ ] **SYNC1125-09**: Hoàn tất Commit Merge sạch và xuất báo cáo nghiệm thu chi tiết
  - **Vai trò**: Release Coordinator
  - **Mô tả**: Tạo commit merge rõ ràng và báo cáo chi tiết từng file xung đột, từng điểm thay thế/nâng cấp cùng bằng chứng kiểm thử cho người dùng.

---

# Danh Sách Task - Mặc Định Phát Trailer 1080p & Khắc Phục Lỗi Tụt Độ Phân Giải Thấp (`trailer-default-1080p`)

> **Kế hoạch tương ứng**: [PLAN-trailer-default-1080p.md](file:///f:/MyGithub/NuvioTV-Fast/PLAN-trailer-default-1080p.md)
> **Phạm vi**: Khóa mặc định độ phân giải trailer ở **1080p** (giữ công tắc **Trailer 4K** mặc định **Tắt**), khắc phục triệt để các lỗi khiến trailer tụt xuống 360p/480p trong `InAppYouTubeExtractor`, gia cố `TrailerPlayerPool` & `YoutubeChunkedDataSourceFactory`, áp dụng đồng bộ cho mọi khu vực (Home, Chi tiết, Ambient Mode, Toàn màn hình).

---

## 📋 Task Breakdown (`TR1080`)

### Phase 1: Chính Sách Mặc Định 1080p, DataStore & Giao Diện Cài Đặt
- [x] **TR1080-01**: Đặt mặc định **1080p** trong `TrailerVideoPolicy`, `TrailerSettingsDataStore`, `TrailerService`, `LayoutSettingsViewModel` và cập nhật chuỗi mô tả EN/VI
  - **Vai trò**: Core Policy & Settings Engineer
  - **Mô tả**: Đổi `TrailerVideoPolicy.default4kTrailers(context)` mặc định trả về `false`, thêm helper `maxTrailerVideoWidth(maxHeight)`; đồng bộ mặc định `allow4k = false` ở `TrailerSettings`, `TrailerService.trailerHeightCap()` (`.getOrDefault(false)`) và `LayoutSettingsUiState.trailer4kEnabled = false`; cập nhật `layout_trailer_4k_sub` trong `values/strings.xml` & `values-vi/strings.xml`.
  - **File**: [TrailerVideoPolicy.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/core/player/TrailerVideoPolicy.kt), [TrailerSettingsDataStore.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/data/local/TrailerSettingsDataStore.kt), [TrailerService.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/data/trailer/TrailerService.kt), [LayoutSettingsViewModel.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/settings/LayoutSettingsViewModel.kt), [strings.xml](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/res/values/strings.xml), [strings.xml (vi)](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/res/values-vi/strings.xml)

### Phase 2: Sửa Gốc Rễ Trích Xuất YouTube 1080p (`InAppYouTubeExtractor`)
- [x] **TR1080-02**: Sửa bug mất header `Range` trong `isUrlReachable()` và bổ sung khôi phục `visitorData` qua `/youtubei/v1/visitor_id`
  - **Vai trò**: YouTube Extractor & Networking Engineer
  - **Mô tả**: Sửa thứ tự gán header `Range` sau `.headers(buildHeaders(...))` (dùng `bytes=0-0` cho video và `bytes=0-1023` cho audio để chặn sớm audio 403 giả); tăng timeout `resolveReachableUrl()` lên `3_500ms`; gọi `POST /youtubei/v1/visitor_id` khi watch page thiếu `VISITOR_DATA` và không bao giờ cache `visitorData = null` với TTL 3 giờ.
  - **File**: [InAppYouTubeExtractor.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/data/trailer/InAppYouTubeExtractor.kt)

- [x] **TR1080-03**: Nâng cấp chọn đa ứng viên Adaptive 1080p (chống kẹt client cũ, chống video câm) & áp trần `maxVideoHeight` cho HLS Manifest
  - **Vai trò**: YouTube Extractor & Media Selection Engineer
  - **Mô tả**: Thêm `rankAdaptiveVideos`/`rankAdaptiveAudios` theo trần `maxVideoHeight` (`1080p → 720p → 480p → 360p`), theo dõi `failedUrls` để thử lần lượt tối đa 3 ứng viên video + 2 audio mà không kẹt vào URL `visionos` đã chết; chỉ chấp nhận nguồn Adaptive khi cả video và audio đều reachable (nếu không thì ưu tiên `HLS_MANIFEST` 1080p trước `PROGRESSIVE`); truyền `maxVideoHeight` vào `pickBestManifest()` và thêm log chẩn đoán nguồn/độ phân giải.
  - **File**: [InAppYouTubeExtractor.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/data/trailer/InAppYouTubeExtractor.kt)

### Phase 3: Khóa Track Selection 1080p Trên Trình Phát, Gia Cố DataSource & Khắc Phục Mờ Ambient Mode
- [x] **TR1080-04**: Siết chặt `DefaultTrackSelector` 1080p trong `TrailerPlayerPool`, gắn User-Agent cho `YoutubeChunkedDataSourceFactory` & thêm debug log kích thước video
  - **Vai trò**: Media3 / ExoPlayer Playback Engineer
  - **Mô tả**: Xóa `.setMaxVideoSizeSd()` thừa trong `TrailerPlayerPool`, áp dụng `clearViewportSizeConstraints()`, `setForceHighestSupportedBitrate(true)`, `setMaxVideoSize(maxWidth, maxHeight)`, `setExceedVideoConstraintsIfNecessary(true)`, `setExceedRendererCapabilitiesIfNecessary(false)` và cập nhật động khi đổi cài đặt `allow4k`; đặt User-Agent chuẩn + log lỗi HTTP trong `YoutubeChunkedDataSourceFactory`; thêm debug log `onVideoSizeChanged` trong `TrailerPlayer` (`BuildConfig.DEBUG`).
  - **File**: [TrailerPlayerPool.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/core/player/TrailerPlayerPool.kt), [YoutubeChunkedDataSourceFactory.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/data/trailer/YoutubeChunkedDataSourceFactory.kt), [TrailerPlayer.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/components/TrailerPlayer.kt)

- [x] **TR1080-05**: Khắc phục triệt để mờ trailer trong Ambient Mode (`cropToFill = false`, `autoCropLetterbox = false`, compositing ModulateAlpha)
  - **Vai trò**: TV Display & Ambient Mode Graphics Engineer
  - **Mô tả**: Vô hiệu hóa việc phóng to ảnh nội suy trên `TextureView` trong `AmbientSlideshow` bằng cách tắt `cropToFill` và `autoCropLetterbox`, giữ nguyên độ phân giải 1:1 pixel native 1080p; tách biệt lớp video với ảnh nền tĩnh bằng công thức alpha `backdropAlpha.value * (1f - videoAlpha.value)` và `CompositingStrategy.ModulateAlpha` tránh làm đục màu video.
  - **File**: [AmbientSlideshow.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/ambient/AmbientSlideshow.kt)

### Phase 4: Kiểm Thử Tự Động & Xác Minh
- [x] **TR1080-06**: Viết & mở rộng bộ Unit Tests (`TrailerVideoPolicyTest`, `InAppYouTubeExtractorProbeAndVisitorTest`, `InAppYouTubeExtractorVideoCapTest`)
  - **Vai trò**: QA / Automated Test Engineer
  - **Mô tả**: Kiểm thử chính sách mặc định 1080p, xác minh `MockWebServer` gửi đúng header `Range` và gọi `/youtubei/v1/visitor_id`, xác minh chọn đa ứng viên 1080p → 720p → 480p → 360p và bỏ qua URL lỗi.
  - **File**: [TrailerVideoPolicyTest.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/test/java/com/nuvio/tv/core/player/TrailerVideoPolicyTest.kt), [InAppYouTubeExtractorProbeAndVisitorTest.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/test/java/com/nuvio/tv/data/trailer/InAppYouTubeExtractorProbeAndVisitorTest.kt), [InAppYouTubeExtractorVideoCapTest.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/test/java/com/nuvio/tv/data/trailer/InAppYouTubeExtractorVideoCapTest.kt)

- [x] **TR1080-07**: Chạy Unit Tests toàn hệ thống trailer & xác minh biên dịch cả 2 flavor (`full` & `playstore`)
  - **Vai trò**: Release / Verification Engineer
  - **Mô tả**: Chạy `testFullDebugUnitTest` cho `data.trailer.*`, `TrailerVideoPolicyTest`, `YouTubeStreamResolverTest`, `ProfileSettingsCredentialPolicyTest`, `AmbientTrailerResolverTest` và chạy `compileFullDebugKotlin :app:compilePlaystoreDebugKotlin`.

---

# Danh Sách Task - Tối Ưu Độ Mượt Khi Bấm Giữ Di Chuyển Trái / Phải (Phạm Vi Thuần Leanback) (`home-horizontal-hold-smoothness`)

> **Kế hoạch tương ứng**: [PLAN-home-horizontal-hold-smoothness.md](file:///f:/MyGithub/NuvioTV-Fast/PLAN-home-horizontal-hold-smoothness.md)
> **Phạm vi**: **100% chỉ sửa trong tầng Android Leanback** (7 tệp Leanback + 1 tệp Unit Test). Không can thiệp các file Compose Screen hay ViewModel.

---

## 📋 Task Breakdown (`HSCROLL`)

### Phase 1: Chuẩn Hóa Động Cơ Cuộn Ngang Leanback (Core GridView)
- [x] **HSCROLL-01**: Loại bỏ xung đột `setSelectedPositionSmooth` vs `requestFocus`, kẹp `MAX_HORIZONTAL_LOOKAHEAD = 2`, áp dụng `LinearInterpolator` (65ms) khi giữ phím ngang và chặn giật ngược thẻ trung gian trong `ModernInteropHorizontalGridView` & `InteropHorizontalGridView`
  - **Vai trò**: Android TV Focus & Leanback Scroller Engineer
  - **Mô tả**: Khi `animationsEnabled == true`, chỉ gọi `setSelectedPositionSmooth(target)` mà không gọi đè `itemView.requestFocus()` ngay trong `ACTION_DOWN`; override `smoothScrollBy` dùng `LinearInterpolator` (65ms) khi `isHoldingHorizontalKey == true` để loại bỏ hiện tượng giật vận tốc `QuinticInterpolator` mỗi 55ms; kẹp khoảng cách dẫn trước `pendingTargetPosition` tối đa 2 thẻ khi đang cuộn; bỏ `requestFocus()` trên các thẻ trung gian trong `onChildViewHolderSelected`; flush trạng thái settled ngay khi nhận `ACTION_UP` hoặc khi đổi phím sang trục khác (`UP`/`DOWN`/`CENTER`/`ENTER`/`MENU`/`BACK`).
  - **File**: [ModernInteropHorizontalGridView.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/ModernInteropHorizontalGridView.kt), [InteropHorizontalGridView.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/container/InteropHorizontalGridView.kt)

### Phase 2: Đóng Băng Software Glow (`LAYER_TYPE_SOFTWARE`), GPU Readback & Marquee Khi Hold Ngang
- [x] **HSCROLL-02**: Chặn vẽ lại `BlurMaskFilter` trên `ModernRowFloatingFocusRingView`, chặn copy `Hardware Bitmap` trích màu và hoãn Marquee khi `isHoldingHorizontalKey == true`
  - **Vai trò**: TV Rendering & Graphics Performance Engineer
  - **Mô tả**: Giữ nguyên màu hào quang hiện tại của `ModernRowFloatingFocusRingView` trong suốt lúc `isHoldingHorizontalKey == true` (tránh `invalidate()` software layer 18 lần/giây) và chỉ gán `translationX` trong `onParentScrolled(dx)` khi lệch `>= 0.5f`; không gọi `extractDominantColorAsync` (tránh copy Hardware Bitmap) và không bật `titleText.isSelected = true` trên thẻ trung gian; cập nhật đầy đủ màu hào quang & Marquee ngay khi dừng/nhả phím.
  - **File**: [ModernRowFloatingFocusRingView.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/ModernRowFloatingFocusRingView.kt), [LeanbackModernRowAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRowAdapter.kt), [LeanbackBaseViewHolder.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/holder/LeanbackBaseViewHolder.kt), [LeanbackPosterViewHolder.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/holder/LeanbackPosterViewHolder.kt), [LeanbackEpisodeViewHolder.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/holder/LeanbackEpisodeViewHolder.kt)

### Phase 3: Coalesce `onItemFocused` & Stride-Throttle Prefetch Ngay Trong Leanback Adapter
- [x] **HSCROLL-03**: Gom (coalesce) callback `onItemFocused` và áp dụng Stride Prefetch bên trong `LeanbackRowAdapter` & `LeanbackUniversalAdapter` khi `isHoldingHorizontalKey == true`
  - **Vai trò**: Leanback Adapter & Bridge Engineer
  - **Mô tả**: Khi `isHoldingHorizontalKey == true`, lưu `pendingFocusedPosition` bên trong Adapter thay vì bắn `onItemFocused` lên Compose 18 lần/giây, sau đó flush 1 lần duy nhất khi nhả phím / chuyển phím / `onScrollSettled()`; xóa lệnh gọi đôi `onFocusedPositionChanged` trong `ModernInteropHorizontalGridView` và áp dụng stride throttle (chỉ prefetch mỗi 3 thẻ khi đang giữ phím ngang).
  - **File**: [LeanbackModernRowAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRowAdapter.kt), [LeanbackUniversalAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/adapter/LeanbackUniversalAdapter.kt), [LeanbackBaseViewHolder.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/holder/LeanbackBaseViewHolder.kt)

### Phase 4: Kiểm Thử Tự Động & Nghiệm Thu
- [x] **HSCROLL-04**: Viết Unit Tests kiểm chứng Horizontal Hold Smoothness thuần Leanback & chạy bộ test
  - **Vai trò**: QA / Verification Engineer
  - **Mô tả**: Bổ sung unit tests cho lookahead clamp, focus coalescing trong Adapter khi giữ phím ngang, chặn redraw floating ring & stride prefetch; chạy `compileFullDebugKotlin` và `testFullDebugUnitTest`.
  - **File**: [LeanbackCoreOptimizationTest.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/test/java/com/nuvio/tv/ui/leanback/LeanbackCoreOptimizationTest.kt)

---

# Danh Sách Task - Rà Soát Sâu Vòng 3 — Chốt Hạ Lỗi Tầng Sâu Android Leanback (`tet2`)

> **Kế hoạch tương ứng**: [PLAN-leanback-deep-review-r3.md](file:///f:/MyGithub/NuvioTV-Fast/PLAN-leanback-deep-review-r3.md)
> **Phạm vi**: Khắc phục dứt điểm 5 lỗi tầng sâu P1 (`R3-BUG-01`..`05`) và 2 tinh chỉnh P2 (`R3-BUG-06`..`07`) phát hiện sau Vòng 2.

---

## 📋 Task Breakdown (Vòng 3)

### Phase 1: Khóa Chặt 5 Lỗi P1 Tầng Sâu
- [x] **LBR3-01**: Khắc phục skip recomposition badge Watched trên Modern Home & sửa thứ tự `setSelectedPosition` sau `submitItems` (`R3-BUG-01`, `R3-BUG-02`)
  - **Vai trò**: Compose-Leanback Lifecycle Engineer
  - **Mô tả**: Đọc `watchedMaskHash` (0 cấp phát) trong thân Composable `LeanbackModernRow` để Compose Snapshot Observer tự kích hoạt `AndroidView.update` → `syncWatchedStateChanges` khi `movieWatchedStatus` đổi; chuyển khối `setSelectedPosition(initialFocusPosition)` xuống sau `adapter.submitItems(items)` ở cả `LeanbackNativeRow` và `LeanbackModernRow`.
  - **File**: [LeanbackModernRow.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt), [LeanbackNativeRow.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/LeanbackNativeRow.kt)

- [x] **LBR3-02**: Sửa race condition & cướp focus của `gridScrollToTopTrigger` trong `FolderDetailScreen` (`R3-BUG-03`)
  - **Vai trò**: Folder Screen Focus Engineer
  - **Mô tả**: Bỏ gán `restoreFocusNonce = gridScrollToTopTrigger`; giữ ref `activeGridView` qua `onGridCreated` của `LeanbackNativeGrid` và chỉ cuộn mượt về 0 (`setSelectedPositionSmooth(0)`) khi click lại đúng Tab đang chọn mà không giật focus khỏi Tab.
  - **File**: [FolderDetailScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/collection/FolderDetailScreen.kt)

- [x] **LBR3-03**: Hoàn thiện Focus/Crash Guards trên 3 GridView, siết `InteropHorizontalGridView.mayTakeFocus` & sửa deep-item restore của `LeanbackNativeGrid` (`R3-BUG-04`, `R3-BUG-05`)
  - **Vai trò**: Android TV Focus Engine Engineer
  - **Mô tả**: Thêm `clearFocusDepth` guard & fix `focusSearch` khi `isBusyLayout` trong `InteropVerticalGridView`; bảo vệ `post { super.requestChildFocus }` bằng kiểm tra `isAttachedToWindow && isDescendant(child)` ở cả 3 GridView; siết điều kiện reclaim focus khi `canTakeFocus == null` trong `InteropHorizontalGridView` & `LeanbackUniversalAdapter`; chuyển `executeAttachedGridFocus` trong `LeanbackNativeGrid` sang `setSelectedPosition` + retry tối đa 8 frame.
  - **File**: [InteropVerticalGridView.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/container/InteropVerticalGridView.kt), [InteropHorizontalGridView.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/container/InteropHorizontalGridView.kt), [ModernInteropHorizontalGridView.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/ModernInteropHorizontalGridView.kt), [LeanbackUniversalAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/adapter/LeanbackUniversalAdapter.kt), [LeanbackNativeGrid.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/LeanbackNativeGrid.kt)

### Phase 2: Tinh Chỉnh P2, Changelog Rollback & Verification Cuối
- [x] **LBR3-04**: Khởi tạo `lastProgressAccentColor`, gỡ khóa sàn `borderWidthDp` & đồng tông hào quang cho `SOLID_*` (`R3-BUG-06`, `R3-BUG-07`)
  - **Vai trò**: TV Rendering & Design System Engineer
  - **Mô tả**: Gán `lastProgressAccentColor = themeStyle.accentColor` trong `LeanbackRowAdapter.onCreateViewHolder()`; đổi `.coerceAtLeast(2f)` cho `resolvedBorderWidthPx` ở `LeanbackBaseViewHolder`; bổ sung `LeanbackThemeStyle.resolveGlowColor(dominantColor)` áp dụng đồng tông hào quang cho `SOLID_*` ở cả `LeanbackBaseViewHolder` và `ModernRowFloatingFocusRingView`.
  - **File**: [LeanbackThemeStyle.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/core/LeanbackThemeStyle.kt), [LeanbackBaseViewHolder.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/holder/LeanbackBaseViewHolder.kt), [ModernRowFloatingFocusRingView.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/ModernRowFloatingFocusRingView.kt), [LeanbackModernRowAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRowAdapter.kt)

- [x] **LBR3-05**: Cập nhật mục Vòng 3 (`R3-BUG-01`..`R3-BUG-07`) vào [LEANBACK_FIX_CHANGELOG.md](file:///f:/MyGithub/NuvioTV-Fast/LEANBACK_FIX_CHANGELOG.md)
  - **Vai trò**: Documentation & Rollback Maintainer
  - **Mô tả**: Ghi rõ từng mã sửa Vòng 3 kèm file/dòng và hướng dẫn khôi phục riêng lẻ.

- [x] **LBR3-06**: Bổ sung Unit Tests Vòng 3, chạy `testFullDebugUnitTest` & `assembleFullRelease` + xác minh trên MuMu Android TV
  - **Vai trò**: QA / Verification Engineer
  - **Mô tả**: Thêm test cho `resolveGlowColor`, chạy toàn bộ `LeanbackCoreOptimizationTest`, đóng gói Release APK và kiểm tra thực tế trên emulator MuMu (`0` crash).
  - **File**: [LeanbackCoreOptimizationTest.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/test/java/com/nuvio/tv/ui/leanback/LeanbackCoreOptimizationTest.kt)

---

# Danh Sách Task - Rà Soát Sâu Vòng 2 & Hoàn Thiện Android Leanback (`tet2`)

> **Kế hoạch tương ứng**: [PLAN-leanback-deep-review-r2.md](file:///f:/MyGithub/NuvioTV-Fast/PLAN-leanback-deep-review-r2.md)
> **Phạm vi**: Khắc phục 6 lỗi/regression P1 (`R2-BUG-01`..`06`), 3 tinh chỉnh P2 (`R2-BUG-07`..`09`), và 3 hạng mục dọn dẹp dead code / đồng bộ `AppFont` (`R2-CLEAN-01`..`03`) sau Đợt 1.

---

## 📋 Task Breakdown (Vòng 2)

### Phase 1: Sửa Regression & Lỗi P1 (Watched, Scroll Restore, Focus Grid/Consumer)
- [x] **LBR2-01**: Khôi phục reactivity Watched trên Modern Home & `FolderDetailScreen`, đồng bộ `blurred` prefetch & màu ProgressBar (`R2-BUG-02`)
  - **Vai trò**: Modern Home & State Sync Engineer
  - **Mô tả**: Thêm cơ chế phát hiện đổi trạng thái `isCatalogItemWatched` trong `LeanbackRowAdapter` để rebind đúng thẻ thay đổi; bổ sung chữ ký watched vào `remember` của `FolderDetailScreen`; truyền `blurred` trong prefetch `ContinueWatchingItem.NextUp` ở `LeanbackRowAdapter` & `ModernHomeRowsList`; đồng bộ `themeStyle.accentColor` cho `progressBar` Modern Home.
  - **File**: [LeanbackModernRowAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRowAdapter.kt), [LeanbackModernRow.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt), [ModernHomeRowsList.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeRowsList.kt), [FolderDetailScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/collection/FolderDetailScreen.kt)

- [x] **LBR2-02**: Sửa lỗi mất khôi phục `initialFocusPosition` khi `items` nạp chậm async (`R2-BUG-03`)
  - **Vai trò**: Compose-Leanback Lifecycle Engineer
  - **Mô tả**: Khởi tạo `lastAppliedInitialPosition = -1` trong `LeanbackNativeRow` và `LeanbackModernRow`; chỉ cập nhật sau khi áp dụng `setSelectedPosition` thành công trên danh sách không rỗng kèm xử lý attach an toàn.
  - **File**: [LeanbackNativeRow.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/LeanbackNativeRow.kt), [LeanbackModernRow.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt)

- [x] **LBR2-03**: Chuẩn hóa `InteropVerticalGridView` (`focusSearch` & biên), Focus Guard và `unbind()` nhánh Universal (`R2-BUG-04`, `R2-BUG-06`)
  - **Vai trò**: Android TV Focus & Recycling Engineer
  - **Mô tả**: Thêm `.coerceAtMost(totalItems - 1)` vào `focusSearch(FOCUS_DOWN)` và tái sử dụng `isVerticalGridLastRow`; chuẩn hóa `handleBoundaryNavigation` + expose `onNavigateRightOnLastColumn`; thêm check `mayTake` ở `rv.post` thứ 2 của `LeanbackUniversalAdapter`; reset sạch marquee/badge/progress trong `unbind()` của `LeanbackPosterViewHolder` & `LeanbackEpisodeViewHolder` và lọc `isPlaceholder()` ở `LeanbackEpisodeViewHolder`.
  - **File**: [InteropVerticalGridView.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/container/InteropVerticalGridView.kt), [LeanbackNativeGrid.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/LeanbackNativeGrid.kt), [LeanbackUniversalAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/adapter/LeanbackUniversalAdapter.kt), [LeanbackPosterViewHolder.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/holder/LeanbackPosterViewHolder.kt), [LeanbackEpisodeViewHolder.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/holder/LeanbackEpisodeViewHolder.kt)

- [x] **LBR2-04**: Sửa 3 điểm rớt Focus ở `SearchDiscoverSection`, `FolderDetailScreen` và `CollectionSection` (`R2-BUG-05`)
  - **Vai trò**: Screen Focus Integration Engineer
  - **Mô tả**: Gắn `firstItemFocusRequester` vào `LeanbackNativeGrid` trong `DiscoverGrid`; thêm auto-focus Tab đầu khi mở `FolderDetailScreen` nhiều tab lần đầu và nối `gridScrollToTopTrigger`; nối `isFocusEnabled = !blockDefaultRestore` và `focusProperties { canFocus = false }` trong `CollectionSection`.
  - **File**: [SearchDiscoverSection.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/search/SearchDiscoverSection.kt), [FolderDetailScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/collection/FolderDetailScreen.kt), [CollectionSection.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/detail/CollectionSection.kt)

### Phase 2: Hoàn Thiện Viền `RAINBOW`, Theme, `AppFont` & Modern UX Polish
- [x] **LBR2-05**: Khôi phục `PosterBorderStyle.RAINBOW`, tách chuẩn màu `SOLID_*`/`THEME`/`NONE`, áp dụng `borderWidthDp` & đồng bộ `AppFont` (`R2-BUG-01`, `R2-CLEAN-03`)
  - **Vai trò**: TV Design System & Canvas Graphics Engineer
  - **Mô tả**: Expose `LocalAppFont` từ `Theme.kt`; mở rộng `LeanbackThemeStyle` với `borderStyle`, `gradientColors`, `typeface` (cached); tạo `LeanbackFocusBorderDrawable` vẽ cả solid lẫn `LinearGradient` cầu vồng theo `borderWidthDp` cho `LeanbackBaseViewHolder` và tắt `glowView` khi `NONE`; nâng cấp `ModernRowFloatingFocusRingView` vẽ `LinearGradient` cho `RAINBOW`; gán `typeface` vào các `TextView` của 4 ViewHolder.
  - **File**: [Theme.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/theme/Theme.kt), [FocusRingStyle.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/theme/FocusRingStyle.kt), [LeanbackThemeStyle.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/core/LeanbackThemeStyle.kt), [LeanbackBaseViewHolder.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/holder/LeanbackBaseViewHolder.kt), [ModernRowFloatingFocusRingView.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/ModernRowFloatingFocusRingView.kt), [LeanbackPosterViewHolder.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/holder/LeanbackPosterViewHolder.kt), [LeanbackEpisodeViewHolder.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/holder/LeanbackEpisodeViewHolder.kt), [LeanbackAvatarViewHolder.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/holder/LeanbackAvatarViewHolder.kt), [LeanbackModernRowAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRowAdapter.kt)

- [x] **LBR2-06**: Hoàn thiện RTL `progressBar`, Fallback Title, A11y Modern Home, tối ưu `watchedSignature` & chặn copy Hardware Bitmap lúc hold-scroll (`R2-BUG-07`, `R2-BUG-08`, `R2-BUG-09`)
  - **Vai trò**: Native View Polish & Performance Engineer
  - **Mô tả**: Đổi `marginStart`/`marginEnd` cho `progressBar` Modern Home, bổ sung `fallbackTitleText` khi ảnh lỗi & `showLabels == false`, thêm `", Watched"` vào `contentDescription`; tối ưu `watchedSignature` trong `CatalogRowSection` tránh cấp phát List mỗi recomposition; bỏ qua trích màu trên Hardware Bitmap khi `isHoldScrolling == true` ở `LeanbackColorEngine`.
  - **File**: [LeanbackModernRowAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRowAdapter.kt), [CatalogRowSection.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/components/CatalogRowSection.kt), [LeanbackColorEngine.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/core/LeanbackColorEngine.kt)

### Phase 3: Dọn Dead Code Còn Sót, Changelog Rollback & Kiểm Thử
- [x] **LBR2-07**: Dọn sạch 4 callback chết trên 2 Adapter, bỏ `setItemViewCacheSize(12)` trùng, bỏ cấp phát thừa `listState` ở 4 Detail Sections & dọn `FilmographyRow` (`R2-CLEAN-01`, `R2-CLEAN-02`)
  - **Vai trò**: Refactoring & Code Hygiene Engineer
  - **Mô tả**: Xóa `onNavigateDown/Up/Left/Back` không còn dùng khỏi `LeanbackUniversalAdapter` và `LeanbackRowAdapter` (cập nhật các file gọi + unit test); xóa `setItemViewCacheSize(12)` thừa ở `LeanbackNativeGrid`; đổi `listState: LazyListState? = null` ở `CastSection`, `CollectionSection`, `MoreLikeThisSection`, `TrailerSection`; dọn tham số thừa & `rowKey` trong `CastDetailScreen`.
  - **File**: [LeanbackUniversalAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/adapter/LeanbackUniversalAdapter.kt), [LeanbackNativeRow.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/LeanbackNativeRow.kt), [LeanbackNativeGrid.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/LeanbackNativeGrid.kt), [LeanbackModernRowAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRowAdapter.kt), [LeanbackModernRow.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt), [CastSection.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/detail/CastSection.kt), [CollectionSection.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/detail/CollectionSection.kt), [MoreLikeThisSection.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/detail/MoreLikeThisSection.kt), [TrailerSection.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/detail/TrailerSection.kt), [CastDetailScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/cast/CastDetailScreen.kt)

- [x] **LBR2-08**: Cập nhật bổ sung mục Vòng 2 (`R2-BUG-01`..`R2-CLEAN-03`) vào [LEANBACK_FIX_CHANGELOG.md](file:///f:/MyGithub/NuvioTV-Fast/LEANBACK_FIX_CHANGELOG.md)
  - **Vai trò**: Documentation & Rollback Maintainer
  - **Mô tả**: Ghi rõ từng mã sửa Vòng 2 kèm vị trí file/dòng và cách khôi phục độc lập theo yêu cầu người dùng.

- [x] **LBR2-09**: Bổ sung Unit Tests Vòng 2, chạy `testFullDebugUnitTest` & `assembleFullRelease` + xác minh trên MuMu Android TV
  - **Vai trò**: QA / Verification Engineer
  - **Mô tả**: Thêm test cho `PosterBorderStyle` (đủ 7 chế độ), Modern Home watched reactivity, blur prefetch parity, partial last-row `focusSearch`; chạy test và kiểm thử thực tế trên emulator.
  - **File**: [LeanbackCoreOptimizationTest.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/test/java/com/nuvio/tv/ui/leanback/LeanbackCoreOptimizationTest.kt)

---

# Danh Sách Task - Kiểm Toán & Tối Ưu Toàn Diện UX/UI Android Leanback (`tet2`)

> **Kế hoạch tương ứng**: [PLAN-leanback-ux-ui-audit.md](file:///f:/MyGithub/NuvioTV-Fast/PLAN-leanback-ux-ui-audit.md)
> **Phạm vi**: Khắc phục 17 lỗi (3 P0, 7 P1, 7 P2), dọn 8 hạng mục thừa/dead code, hoàn thiện 8 khoảng trống UX/UI/Theme/Accessibility trên toàn bộ hệ thống Leanback (`ui/leanback/` & `ui/screens/home/`)

---

## 📋 Task Breakdown

### Phase 1: Khắc Phục Lỗi P0 Nghiêm Trọng (Focus & Ảnh Trống)
- [x] **LB-01**: Sửa triệt để lỗi kẹt `LeanbackArtworkLoader.isHoldScrolling` và ảnh đen trống sau khi dừng cuộn nhanh (`BUG-01`)
  - **Vai trò**: Leanback Core & Image Loading Engineer
  - **Mô tả**: Reset `isHoldScrolling = false` trong `finishOverlayRingToCurrentGrid()`, refresh ảnh cho toàn bộ hàng visible khi `endVerticalHoldScroll()` kết thúc, và thêm `rebindMissingImages()` trong `onScrollSettled()` của cả `LeanbackUniversalAdapter` & `LeanbackRowAdapter`.
  - **File**: [ModernHomeRowsList.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeRowsList.kt), [LeanbackUniversalAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/adapter/LeanbackUniversalAdapter.kt), [LeanbackModernRowAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRowAdapter.kt)

- [x] **LB-02**: Sửa công thức `isLastRow` và lỗi `ACTION_UP` giật ngược focus + nuốt phím biên trong `InteropVerticalGridView` (`BUG-02`, `BUG-03`)
  - **Vai trò**: Android TV Focus Engine Engineer
  - **Mô tả**: Đổi điều kiện hàng cuối sang `(actualPos / cols) >= (totalItems - 1) / cols`; thêm cờ `isNavigatingOutOfGrid` ngăn `ACTION_UP` giật focus ngược sau khi thoát sang Filter/Tab/Sidebar; xử lý trả về đúng cho callback biên khi trả `false`.
  - **File**: [InteropVerticalGridView.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/container/InteropVerticalGridView.kt)

- [x] **LB-03**: Loại bỏ double-dispatch callback điều hướng và bảo vệ `updateConfig()` khỏi crash đang layout (`BUG-09`, `BUG-10`)
  - **Vai trò**: Leanback Adapter Engineer
  - **Mô tả**: Gỡ gọi trùng `onNavigateUp/Down/Back` ở `root.setOnKeyListener` trong `LeanbackCardViewHolder` & `LeanbackBaseViewHolder`; bọc `notifyItemRangeChanged` trong `updateConfig()` bằng kiểm tra `rv.isComputingLayout || rv.isInLayout`.
  - **File**: [LeanbackBaseViewHolder.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/holder/LeanbackBaseViewHolder.kt), [LeanbackUniversalAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/adapter/LeanbackUniversalAdapter.kt), [LeanbackModernRowAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRowAdapter.kt)

### Phase 2: Ổn Định Dữ Liệu, Grid Thích Ứng & Tắt Hiệu Ứng Đúng Cài Đặt
- [x] **LB-04**: Sửa reactivity trạng thái Watched, debounce `onLoadMore` và chống giật ngược scroll khi recompose (`BUG-04`, `BUG-12`, `BUG-16`)
  - **Vai trò**: Compose-Leanback Interop Engineer
  - **Mô tả**: Đưa trạng thái `isWatched` / `searchWatched*` vào key `remember(...)` ở `CatalogRowSection` và `CatalogSeeAllScreen`; thêm debounce `250ms` cho `onLoadMore` ở `LeanbackUniversalAdapter`; chỉ gọi `setSelectedPosition` trong `update` khi `initialFocusPosition` thực sự thay đổi.
  - **File**: [CatalogRowSection.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/components/CatalogRowSection.kt), [CatalogSeeAllScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/CatalogSeeAllScreen.kt), [LeanbackNativeRow.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/LeanbackNativeRow.kt), [LeanbackModernRow.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt), [LeanbackUniversalAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/adapter/LeanbackUniversalAdapter.kt)

- [x] **LB-05**: Kiểm soát auto-focus và tính số cột thích ứng (adaptive columns) cho `LeanbackNativeGrid` (`BUG-06`, `BUG-07`)
  - **Vai trò**: TV Layout & Focus Engineer
  - **Mô tả**: Thêm `requestInitialFocus: Boolean` cho `LeanbackNativeGrid` để không giật focus khỏi thanh Filter/Tab; tạo helper `computeAdaptiveGridColumns` áp dụng cho `CatalogSeeAllScreen`, `SearchDiscoverSection`, `FolderDetailScreen`.
  - **File**: [LeanbackNativeGrid.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/LeanbackNativeGrid.kt), [CatalogSeeAllScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/CatalogSeeAllScreen.kt), [SearchDiscoverSection.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/search/SearchDiscoverSection.kt), [FolderDetailScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/collection/FolderDetailScreen.kt)

- [x] **LB-06**: Tắt software blur glow của `ModernRowFloatingFocusRingView` khi `animationsEnabled == false` (`BUG-08`)
  - **Vai trò**: Rendering & Performance Engineer
  - **Mô tả**: Truyền `animationsEnabled` xuống `ModernRowFloatingFocusRingView` và `VerticalRingOverlayHost`, vô hiệu hóa `LAYER_TYPE_SOFTWARE` + `BlurMaskFilter` khi người dùng tắt hiệu ứng.
  - **File**: [ModernRowFloatingFocusRingView.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/ModernRowFloatingFocusRingView.kt), [LeanbackModernRow.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt), [ModernHomeRowsList.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeRowsList.kt)

### Phase 3: Hoàn Thiện UX/UI, Đồng Bộ `NuvioTheme` & Parity Tính Năng
- [x] **LB-07**: Đồng bộ `NuvioTheme`, `PosterBorderStyle`, `AppFont` và co giãn vùng nhãn theo `fontScale` (`BUG-14`)
  - **Vai trò**: TV Design System Engineer
  - **Mô tả**: Xây dựng `LeanbackThemeStyle` kết nối token màu, `PosterBorderStyle` (`THEME`/`RAINBOW`/`SOLID_*`/`NONE`), font chữ và độ dày viền nhất quán tới toàn bộ ViewHolder & Floating Ring; tính `labelHeightDp` động theo `fontScale` và loại thẻ.
  - **File**: `app/src/main/java/com/nuvio/tv/ui/leanback/core/LeanbackThemeStyle.kt` (mới), [LeanbackBaseViewHolder.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/holder/LeanbackBaseViewHolder.kt), [LeanbackNativeRow.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/LeanbackNativeRow.kt), [LeanbackModernRow.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt), [ModernRowFloatingFocusRingView.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/ModernRowFloatingFocusRingView.kt)

- [x] **LB-08**: Sửa bo góc Episode gradient, RTL margins, thẻ `See All`, Fallback Avatar/Poster, Marquee, phím `MENU` & `contentDescription` (`BUG-11`, `BUG-13`, `BUG-15`)
  - **Vai trò**: Native View UX Polish Engineer
  - **Mô tả**: Bo góc `gradientView` ở `LeanbackEpisodeViewHolder`; chuyển sang `marginStart`/`marginEnd`; bổ sung giao diện thẻ `See All` có icon mũi tên; thêm initials/icon fallback cho Avatar & tiêu đề fallback khi Poster lỗi ảnh; bật marquee khi focus; lắng nghe `KEYCODE_MENU`; gán `contentDescription` cho TalkBack.
  - **File**: [LeanbackEpisodeViewHolder.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/holder/LeanbackEpisodeViewHolder.kt), [LeanbackPosterViewHolder.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/holder/LeanbackPosterViewHolder.kt), [LeanbackAvatarViewHolder.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/holder/LeanbackAvatarViewHolder.kt), [LeanbackCardItem.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/model/LeanbackCardItem.kt), [CatalogRowSection.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/components/CatalogRowSection.kt)

- [x] **LB-09**: Khôi phục Parity hiển thị trên Modern Home (`Watched`, `Blur Unwatched CW`, `CoverEmoji`, `IMDb/AirDate Badge`) (`BUG-05`)
  - **Vai trò**: Modern Home UI Engineer
  - **Mô tả**: Nối `isCatalogItemWatched`, `blurUnwatchedEpisodes` và `coverEmoji` (CollectionFolder) xuống `LeanbackCardViewHolder`, đồng bộ các badge còn thiếu với trải nghiệm chuẩn của ứng dụng.
  - **File**: [ModernHomeRowsList.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeRowsList.kt), [ModernHomeRows.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeRows.kt), [LeanbackModernRowAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRowAdapter.kt)

### Phase 4: Tinh Gọn Kiến Trúc, Dọn Code Thừa & Tối Ưu RAM Ảnh
- [x] **LB-10**: Hợp nhất tiện ích lõi (`BlurFilterCache`, `PrefetchController`), rút gọn `LeanbackColorEngine` và xóa toàn bộ Dead Code
  - **Vai trò**: Architecture & Refactoring Engineer
  - **Mô tả**: Gộp 2 cache `BlurMaskFilter` & logic prefetch giữa Universal và Modern; xóa `dominantColorCache` trùng lặp + unused import `LruCache` trong `LeanbackColorEngine`; dọn `isLandscape` unused, `borderWidthPx`/`glowMarginPx` thừa, `onNavigateRight` chết, `listState` thừa ở 4 Detail Sections, bỏ gọi trùng `setItemViewCacheSize`, sửa side-effect trong setter `canTakeFocus`.
  - **File**: [LeanbackColorEngine.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/core/LeanbackColorEngine.kt), [PlayStoreGlowView.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/core/PlayStoreGlowView.kt), [ModernRowFloatingFocusRingView.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/ModernRowFloatingFocusRingView.kt), [ModernInteropHorizontalGridView.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/ModernInteropHorizontalGridView.kt), [InteropHorizontalGridView.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/container/InteropHorizontalGridView.kt), [CastSection.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/detail/CastSection.kt), [CollectionSection.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/detail/CollectionSection.kt), [MoreLikeThisSection.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/detail/MoreLikeThisSection.kt), [TrailerSection.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/detail/TrailerSection.kt)

- [x] **LB-11**: Hỗ trợ an toàn `Hardware Bitmaps` trong `LeanbackArtworkLoader` & `LeanbackColorEngine` (`BUG-17`)
  - **Vai trò**: Performance & Graphics Engineer
  - **Mô tả**: Bảo vệ `LeanbackColorEngine` khỏi `IllegalStateException` khi gặp `Bitmap.Config.HARDWARE`; gỡ ép cứng `.allowHardware(false)` (chỉ giữ khi `blurred == true`) để `HardwareBitmapSettingInterceptor` hoạt động đúng trên toàn bộ poster Leanback.
  - **File**: [LeanbackArtworkLoader.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/core/LeanbackArtworkLoader.kt), [LeanbackColorEngine.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/core/LeanbackColorEngine.kt)

### Phase 5: Mở Rộng Độ Phủ (Tùy Chọn) & Kiểm Thử Toàn Diện
- [ ] **LB-12**: *(Tùy chọn)* Chuyển lưới poster Tab Saved của `LibraryScreen` sang `LeanbackNativeGrid` với adaptive columns
  - **Vai trò**: Library Screen Engineer
  - **Mô tả**: Chỉ triển khai sau khi Phase 1–2 ổn định và được xác nhận phạm vi; đảm bảo giữ trọn điều hướng lên bộ lọc `LibrarySelectorsRow`, mở Sidebar ở cột 0 và khôi phục focus khi quay về từ Detail.
  - **File**: [LibraryScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/library/LibraryScreen.kt)

- [x] **LB-13**: Bổ sung bộ Unit Tests (`InteropVerticalGridView`, `HoldScrollRecovery`, `AdaptiveColumns`, `HardwareBitmapColor`, `LoadMoreDebounce`) & Nghiệm thu trên Android TV
  - **Vai trò**: QA / Verification Engineer
  - **Mô tả**: Viết unit tests khóa chặt toàn bộ lỗi P0/P1 đã sửa, chạy `./gradlew :app:compileFullDebugKotlin :app:testFullDebugUnitTest` và xác minh checklist thủ công trên thiết bị/emulator Android TV.
  - **File**: [LeanbackCoreOptimizationTest.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/test/java/com/nuvio/tv/ui/leanback/LeanbackCoreOptimizationTest.kt)

---

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
