# PLAN-sync-dev-2026-11-17: Kế hoạch Đồng bộ Toàn bộ Commit Mới từ Nhánh `dev` (`origin/dev`) sang `tet2`

> [!IMPORTANT]
> **Mục tiêu cốt lõi:** Cập nhật toàn bộ **48 commits mới nhất** (từ `2026-11-15` đến `2026-11-17`) từ nhánh `origin/dev` (`fc5c10c3d`, trùng khớp 100% với `upstream/dev` trên remote) vào nhánh hiện tại `tet2` (`af7887221`, tag `v1.5.6`), **xử lý toàn bộ 5 xung đột trực tiếp và các xung đột ngầm trong 21 tệp auto-merged**, **giữ nguyên toàn bộ các tùy chỉnh đã thêm trên `tet2`**, và **thay thế/hòa giải các phần mà code mới từ `dev` làm tốt hơn kèm báo cáo chi tiết**.

> [!WARNING]
> **Phát hiện Quan trọng từ Mô phỏng Merge (`git merge-tree --write-tree`):**
> 1. **Lỗi Crash Runtime Ẩn trong Auto-Merge:** Git tự gộp sạch về mặt văn bản tại [SubtitleAppearanceEditor.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/settings/SubtitleAppearanceEditor.kt), nhưng lại sinh ra **2 khối `item(key = "outline_width")` trùng key** trong cùng `LazyColumn` -> Sẽ gây crash `IllegalArgumentException: Key "outline_width" was already used` khi mở Cài đặt Giao diện Phụ đề.
> 2. **Xung đột Thang đo & Cơ chế `outlineWidth`:** `tet2` dùng thang `1..50` (mặc định `15`) kết hợp `OutlineSpan` trên ExoPlayer `SubtitleView`, trong khi `origin/dev` (PR #4649) đưa ra cơ chế mới thang `1..5` (mặc định `2`) tự động kích hoạt `StyledSubtitleOverlay` + `StyledSubtitleText` khi `outlineWidth != OUTLINE_WIDTH_DEFAULT` và vẽ nét viền theo tỉ lệ cỡ chữ. Nếu giữ mặc định `15` mà dùng `StyledSubtitle.kt` mới, phụ đề sẽ bị **viền dày gấp 7.5 lần che mất chữ** và làm **fail unit test `StyledSubtitlePresenterTest`**.
> 3. **Thay đổi Chưa Commit trong Working Tree:** [build.gradle.kts](file:///f:/MyGithub/NuvioTV-Fast/app/build.gradle.kts) hiện đang có sửa đổi chưa commit (`versionCode = 1197 -> 1198`, `versionName = "1.5.5" -> "1.5.6"`). Cần stash/xử lý sạch trước khi chạy `git merge`.
> 4. **Local `dev` tụt hậu:** Nhánh local `dev` đang dừng ở `a3532b819` (`2026-11-14`), trong khi `origin/dev` và `upstream/dev` đã có đầy đủ tới `fc5c10c3d` (`2026-11-17`). Phải merge từ `origin/dev` (và có thể fast-forward local `dev` theo `origin/dev`).

---

## 1. Hiện trạng Git & Phạm vi Đồng bộ

| Thông số | Giá trị thực tế quan sát |
| :--- | :--- |
| **Nhánh hiện tại** | `tet2` (`af7887221` — `feat: implement live tv guide redesign and navigation components`, tag `v1.5.6`) |
| **Nhánh nguồn cập nhật** | `origin/dev` (`fc5c10c3d` — `Merge pull request #4596 from ysosrs123/pr/silo-server`, khớp `upstream/dev` trên GitHub) |
| **Điểm tách nhánh (`merge-base`)** | `6ab8a5735` (`2026-11-15 01:13:36 +0100` — PR #4629, đã được sync vào `tet2` tại commit `0d4d2df26`) |
| **Số commit mới trên `origin/dev`** | **48 commits** (16 PR/merge commits), tác động **104 files** |
| **Số commit/tùy chỉnh riêng trên `tet2`** | **25 commits** kể từ `0d4d2df26` (và **502 files** khác biệt từ `6ab8a5735..tet2`) |
| **Giao điểm file sửa đổi hai phía** | **26 files** (gồm **5 file CONFLICT trực tiếp** + **21 file AUTO-MERGED**) |

---

## 2. Báo cáo Chi tiết 48 Commits Mới từ `origin/dev` (15/11/2026 – 17/11/2026)

| Nhóm / PR | Commit đại diện | Nội dung & Tệp chính | Đánh giá tác động lên `tet2` |
| :--- | :--- | :--- | :--- |
| **1. Plex Media Server** (PR #4595) | `f12c35d0d`, `9af02059a`, `4247d5cb1` | Thêm `PlexProvider`, `PlexClient`, `PlexAccountClient`, `PlexMapper`, `PlexArtwork`, `PlexSignInDialog`, `plex_logo.svg`; cập nhật `ServerProvider`, `ServerRepository`, `ServerSync`, `ServerPlayback`, `MediaServerModule`, `NuvioApplication`. | **Bổ sung tính năng mới sạch.** Cần giữ `ArcDns`/`IPv4FirstDns` của `tet2` trong `MediaServerModule` và `ArtworkDiagnostics`/`SavedCopyOnErrorFetcher` trong `NuvioApplication`. |
| **2. Silo Media Server** (PR #4596) | `fc5c10c3d`, `f0f1ffc31`, `c7e7a4338`, `bb7b85d36` | Thêm `SiloProvider`, `SiloClient`, `SiloArtwork`, `SiloMapper`, `SiloPlayback`, `SiloSignInDialog`, `silo_logo.svg`; tách luồng đăng nhập chung `ServerSignInRoute`, `ServerSignInSteps`; cập nhật `strings_servers.xml`. | **Bổ sung tính năng mới sạch.** Không xung đột với `LiveTv` hay `TorrServer`. |
| **3. Stremio Tracking Addons** (PR #4633) | `9ce49f451`, `868e6a4f0`, `9c86bb0e0`, `d1215e03e` | Thêm `AddonEventDispatcher`, `TrackingAddonConsentController`, `TrackingAddonConsentDialog`; cập nhật `AddonWebPage`, `DeepLinkHandler`, `MainActivity`, `AddonManagerScreen`, `AddonManagerViewModel`, `LibraryRepositoryImpl`, `WatchProgressRepositoryImpl`, `PlayerRuntimeController`, `PlayerRuntimeControllerPlaybackEvents`, `PlayerViewModel`. | **Overlap nhiều file cốt lõi** (`MainActivity`, `AddonManager*`, `LibraryRepositoryImpl`, `WatchProgressRepositoryImpl`, `PlayerRuntimeController*`, `PlayerViewModel`). Auto-merge giữ được cả hai phía, cần kiểm tra chéo constructor và dialog consent. |
| **4. Subtitle Outline Width & Styled Overlay** (PR #4649 + commits phụ đề) | `727b34258`, `2491dccef`, `2c5398e85`, `eacad43c0`, `1768224fb` | Thêm kiểm soát `outlineWidth` (`1..5`, mặc định `2`), vẽ viền trực tiếp trong `StyledSubtitleText`, hỗ trợ màu phụ đề file (`ForegroundColorSpan`, ASS `&HBBGGRR&`, HDR brightness), định vị 9 vùng (`TOP`/`MIDDLE`/`BOTTOM` × `START`/`CENTER`/`END`, ASS `\an1..9`), tính `outlineSize` trên MPV (`outlineWidth * 0.5` cho text, `1..6` cho ASS). | **XUNG ĐỘT TRỰC TIẾP & XUNG ĐỘT LOGIC LỚN NHẤT** với custom `outlineWidth` (`1..50`) + `OutlineSpan` + `SubtitleFontOption` của `tet2`. Xem phân tích chi tiết ở Mục 3. |
| **5. Nâng cấp `mpv-android-lib:0.2.1`** (PR #4670) | `48d64499e`, `b3d0d294a` | Bump `mpv-android-lib` lên `0.2.1`, tạo `NuvioBaseMpvView` thay `BaseMPVView` (do thư viện 0.2 bỏ khả năng re-init sau `destroy()`), đổi `setOptionString("profile", "fast")` -> `command("apply-profile", "fast")`. | **Xung đột tại `NuvioMpvSurfaceView.kt` và `app/build.gradle.kts`.** Cần nhận `NuvioBaseMpvView` + `0.2.1` để tái sử dụng MPV view khi đổi nguồn/restart, đồng thời giữ toàn bộ tùy chỉnh IPTV/Font/Cache của `tet2`. |
| **6. Episode Ratings Alignment** (PR #4676) | `2ab44c6c7`, `e2ecb1c5f` | Thêm `EpisodeRatingsAlignment`, cập nhật `SeriesGraphApi`, `ImdbEpisodeRatingsRepository`, `MetaDetailsViewModel`, `HomeViewModelContinueWatching` để khớp điểm IMDb theo ngày phát hành tập của addon. | **Cải tiến tốt hơn.** Gộp sạch với `MetaDetailsViewModel` và `HomeViewModelContinueWatching` của `tet2`. |
| **7. TorBox Alternative-Hash Cache Fix** (PR #4669) | `7e9115f5c`, `89989cf2b` | Thêm `resolveTorboxAliasMatches` trong `LocalDebridService` + unit test `LocalDebridServiceTest`. | **Sửa lỗi thực tế tốt.** Không overlap với `tet2`. |
| **8. Sửa Focus UI & Bàn phím Ambient** (#4626, #4661, #4662, #4664) | `e0e830b60`, `dc4ee4e38`, `c628e6894`, `2575ddb19` | Ẩn bàn phím khi vào Ambient Mode; sửa focus ban đầu tab Import ở `CollectionManagementScreen`; sửa theme switch GIF ở `CollectionEditorFolderContent`; sửa focus D-pad giữa panel trái và `AvatarPickerGrid` ở `ProfileSelectionScreen` + test `ProfileEditorFocusTest`. | **Sửa lỗi UX/TV focus tốt.** Không làm mất tùy chỉnh nào của `tet2`. |
| **9. Bản dịch i18n** (#4650, #4651, #4674, #4678) | `8f07453ef`, `81cc39005`, `60eb9602a`, `4c33217cb` | Cập nhật `values-fr`, `values-pl`, `values-vi`, và viết lại/bổ sung `values-pt-rPT/strings.xml`, `strings_party.xml`, `strings_servers.xml`. | **Xung đột XML tại `values/strings.xml` và `values-pt-rPT/strings.xml`.** Cần giữ chuỗi tùy chỉnh của `tet2` và phòng lỗi `aapt2` với dấu `%`. |
| **10. Version Bump `origin/dev`** | `2c853c178` | Bump `versionCode = 1078 -> 1079`, `versionName = "1.2.0-beta.1" -> "1.2.0-beta.2"`. | **Bỏ qua số version này**, giữ version của nhánh `NuvioTV-Fast` (`tet2`: `1198` / `"1.5.6"` hoặc tăng theo ý người dùng). |

---

## 3. Phân tích Chuyên sâu: Tùy chỉnh `tet2` vs Code Mới từ `dev` (Giữ nguyên hay Thay thế?)

### 3.1. Danh sách Tùy chỉnh Cốt lõi của `tet2` Cần Giữ Nguyên 100%
1. **Live TV & EPG Engine:** Toàn bộ package `features/livetv/` (EPG database, XMLTV parser, Synthetic EPG, CatchupUrlBuilder, LiveTvGuideScreen 3 cột, LiveTvSettingsScreen, LiveTvPlaybackController, ClearKey DRM, M3U/Xtream/Stalker, nút Sidebar & `StartupTab`).
2. **Leanback & Home Performance Pipeline:** Toàn bộ `ui/leanback/`, `NativeSidebarView`, `ModernHomeRowsList`, `LeanbackModernRow`, `DpadFastScrollModifier`, `sharedLeanbackPool` trong `MainActivity`.
3. **Image & Network Optimization:** `ArtworkMemoryCache`, `ArtworkDiagnostics`, `SavedCopyOnErrorFetcher`, `sizedHomeArtworkUrl` (TMDB downscaler), `ImageDiskCacheSize`, `onTrimMemory`/`onLowMemory` trong `NuvioApplication`, `ArcDns` + `IPv4FirstDns` + `NoDelaySocketFactory` trong `MediaServerModule` & OkHttp.
4. **TorrServer Integration & UI:** `TorrServerRemoteApi`, `TorrServerAddonConfig`, `TorrServerSettingsScreen`, Torrent File Picker, tắt seed/peer badge cho TorrServer & hiển thị tốc độ theo `bps/Kbps/Mbps` trong `PlayerRuntimeControllerPlaybackEvents.kt`.
5. **Audio Surround & Player Watchdogs:** `PlayerTunnelAvSyncPolicy`, `AudioRejectionReverifier`, Startup Watchdog (`STARTUP_WATCHDOG_TIMEOUT_MS`, thông báo lỗi codec/timeout), `demuxerCacheBytes()`, `demuxerCacheSpeedBps()`, `IptvHeaderProvider.mergeWithDefaults(...)`.
6. **App Branding, UI Settings & Updater:** `applicationId = "com.nuvio.tv.fast"`, `app_name = "Nuvio TV"`, `GITHUB_OWNER = "9000000"`, `GITHUB_REPO = "NuvioTV-Fast"`, `animationsEnabled`, `posterBorderStyle`, `gpuOffscreenCompositingEnabled`, `Force re-download update`, `RefreshAddonsEntryCard` spinning indicator (`isRefreshingAddons`).
7. **Custom Subtitle Fonts:** 5 font nhúng (`PhimMoi`, `Oswald`, `Inter`, `Open Sans`, `DM Sans`) + chọn font `.ttf/.otf` từ bộ nhớ (`SubtitleFontStore` + `SubtitleFontOption` + `ensureCustomFontsExtracted` cho MPV).

---

### 3.2. Phân tích Chi tiết 5 File Xung đột Trực tiếp & Các Điểm Cần Đánh giá Thay thế

#### A. Điểm nóng #1 — Cơ chế Độ dày Viền Phụ đề (`outlineWidth`) & Render Phụ đề
*(Liên quan: [PlayerSettingsDataStore.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/data/local/PlayerSettingsDataStore.kt), [NuvioMpvSurfaceView.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/NuvioMpvSurfaceView.kt), [StyledSubtitle.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/StyledSubtitle.kt), [SubtitleAppearanceEditor.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/settings/SubtitleAppearanceEditor.kt), [SubtitleSelectionOverlay.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/SubtitleSelectionOverlay.kt), [SubtitleStyleSidePanel.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/SubtitleStyleSidePanel.kt), [OutlineSpan.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/OutlineSpan.kt))*

| Tiêu chí | Code hiện tại trên `tet2` | Code mới trên `origin/dev` (PR #4649) | Đánh giá & Đề xuất |
| :--- | :--- | :--- | :--- |
| **Thang đo `outlineWidth`** | `1..50` (mặc định `15`), hiển thị `"${width}px"` | `OUTLINE_WIDTH_MIN = 1`, `OUTLINE_WIDTH_MAX = 5`, `OUTLINE_WIDTH_DEFAULT = 2`, hiển thị số `1..5` | **Code mới tốt hơn về mặt chuẩn hóa**, nhưng cần hỏi/báo cáo rõ vì sẽ đổi giá trị cài đặt cũ của user. |
| **Render trên ExoPlayer** | Dùng [OutlineSpan.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/OutlineSpan.kt) chèn `TextPaint.strokeWidth = outlineWidth.toFloat()` vào `SubtitleView` native (theo pixel cứng, không scale theo cỡ chữ và không hỗ trợ màu/vị trí 9 vùng khi bật StyledOverlay). | `StyledSubtitle.usesStyledRenderer` tự bật khi `outlineEnabled && outlineWidth != 2`. [StyledSubtitle.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/StyledSubtitle.kt) vẽ `Stroke(width = style.outlineWidth * unit)` tỉ lệ theo cỡ chữ + giữ nguyên `ForegroundColorSpan`, màu ASS, HDR brightness và căn 9 vị trí qua `StyledSubtitleOverlay`. | **Code mới của `origin/dev` tốt hơn hẳn!** Nếu vẫn giữ `outlineWidth = 15` (mặc định) kết hợp với `StyledSubtitle.kt` mới thì viền chữ sẽ dày gấp 7.5x (che kín chữ) và fail test `StyledSubtitlePresenterTest`. |
| **Render trên MPV** | `style.outlineWidth.coerceIn(1, 50).toDouble()` | Text subtitle: `style.outlineWidth * 0.5` (`0.5..2.5`); ASS/SSA: `style.outlineWidth.coerceIn(1, 6).toDouble()` | Code mới phân biệt đúng giữa ASS/SSA và text SRT/VTT (tránh ASS bị vỡ layout). Nếu user thích viền đậm hơn trên MPV có thể mở rộng mức tối đa hoặc giữ `1..5` với bước `0.5` hoặc `0.75`. |
| **Custom Fonts (`PhimMoi`, `Oswald`, `Inter`, `Open Sans`, `DM Sans`, Custom Storage)** | `tet2` tích hợp `PHIMMOI` & `OSWALD` vào `SubtitleFonts` (`StyledSubtitle.kt` + `SubtitleFontStore`), và giữ `SubtitleFontOption` (`INTER`, `OPENSANS`, `DMSANS`, `CUSTOM`) trong `PlayerSettingsDataStore`, `PlaybackSubtitleSettings`, `PlayerScreen`, `NuvioMpvSurfaceView`. | `origin/dev` chỉ có `DEFAULT`, `SERIF`, `MONO` và custom upload web (`SubtitleFontStore`). | **GIỮ NGUYÊN 100% Custom Fonts của `tet2`** trên cả `SubtitleStyleSettings.fontId`, `SubtitleStyleSettings.font`, `StyledSubtitle.kt` (`PHIMMOI`, `OSWALD`), `PlayerScreen.kt` và `NuvioMpvSurfaceView.kt`! |

> [!TIP]
> **Hai phương án xử lý cho `outlineWidth` (sẽ xác nhận với User trước khi sửa code):**
> - **Phương án A (Khuyến nghị):** Chuyển sang dùng hệ thống `outlineWidth` mới của `origin/dev` (`OUTLINE_WIDTH_MIN = 1`, `OUTLINE_WIDTH_MAX = 5`, `OUTLINE_WIDTH_DEFAULT = 2`) đồng bộ trên `PlayerSettingsDataStore` (coerce dữ liệu cũ nếu lỡ lưu > 5 về `1..5`), `SubtitleAppearanceEditor` (xóa slider trùng key, giữ 1 slider `1..5`), `SubtitleStyleSidePanel` (`1..5`), `SubtitleSelectionOverlay` (`1..5`), `StyledSubtitle.kt`, `NuvioMpvSurfaceView.kt`; và **bỏ `OutlineSpan` khi đã có `StyledSubtitleOverlay`** (hoặc giữ `OutlineSpan` chỉ là dead-safe fallback / vô hiệu hóa gọi trùng ở `PlayerRuntimeControllerInitialization` & `PlayerSidecarSubtitles` để không vẽ viền 2 lần).
> - **Phương án B (Giữ dải `1..50` kiểu `tet2`):** Giữ slider `1..50` (mặc định `15`), sửa công thức ở `StyledSubtitle.kt` (`usesStyledRenderer` so với `15` và `outline = (style.outlineWidth / 7.5f) * unit / 2f`) và sửa `NuvioMpvSurfaceView` + cập nhật unit test `StyledSubtitlePresenterTest`. Cách này giữ UI cũ `1-50px` nhưng phức tạp và dễ lệch giữa ExoPlayer/MPV/ASS.

---

#### B. Điểm nóng #2 — `NuvioMpvSurfaceView.kt` & `mpv-android-lib:0.2.1`
- **Thay đổi của `origin/dev` (PR #4670):**
  - Đổi thư viện `io.github.abdallahmehiz:mpv-android-lib:0.1.12` -> `0.2.1`.
  - Tạo [NuvioBaseMpvView.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/NuvioBaseMpvView.kt) vì `mpv-android-lib 0.2.1` tạo instance ngay trong constructor `MPV(context)` và xóa `destroy()` khỏi `BaseMPVView` của thư viện.
  - Đổi superclass `NuvioMpvSurfaceView` từ `BaseMPVView` -> `NuvioBaseMpvView`, và đổi `mpv.setOptionString("profile", "fast")` -> `mpv.command("apply-profile", "fast")` trong `initOptions()`.
- **Tùy chỉnh của `tet2` phải giữ nguyên:**
  - `demuxerCacheBytes()`, `demuxerCacheSpeedBps()`.
  - `ensureCustomFontsExtracted(context)` (giải nén 5 font `.ttf` ra `filesDir/fonts/`) + `mpv.setOptionString("sub-fonts-dir", fontsDir.absolutePath)` trong `initOptions()`.
  - Ánh xạ `fontName` từ `style.fontId` và `style.font` (`UVN Hong Ha Hep, PhimMoi`, `Inter`, `Open Sans`, `DM Sans 9pt`, `Oswald`, `Roboto`) + `mpv.setPropertyString("sub-font", fontName)`.
  - `applyHeaders()` gọi `IptvHeaderProvider.mergeWithDefaults(currentUrl, headers)` cho luồng Live TV/IPTV.
  - Giữ việc loại bỏ dead method `applyCoverAspectScale()` (vì `tet2` dùng `resolveAspectScale`).
- **Quyết định:** **Áp dụng kiến trúc `NuvioBaseMpvView` + `0.2.1` của `origin/dev`** (bắt buộc để tương thích `0.2.1` và không lỗi crash khi re-init/đổi nguồn), **giữ 100% tùy chỉnh MPV của `tet2`**.

---

#### C. Điểm nóng #3 — `app/build.gradle.kts`
- **Tùy chỉnh của `tet2` giữ nguyên:**
  - Các hằng số fallback `officialSupabaseUrl`, `officialSupabaseAnonKey`, `officialTrailerApiUrl`, `officialTvLoginWebBaseUrl`, `officialDeviceLoginWebBaseUrl`, `officialSupportersApiBaseUrl`, `officialSupportUrl`, `officialPlaybackReportsBaseUrl`, `officialAvatarPublicBaseUrl`.
  - `applicationId = "com.nuvio.tv.fast"`, `GITHUB_OWNER = "9000000"`, `GITHUB_REPO = "NuvioTV-Fast"`.
  - Playstore flavor bật `FEATURE_IN_APP_TRAILERS_ENABLED = true`, cấu hình `signingConfigs` an toàn khi thiếu keystore, `applicationVariants` xuất APK `NuvioTV-${versionName}-${abi}.apk`, `lint` (`MissingTranslation`, `ExtraTranslation`), `baselineProfile` bỏ filter.
  - Dependencies bổ sung của `tet2`: `androidx.leanback:leanback:1.0.0`, `libs.okhttp.dnsoverhttps`, `io.coil-kt.coil3:coil:3.3.0`.
- **Thay đổi từ `origin/dev` lấy vào:**
  - Nâng `mpv-android-lib:0.1.12` -> `0.2.1`.
  - **Không lấy** `versionCode = 1079` / `versionName = "1.2.0-beta.2"` của `origin/dev`.
- **Uncommitted change hiện tại:** Working tree đang sửa `versionCode = 1197 -> 1198`, `versionName = "1.5.5" -> "1.5.6"`. Kế hoạch sẽ **giữ `1198` / `"1.5.6"`** (hoặc nâng lên `1199` / `"1.5.7"` nếu user muốn đánh dấu bản sau sync).

---

#### D. Điểm nóng #4 — `values/strings.xml` & `values-pt-rPT/strings.xml` (+ `values-vi/strings.xml`)
- **`values/strings.xml`:**
  - Giữ `app_name` = `Nuvio TV`, giữ `formatted="false"` ở `supporters_contributors_donation_progress_remaining` và `playback_cache_info_auto`, giữ toàn bộ các nhóm strings tùy chỉnh của `tet2` (Appearance Animations, Poster Border, Force Update, GPU Offscreen, Audio Surround, Custom Subtitle Fonts, Startup Tab, Stream Search, Refresh Addons, Live TV, Player Buttons, Đơn vị `bps/Kbps/Mbps`, TorrServer, Watchdog Errors).
  - Gộp thêm từ `origin/dev`: 7 strings `addon_tracking_consent_*` (PR #4633) và giải quyết trùng tag `sub_outline_width` (tùy chọn giữ mô tả `sub_outline_width_sub` nếu theo Phương án B hoặc giữ/chỉnh mô tả cho thang `1-5` nếu theo Phương án A).
- **`values-pt-rPT/strings.xml`:**
  - Áp dụng bản dịch đầy đủ mới nhất từ PR #4678 (`origin/dev`) cùng 2 file mới `strings_party.xml` & `strings_servers.xml`, đồng thời kiểm tra giữ `formatted="false"` tại `playback_cache_info_auto` (chứa `10%`) và `supporters_contributors_donation_progress_remaining` nếu aapt2 yêu cầu.
- **`values-vi/strings.xml`:**
  - Auto-merge giữ nguyên toàn bộ strings tùy chỉnh của `tet2` (`Nuvio TV`, Live TV, TorrServer, Animations, Poster Border, Watchdog...) và tự động thêm `addon_tracking_consent_*` + `library_empty_*` đã dịch Tiếng Việt từ `origin/dev`.
  - Bổ sung kiểm tra bản dịch tiếng Việt cho `sub_outline_width` / `strings_servers.xml` nếu cần (không bắt buộc vì `lint` đã tắt `MissingTranslation`, nhưng giúp UX tiếng Việt đồng bộ hơn).

---

### 3.3. Kiểm tra Chéo 21 File Auto-Merged (Tránh Lỗi Ngầm / Semantic Conflicts)

| STT | Tệp | `tet2` sửa gì | `origin/dev` sửa gì | Rủi ro ngầm & Hành động |
| :-: | :--- | :--- | :--- | :--- |
| 1 | [NuvioApplication.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/NuvioApplication.kt) | `ArcDns.init`, `LiveTvStorage.init`, `onTrimMemory`/`onLowMemory`, `ArtworkDiagnostics`, `sizedHomeArtworkUrl`, `SavedCopyOnErrorFetcher`, `ArtworkMemoryCache`, `pictureMemoryCacheBytes`, `imageDiskCacheBytes`, `pictureMaxBitmapSide`, `HOME_POSTER_BITMAP_PARALLELISM`. | Inject `plexArtworkSigner`, `siloArtworkRefresher`; thêm 3 interceptors (`PlexArtworkInterceptor`, `PlexArtworkInterceptor.Token`, `SiloArtworkInterceptor`) vào `imageOkHttpClient`. | **Thấp.** Đã verify trong `merge-tree`: gộp sạch cả hai. Cần kiểm tra `sizedHomeArtworkUrl` không ảnh hưởng URL Plex/Silo (chỉ rewrite `image.tmdb.org`). |
| 2 | [MainActivity.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/MainActivity.kt) | `NativeSidebarView`, `LiveTv` drawer/route, `StartupTab`, splash keep 1500ms, `sharedLeanbackPool`, `animationsEnabled`, `posterBorderStyle`, `gpuOffscreenCompositingEnabled`, `LocalOpenSidebar`, `LocalRequestUpdateBannerFocus`. | Inject `trackingAddonConsentController`; hiển thị `TrackingAddonConsentDialog`; chặn toast khi `installResult is DeepLinkInstallResult.ConsentRequired`. | **Thấp.** Auto-merge sạch. Kiểm tra lại thứ tự dialog trên Compose root & focus không bị đè bởi `NativeSidebarView`. |
| 3 | [MediaServerModule.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/core/di/MediaServerModule.kt) | Gắn `.dns(IPv4FirstDns(ArcDns.withSystemRetry))` + `.socketFactory(NoDelaySocketFactory)`. | Thêm `PlexProvider(http, clientIdentity)`, `SiloProvider(http, clientIdentity)` vào `ServerRepository`. | **An toàn 100%.** Gộp sạch cả DNS tùy chỉnh lẫn 2 provider mới. |
| 4 | [LibraryRepositoryImpl.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/data/repository/LibraryRepositoryImpl.kt) | Dọn import thừa & xóa `hydrateLibraryLogos`. | Inject `AddonEventDispatcher`, bọc `toggleDefault` & `applyMembershipChanges` bằng `withAddonLibraryEvent`. | **An toàn.** Kiểm tra lại constructor trong `LibraryRepositoryTrackingTest.kt`. |
| 5 | [WatchProgressRepositoryImpl.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/data/repository/WatchProgressRepositoryImpl.kt) | Dọn import thừa. | Inject `AddonEventDispatcher`, phát sự kiện `WATCHED`/`UNWATCHED` ở `removeFromHistory*` và `markAsCompleted*`. | **An toàn.** Kiểm tra các unit test `WatchProgress*Test.kt` chạy pass. |
| 6 | [AddonManagerScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/addon/AddonManagerScreen.kt) | Nâng cấp `RefreshAddonsEntryCard` (hiện `isRefreshing`, icon xoay `rememberInfiniteTransition`, `LoadingIndicator`). | Thêm `TrackingAddonConsentDialog` trong pending dialog, đổi `onConfirm = { viewModel.confirmPendingChange() }`, sửa `LaunchedEffect` focus ở `ConfirmAddonChangesDialog`. | **An toàn.** Cần kiểm tra thêm [EssentialAddonSetupScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/addon/EssentialAddonSetupScreen.kt) xem có cần hiển thị `TrackingAddonConsentDialog` tương tự khi setup lần đầu không. |
| 7 | [AddonManagerUiState.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/addon/AddonManagerUiState.kt) | Thêm `isRefreshingAddons: Boolean`. | Thêm `trackingAddonNames`, `trackingConsentUrls`, `awaitingTrackingConsent` vào `PendingChangeInfo`. | **An toàn 100%.** Hai data class/vị trí tách biệt. |
| 8 | [AddonManagerViewModel.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/addon/AddonManagerViewModel.kt) | Thay `requestAddonSyncNow()` dùng `syncAndRefreshAddonsNow()` kèm state `isRefreshingAddons` và message chi tiết. | Inject `TrackingAddonConsentController`, thêm luồng kiểm tra/xác nhận consent khi cài từ URL hoặc QR web (`confirmPendingChange(trackingConsentGiven: Boolean = false)`). | **An toàn.** Kiểm tra compile với mọi caller của `confirmPendingChange`. |
| 9 | [AvatarPickerGrid.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/components/AvatarPickerGrid.kt) | Dọn import thừa. | Thêm `entryFocusRequester`, `leftFocusRequester`, `key(filteredAvatars)`, `lastFocusedAvatarId` cho D-pad TV. | **An toàn 100%.** |
| 10 | [CollectionEditorFolderContent.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/collection/CollectionEditorFolderContent.kt) | Dọn import thừa. | Fix màu switch `focusGifEnabled` dùng theme. | **An toàn 100%.** |
| 11 | [CollectionManagementScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/collection/CollectionManagementScreen.kt) | Dọn import thừa. | Focus ban đầu vào tab `FILE` khi mở màn hình Import. | **An toàn 100%.** |
| 12 | [MetaDetailsViewModel.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/detail/MetaDetailsViewModel.kt) | Dọn import thừa. | Truyền thêm `addonEpisodes` vào `imdbEpisodeRatingsRepository.getEpisodeRatings(...)`. | **An toàn 100%.** |
| 13 | [HomeViewModelContinueWatching.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/HomeViewModelContinueWatching.kt) | `.debounce { if (_initialCwResolved.value) CW_PROGRESS_DEBOUNCE_MS else 0L }` + xóa 2 helper thừa. | Truyền `addonEpisodes` vào `imdbEpisodeRatingsRepository.getEpisodeRatings(...)` ở 2 chỗ. | **An toàn 100%.** Giữ được tối ưu load CW tức thì lúc khởi động của `tet2`. |
| 14 | [PlayerRuntimeController.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeController.kt) | Thêm `torrServerRemoteApi`, `torrServerAddonConfig`, `isLiveTv`, `currentDrmType/Key`, tunnel/audio/startup watchdog state, `scheduleOwnedPlaybackRecovery`. | Thêm `addonEventDispatcher`, `activeAddonPlayerEventTarget`, `lastAddonPlayerAction`. | **An toàn.** Kiểm tra thứ tự tham số constructor giữa `PlayerRuntimeController` và `PlayerViewModel`. |
| 15 | [PlayerRuntimeControllerPlaybackEvents.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt) | Ẩn seed/peer khi `isTorrServerStream`, tốc độ `bps/Kbps/Mbps`, handler `OnTorrentFileSelected`, `OnSetSubtitleOutlineWidth`, đồng bộ `setSubtitleFont`. | Gọi `emitAddonPlayerEvent` ở `START`, `PAUSE`, `STOP`, seek, ended. | **An toàn.** Nếu chuyển sang Phương án A cho `outlineWidth`, xác nhận handler `OnSetSubtitleOutlineWidth` lưu đúng thang `1..5`. |
| 16 | [PlayerViewModel.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerViewModel.kt) | Inject `torrServerRemoteApi`, `torrServerAddonConfig` và truyền vào `PlayerRuntimeController`. | Inject `addonEventDispatcher` và truyền vào `PlayerRuntimeController`. | **An toàn.** Đã kiểm tra cây `merge-tree`: cả 3 tham số đều hiện diện khớp constructor. |
| 17 | [StyledSubtitle.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/StyledSubtitle.kt) | Thêm `SubtitleFonts.PHIMMOI` và `SubtitleFonts.OSWALD` trong `builtInSubtitleFontFamily`. | Cập nhật `usesStyledRenderer`, hỗ trợ căn ngang `horizontal`, giữ màu gốc (`mapColors`, `subtitleFileColor`), `outline = style.outlineWidth * unit / 2f`. | **CẨN TRỌNG CAO!** Phụ thuộc trực tiếp quyết định Phương án A hay B cho `outlineWidth` (xem Mục 3.2.A). |
| 18 | [SubtitleAppearanceEditor.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/settings/SubtitleAppearanceEditor.kt) | Thêm `item(key = "outline_width")` (1..50px) **trước** `outline_color`. | Thêm `item(key = "outline_width")` (1..5) **sau** `outline_color`. | **CRASH RUNTIME NẾU KHÔNG SỬA!** Trùng key `"outline_width"` trong `LazyColumn`. Bắt buộc hợp nhất thành 1 item duy nhất. |
| 19-21 | `values-fr/strings.xml`, `values-pl/strings.xml`, `values-vi/strings.xml` | Tinh chỉnh bản dịch tiếng Việt & các string riêng `tet2`. | Bổ sung bản dịch `addon_tracking_consent_*`, `library_empty_*`, v.v. | **An toàn.** Chạy kiểm tra XML well-formed & duplicate `<string name=...>` trong `aapt2`. |

---

## 4. Chuỗi Phụ thuộc Thực thi (Dependency Chains)

```text
[T01 Backup & Stash]
        │
        ▼
[T02 Merge origin/dev --no-commit]
        │
        ├──► [T03 Resolve app/build.gradle.kts] (mpv 0.2.1 + tet2 config/version)
        │
        ├──► [T04 Resolve Subtitle Architecture]
        │       ├── PlayerSettingsDataStore.kt
        │       ├── NuvioMpvSurfaceView.kt (+ NuvioBaseMpvView.kt)
        │       ├── SubtitleAppearanceEditor.kt (Fix duplicate LazyColumn key!)
        │       ├── StyledSubtitle.kt + SubtitleStyleSidePanel.kt + SubtitleSelectionOverlay.kt + OutlineSpan.kt
        │       └── StyledSubtitlePresenterTest.kt / MpvSubtitleTextTest.kt
        │
        ├──► [T05 Resolve Strings XML]
        │       ├── values/strings.xml
        │       ├── values-pt-rPT/strings.xml (+ strings_party.xml, strings_servers.xml)
        │       └── values-vi/strings.xml (bổ sung chuỗi còn thiếu nếu cần)
        │
        └──► [T06 Verify Auto-Merged & Cross-File Integrity]
                ├── NuvioApplication.kt + MediaServerModule.kt (Plex & Silo + ArcDns/ImageCache)
                ├── MainActivity.kt + AddonManager* + EssentialAddonSetupScreen.kt
                └── PlayerViewModel.kt + PlayerRuntimeController*.kt + Repositories
                        │
                        ▼
               [T07 Build & Unit Tests Verification]
                        │
                        ▼
               [T08 Commit Merge & Restore Local Dev Ref]
```

---

## 5. Phân rã Kế hoạch Thực thi Chi tiết (Task-by-Task Breakdown)

### Phase 0: Chuẩn bị An toàn & Lưu trữ Hiện trạng
- [ ] **T01 — Sao lưu & Làm sạch Working Tree:**
  - Tạo nhánh backup tại HEAD hiện tại của `tet2`: `git branch backup-tet2-before-merge-20261117 tet2`.
  - Lưu thay đổi chưa commit của [build.gradle.kts](file:///f:/MyGithub/NuvioTV-Fast/app/build.gradle.kts) (`versionCode = 1198`, `versionName = "1.5.6"`) vào `git stash push -m "wip-version-bump-1.5.6" -- app/build.gradle.kts` (hoặc giữ giá trị để áp dụng lại ở T03).
  - Kiểm tra `git status` sạch hoàn toàn và chạy `git fetch origin` (nếu cần đồng bộ mới nhất trước giờ chạy).

### Phase 1: Thực hiện Merge Có Kiểm soát
- [ ] **T02 — Chạy Merge Không Tự động Commit:**
  - Chạy `git merge --no-commit --no-ff origin/dev` để nạp toàn bộ 48 commits mới vào staging/working tree và dừng lại ở trạng thái đang giải quyết conflict để rà soát cả 5 file conflict lẫn 21 file auto-merged trước khi tạo merge commit.

### Phase 2: Giải quyết 5 File Conflict Trực tiếp
- [ ] **T03 — Giải quyết [build.gradle.kts](file:///f:/MyGithub/NuvioTV-Fast/app/build.gradle.kts):**
  - Giữ toàn bộ phần tùy chỉnh `tet2` (`official*`, `com.nuvio.tv.fast`, `9000000/NuvioTV-Fast`, signingConfigs an toàn, `applicationVariants` naming, `lint`, `baselineProfile`, dependencies Leanback/DoH/Coil).
  - Cập nhật `implementation("io.github.abdallahmehiz:mpv-android-lib:0.2.1")` từ `origin/dev`.
  - Áp dụng lại `versionCode = 1198`, `versionName = "1.5.6"` (hoặc nâng theo xác nhận của user) và dọn `git stash drop` sau khi xác nhận khớp.

- [ ] **T04 — Giải quyết [PlayerSettingsDataStore.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/data/local/PlayerSettingsDataStore.kt) & [NuvioMpvSurfaceView.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/NuvioMpvSurfaceView.kt):**
  - Trong `PlayerSettingsDataStore.kt`:
    - Giữ nguyên `SubtitleFontOption`, `font: String = SubtitleFontOption.DEFAULT`, `subtitleFontKey`, `setSubtitleFont()`, và ánh xạ trong `setSubtitleFontId()` của `tet2`.
    - Với `outlineWidth` (theo Phương án A khuyến nghị, hoặc Phương án B nếu user chọn): sử dụng `OUTLINE_WIDTH_MIN = 1`, `OUTLINE_WIDTH_MAX = 5`, `OUTLINE_WIDTH_DEFAULT = 2` kèm `.coerceIn(...)` khi đọc DataStore (tự động giới hạn giá trị cũ như `15` từ phiên bản trước về hợp lệ trong `1..5`) và giữ `fileColorBrightness: Float = 1f` trong `SubtitleStyleSettings` + `forVideo()`.
  - Trong `NuvioMpvSurfaceView.kt`:
    - Kế thừa `NuvioBaseMpvView(context, attrs)` thay `BaseMPVView` và chuyển `mpv.command("apply-profile", "fast")` theo `origin/dev` (PR #4670).
    - Giữ nguyên `demuxerCacheBytes()`, `demuxerCacheSpeedBps()`, `ensureCustomFontsExtracted(context)`, `sub-fonts-dir`, khối tính `fontName` (gồm `SubtitleFonts` & `SubtitleFontOption`), `mpv.setPropertyString("sub-font", fontName)`, và `applyHeaders()` với `IptvHeaderProvider.mergeWithDefaults(...)`.
    - Áp dụng logic `outlineSize` của `origin/dev` (`MPV_TEXT_OUTLINE_SIZE_PER_WIDTH = 0.5` cho text, `coerceIn(1, 6)` cho ASS/SSA — hoặc theo phương án user chọn).

- [ ] **T05 — Giải quyết [values/strings.xml](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/res/values/strings.xml) & [values-pt-rPT/strings.xml](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/res/values-pt-rPT/strings.xml):**
  - Trong `values/strings.xml`:
    - Giữ `app_name = Nuvio TV`, giữ `formatted="false"` và toàn bộ chuỗi `tet2` (Live TV, TorrServer, Custom Fonts, Animations, Poster Border, Startup Tab, Surround Audio, Player Buttons, Đơn vị tốc độ mạng `bps/Kbps/Mbps`, Watchdog Errors).
    - Gộp `addon_tracking_consent_*` từ `origin/dev`; giữ một thẻ `<string name="sub_outline_width">Outline Width</string>` duy nhất (cập nhật/giữ `sub_outline_width_sub` tương ứng phương án `outlineWidth` đã chọn).
  - Trong `values-pt-rPT/strings.xml`:
    - Lấy bản dịch đầy đủ từ `origin/dev` (PR #4678), giữ `formatted="false"` tại `playback_cache_info_auto` (chứa `10%`) và `supporters_contributors_donation_progress_remaining` để tránh lỗi lint/aapt2.

### Phase 3: Xử lý Xung đột Ngầm trong 21 File Auto-Merged & Các File Liên quan
- [ ] **T06 — Sửa [SubtitleAppearanceEditor.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/settings/SubtitleAppearanceEditor.kt) & Đồng bộ Hệ thống Phụ đề:**
  - **Bắt buộc:** Gộp 2 khối `item(key = "outline_width")` bị trùng sau auto-merge thành **1 item duy nhất** tại vị trí hợp lý (ngay sau `outline_color` hoặc trước), đồng bộ `minValue`/`maxValue`/`valueText` theo phương án `outlineWidth` đã thống nhất.
  - Đồng bộ [SubtitleStyleSidePanel.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/SubtitleStyleSidePanel.kt) và [SubtitleSelectionOverlay.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/SubtitleSelectionOverlay.kt) dùng cùng dải `OUTLINE_WIDTH_MIN..OUTLINE_WIDTH_MAX` (nếu theo Phương án A) và giữ UI chọn font `SubtitleFontIds` của `tet2`.
  - Đồng bộ [OutlineSpan.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/OutlineSpan.kt), [PlayerRuntimeControllerInitialization.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt) và [PlayerSidecarSubtitles.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerSidecarSubtitles.kt): khi `usesStyledRenderer` đã xử lý `outlineWidth != OUTLINE_WIDTH_DEFAULT`, `SubtitleView` native chỉ nhận bitmap cues (hoặc khi ở default `outlineWidth = 2`, giữ `OutlineSpan` không chạy hoặc scale nhẹ `2f` tránh vẽ nét quá dày).

- [ ] **T07 — Rà soát Kiểm tra Chéo Tích hợp Tracking Addons, Media Servers & Core:**
  - Kiểm tra [NuvioApplication.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/NuvioApplication.kt) & [MediaServerModule.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/core/di/MediaServerModule.kt): xác nhận Plex/Silo artwork interceptors và providers hoạt động cùng `ArcDns`, `SavedCopyOnErrorFetcher`, `ArtworkMemoryCache`.
  - Kiểm tra [MainActivity.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/MainActivity.kt), [AddonManagerScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/addon/AddonManagerScreen.kt), [AddonManagerViewModel.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/addon/AddonManagerViewModel.kt), và [EssentialAddonSetupScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/addon/EssentialAddonSetupScreen.kt): xác nhận luồng `TrackingAddonConsentDialog` không xung đột với `NativeSidebarView` hoặc `RefreshAddonsEntryCard`.
  - Kiểm tra [PlayerViewModel.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerViewModel.kt), [PlayerRuntimeController.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeController.kt), [PlayerRuntimeControllerPlaybackEvents.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt): xác nhận chữ ký constructor và event `AddonPlayerAction` hoạt động đúng với cả VOD lẫn Live TV (lưu ý `isShortPlaceholderStream()` / `isLiveTv` để tránh gửi nhầm sự kiện tracking Stremio cho kênh Live TV nếu `contentType == "livetv"` — kiểm tra `AddonEventDispatcher` chỉ gửi khi có tracking addon cho `movie`/`series`).
  - Kiểm tra [values-vi/strings.xml](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/res/values-vi/strings.xml): xác nhận không trùng khóa XML và bổ sung dịch tiếng Việt cho chuỗi mới còn thiếu nếu phù hợp.

### Phase 4: Kiểm thử, Sửa lỗi Phát sinh & Hoàn tất Merge
- [ ] **T08 — Chạy Kiểm tra Biên dịch Toàn bộ Dự án (Automated Build Check):**
  - Chạy `./gradlew :app:compileFullDebugKotlin :app:assembleFullDebug` (và `:app:lintFullDebug` hoặc kiểm tra XML resources) để chắc chắn không lỗi compile, Hilt DI, hay `aapt2`.
- [ ] **T09 — Chạy Unit Tests & Sửa Lỗi Phát sinh (Automated Test Check):**
  - Chạy `./gradlew :app:testFullDebugUnitTest` (chú trọng các bộ test mới/ảnh hưởng: `StyledSubtitlePresenterTest`, `StyledSubtitlePlacementTest`, `SubtitleStyleHdrOverrideTest`, `MpvSubtitleTextTest`, `PlayerSubtitleUtilsMergeTest`, `PlexProviderTest`, `SiloProviderTest`, `AddonEventDispatcherTest`, `EpisodeRatingsAlignmentTest`, `LibraryRepositoryTrackingTest`, `TrackingPlaybackScenariosTest`, `WatchProgress*Test`, `LocalDebridServiceTest`, `LiveTvGuideNavigationTest`, `SearchViewModelDiscoverRecoveryTest`).
- [ ] **T10 — Commit Merge & Cập nhật Nhánh Local:**
  - Rà soát `git diff --staged` và `git status` để đảm bảo không dư thừa code hay mất tùy chỉnh.
  - Tạo merge commit chuẩn (ví dụ: `Merge branch 'origin/dev' into tet2: sync 48 commits (2026-11-15 to 2026-11-17)`).
  - (Tùy chọn theo người dùng) Fast-forward local `dev` theo `origin/dev`.

---

## 6. Kế hoạch Kiểm chứng Thủ công (Manual Verification trên Thiết bị Android TV)

1. **Live TV & Leanback Home:** Mở ứng dụng kiểm tra Splash, Native Sidebar, mở màn hình Live TV 3 cột, chuyển kênh, preview, EPG dọc, ClearKey stream, tốc độ load poster Home.
2. **Phụ đề ExoPlayer & MPV (Quan trọng nhất):**
   - Vào *Cài đặt → Phát lại → Phụ đề → Giao diện*: Kiểm tra **không bị crash** khi cuộn qua `Outline Width`, thử thay đổi `Outline Width`, `Font` (`PhimMoi`, `Oswald`, `Inter`, `Open Sans`, `DM Sans`, `Custom Font` từ bộ nhớ), màu sắc và HDR override.
   - Phát 1 phim bằng **ExoPlayer** (kiểm tra SRT có màu/vị trí `{\an8}` + độ dày viền hiển thị đúng, không dày quá mức) và chuyển sang **MPV** (`mpv-android-lib:0.2.1` với `NuvioBaseMpvView`) kiểm tra đổi nguồn/restart MPV mượt và hiển thị font tiếng Việt (`PhimMoi`/`Oswald`) chuẩn.
3. **Media Servers (Plex & Silo):** Vào *Cài đặt → Media servers*, kiểm tra hiển thị logo & màn hình đăng nhập code cho Plex/Silo.
4. **Addons & Tracking Consent:** Vào *Addons*, bấm nút *Refresh Addons* kiểm tra animation xoay của `tet2` vẫn hoạt động; thử luồng cài addon mới hiển thị dialog xác nhận nếu là tracking addon.
5. **TorrServer:** Phát một torrent qua TorrServer kiểm tra file picker và thanh trạng thái tốc độ `Mbps`/`Kbps` không hiện nhầm seed/peer.

---

## 7. Các Điểm Cần Người Dùng Xác nhận Trước Khi Thực thi

> [!NOTE]
> Trước khi bắt đầu bước merge và sửa code, vui lòng xác nhận giúp 2 quyết định nhỏ sau:
> 1. **Cách xử lý `outlineWidth` (Mục 3.2.A):** Chọn **Phương án A (Khuyến nghị)** — chuyển sang thang `1..5` (mặc định `2`) dùng công thức chuẩn của `origin/dev` cho cả `StyledSubtitleOverlay` (ExoPlayer) và `NuvioMpvSurfaceView` (MPV), vẫn giữ nguyên 100% hệ thống Custom Fonts của bạn; hay **Phương án B** — giữ nguyên giao diện thang `1..50` (mặc định `15`) rồi quy đổi tỉ lệ sang renderer mới?
> 2. **Số phiên bản trong `app/build.gradle.kts`:** Giữ nguyên thay đổi đang có trong working tree là `versionCode = 1198` / `versionName = "1.5.6"`, hay nâng lên `1199` / `"1.5.7"` cho bản sync này?
