# Kế hoạch đồng bộ nhánh `dev` → `tet2` (2026-11-07)

> Tài liệu này được tạo **trước khi thực hiện merge**.
> Mọi số liệu, commit, file và xung đột dưới đây đã được kiểm tra bằng lệnh Git thật trên repository `F:\MyGithub\NuvioTV-Fast`.
> Công cụ dự báo xung đột: `git merge-tree --write-tree --name-only tet2 origin/dev`.

---

## 1. Tóm tắt điều hành

| Hạng mục | Giá trị |
|---|---|
| Nhánh đích | `tet2` |
| HEAD hiện tại | `df8b0480c` — docs: add 2026-11-05 dev sync plan, conflict report, and result report |
| Thay đổi cục bộ chưa commit | `app/build.gradle.kts` — versionCode `1174` → `1175`, versionName `1.3.2` → `1.3.3` |
| Nguồn đồng bộ | `origin/dev` = `upstream/dev` = `fe5c314bd` — bump version (2026-11-06) |
| Merge base | `1206736fd` — bump version (2026-11-05), mốc merge lần trước |
| Phạm vi người dùng yêu cầu | Các commit mới nhất của `dev` từ 2026-11-05 đến hôm nay (2026-11-07) |
| Phạm vi thực tế còn thiếu | **26 commit** sau mốc `1206736fd` (16 commit thường + 10 merge commit) |
| Thay đổi upstream | **34 file**, `+1.836 / −204` dòng |
| Thay đổi custom của `tet2` so với base | 352 file, `+42.247 / −3.598` dòng |
| File giao nhau | 20 file |
| Xung đột văn bản dự báo | **2 file**: `app/build.gradle.kts`, `app/src/main/res/values-ja/strings.xml` |
| Chiến lược | Backup → commit thay đổi cục bộ → merge `--no-ff` → xử lý 2 xung đột → review ngữ nghĩa 18 file auto-merge → validate XML/key/symbol → compile + test trọng tâm → báo cáo kết quả |
| Push | **Không push**; để người dùng kiểm tra trước |

### Kết luận kiểm tra phạm vi ngày

- Lần đồng bộ trước đã merge `dev` đến `1206736fd` (bump version ngày 2026-11-05).
- Từ sau mốc đó, `dev` có thêm **26 commit**, mới nhất là `fe5c314bd` (bump version 2026-11-06).
- Trong 26 commit này có 3 commit được tác giả viết trước ngày 2026-11-05 nhưng chỉ được merge vào `dev` ngày 2026-11-06:
  - `5d925b210` — Auto-scroll truncated hero descriptions on Home (2026-10-23)
  - `2670d72de` — Address review feedback on auto-scrolling hero descriptions (2026-11-02)
  - `42843eabc` — Fix stuck carousel hold and visible jump on trailer pause (2026-11-04)
- Nếu lọc thuần theo ngày tác giả sẽ bỏ sót 3 commit quan trọng của tính năng hero auto-scroll. Vì vậy phạm vi đúng là **toàn bộ commit sau mốc merge `1206736fd`**, tương đương "tất cả commit mới nhất từ `dev` kể từ lần đồng bộ ngày 2026-11-05".
- Tính đến thời điểm lập kế hoạch, `dev` chưa có commit nào mới hơn `fe5c314bd` trong ngày 2026-11-07.

---

## 2. Xác minh ref và môi trường

```text
origin    https://github.com/9000000/NuvioTV-Fast.git
upstream  https://github.com/NuvioMedia/NuvioTV.git

tet2                      df8b0480c
tet2 (sau commit local)   sẽ là commit bảo toàn version 1175 / 1.3.3
origin/dev                fe5c314bd
upstream/dev              fe5c314bd
local dev                 1206736fd  (ancestor của origin/dev, có thể fast-forward)
merge-base(tet2, dev)     1206736fd
```

- Git: `2.56.0.windows.4`
- Gradle wrapper: `8.14.5`
- Gradle launcher JVM: `21.0.11` (JetBrains Runtime), phù hợp JDK 21
- Android SDK: `C:\Users\NongTuan\AppData\Local\Android\Sdk`
- Biến thể build kiểm tra: `:app:compileFullDebugKotlin`
- Biến thể unit test: `:app:testFullDebugUnitTest`

### Thay đổi cục bộ đang có (phải bảo toàn)

```text
modified:   app/build.gradle.kts
            versionCode 1174 -> 1175
            versionName "1.3.2" -> "1.3.3"
```

Kế hoạch: tạo backup branch + patch dự phòng, sau đó commit thay đổi này thành một commit bảo toàn **trước khi merge**. Như vậy version `1.3.3` không bị mất và xung đột version sẽ được xử lý có chủ đích.

---

## 3. Danh sách 26 commit sẽ merge

### 3.1 Nhóm Hero auto-scroll / Home (PR #4224)

| # | Hash | Ngày | Nội dung |
|---|---|---|---|
| 1 | `5d925b210` | 2026-10-23 | Auto-scroll truncated hero descriptions on Home |
| 2 | `2670d72de` | 2026-11-02 | Address review feedback on auto-scrolling hero descriptions |
| 3 | `42843eabc` | 2026-11-04 | Fix stuck carousel hold and visible jump on trailer pause |
| 4 | `136392bd9` | 2026-11-06 | Merge pull request #4224 from marezezelj/feat/hero-description-auto-scroll |

**Tác động chính:**

- Thêm mới `AutoScrollingText.kt` (229 dòng): text quá dài tự cuộn dọc, có fade trên/dưới, dừng khi trailer đang phát, tôn trọng cài đặt giảm animation.
- Thêm setting `hero_description_auto_scroll_enabled` (mặc định tắt) vào `LayoutPreferenceDataStore`.
- Thêm state `heroDescriptionAutoScrollEnabled` vào `HomeUiState` và pipeline Home.
- Thêm toggle trong `LayoutHomeSettings` + event/state trong `LayoutSettingsViewModel`.
- Thay `Text` bằng `AutoScrollingText` trong `HeroCarousel` và `ModernHomeHero`.
- Giữ chỗ (hold) auto-advance của carousel cho đến khi đoạn mô tả dài cuộn xong lượt đầu.
- Thêm 2 string tiếng Anh: `layout_hero_description_auto_scroll`, `layout_hero_description_auto_scroll_sub`.
- Truyền `descriptionAutoScrollEnabled` qua `ClassicHomeContent`, `GridHomeContent`, `ModernHomeContent`.

### 3.2 Nhóm Player / phụ đề / Exo end-overrun

| # | Hash | Ngày | Nội dung |
|---|---|---|---|
| 5 | `19b8a0c2f` | 2026-11-06 | fix(player): keep restored subtitle preference and let AutoSync search further |
| 6 | `105490c2d` | 2026-11-06 | fix(player): end Exo playback when video renderer never reaches end of stream |
| 7 | `fe8dd2e44` | 2026-11-06 | Merge pull request #4495 from skoruppa/fix/exo-end-overrun |
| 8 | `248ed08bf` | 2026-11-06 | Merge pull request #4494 from skoruppa/fix/track-pref-wiped-after-restore |

**Tác động chính:**

- Thêm `exoEndOverrunHandled` + `checkExoPlaybackOverrun(...)`: nếu audio đã kết thúc nhưng video renderer không bao giờ đọc được EOS, tự kết thúc playback tự nhiên sau 1 giây vượt duration thay vì treo màn hình đen.
- Reset cờ overrun khi: khởi tạo player, attach/initialize MPV, chuyển stream, chọn episode mới, reset loading overlay.
- Thêm `loadedTrackPreference`: giữ snapshot preference đã load từ storage để không bị ghi đè khi AutoSync reset delay hoặc khi `persistedTrackPreference` đã bị tiêu thụ.
- Đổi AutoSync khi khôi phục phụ đề đã chọn: thêm scope `SELECTED_THEN_SEARCH` — thử phụ đề đã chọn trước; nếu không khớp mới tìm các phụ đề cùng ngôn ngữ khác.
- Thêm `searchAfterSelectedFailed(...)` để tránh tìm vô ích khi video không có track phụ đề hoặc không có reference.
- Cập nhật `PlayerRuntimeControllerAutomaticSubtitleSync` cho cả 3 đường Exo, MPV, libass.

### 3.3 Nhóm Anime-Skip / Skip Intro

| # | Hash | Ngày | Nội dung |
|---|---|---|---|
| 9 | `6312eb2a8` | 2026-11-06 | fix(skip-intro): drop open-ended Anime-Skip segments and prefer AniSkip |
| 10 | `a3192c3d2` | 2026-11-06 | fix(skip-intro): pick the most complete Anime-Skip entry for an episode |
| 11 | `01f7c3cea` | 2026-11-06 | Merge pull request #4501 from skoruppa/fix/animeskip-open-ended-segments |
| 12 | `cb06272bd` | 2026-11-06 | fix(skip-intro): query Anime-Skip for Kitsu/MAL even without an IntroDB mapping |

**Tác động chính:**

- Thêm `baseDuration` vào model `AnimeSkipEpisode`.
- Anime-Skip không còn phụ thuộc vào mapping IntroDB; vẫn query được khi chỉ có AniList ID (Kitsu/MAL).
- Đổi thứ tự ưu tiên merge: IntroDB → AniSkip → Anime-Skip (AniSkip được ưu tiên hơn Anime-Skip).
- Chọn entry Anime-Skip "đầy đủ nhất" khi một tập có nhiều entry cộng đồng.
- Bỏ segment mở-ended không an toàn: intro/recap không có timestamp kết thúc bị loại; ending chỉ được kéo đến hết tập nếu bắt đầu ở nửa sau và có `baseDuration`.
- Thêm test `AnimeSkipIntervalsTest` (122 dòng).

### 3.4 Nhóm bản dịch / chuỗi

| # | Hash | Ngày | Nội dung |
|---|---|---|---|
| 13 | `eb3ee01d9` | 2026-11-05 | update latest missing strings |
| 14 | `0501488d7` | 2026-11-05 | fix(i18n): update Bengali localization and sync missing strings |
| 15 | `439bc8b83` | 2026-11-05 | i18n: add French strings for decoder release and autosync no-fit toast |
| 16 | `ffa71adfc` | 2026-11-06 | Merge pull request #4487 from blueocean2308/dev |
| 17 | `d4f2e9d7f` | 2026-11-06 | Merge pull request #4488 from TanvirSdq/dev |
| 18 | `0e320e3c3` | 2026-11-06 | Merge pull request #4489 from SK-Gits/localization/update-fr-strings |
| 19 | `3c7fed5b4` | 2026-11-06 | i18n(hu): Add translation for latest strings |
| 20 | `6e0dd55a0` | 2026-11-06 | Merge pull request #4491 from ToxicLand/dev |
| 21 | `c03870e3e` | 2026-11-06 | Update Japanese strings.xml translation |
| 22 | `73ebee1e8` | 2026-11-06 | Update strings.xml |
| 23 | `c6e9c6395` | 2026-11-06 | Add missing Turkish translations and update rewritten strings |
| 24 | `0b37c38e6` | 2026-11-06 | Merge pull request #4500 from Joe00011/dev |
| 25 | `149e56ce6` | 2026-11-06 | Merge pull request #4504 from halibiram/i18n/update-tr-strings |

**Tác động chính:**

- `values-ja`: cập nhật lớn **+819 / −83**, bổ sung khoảng 725 key tiếng Nhật (theme, ambient mode, tracking, update, app icon, v.v.).
- `values-bn`: +136 / −26, thêm mới `strings_party.xml` (+79) và `strings_servers.xml` (+61).
- `values-hu`: +41 / −4.
- `values-tr`: +27 / −5.
- `values-fr`: +3.
- `values-vi`: +2 (key mới của upstream, không đụng bản dịch custom).
- `values/strings.xml`: +2 (2 string cho hero auto-scroll).

### 3.5 Nhóm version

| # | Hash | Ngày | Nội dung |
|---|---|---|---|
| 26 | `fe5c314bd` | 2026-11-06 | bump version 1074/1.1.0-beta.11 → 1075/1.1.0-beta.12 |

---

## 4. Tác động file của upstream

```text
34 files changed, 1836 insertions(+), 204 deletions(-)
```

### 4.1 File mới

| File | Dòng | Vai trò |
|---|---:|---|
| `app/src/main/java/com/nuvio/tv/ui/components/AutoScrollingText.kt` | 229 | Component auto-scroll mô tả hero |
| `app/src/test/java/com/nuvio/tv/data/repository/AnimeSkipIntervalsTest.kt` | 122 | Test logic Anime-Skip mới |
| `app/src/main/res/values-bn/strings_party.xml` | 79 | Chuỗi Watch Party tiếng Bengal |
| `app/src/main/res/values-bn/strings_servers.xml` | 61 | Chuỗi server tiếng Bengal |

### 4.2 Nhóm file theo khu vực

| Khu vực | File tiêu biểu | Số file |
|---|---|---:|
| Home / Hero auto-scroll | `AutoScrollingText.kt`, `HeroCarousel.kt`, `ModernHomeHero.kt`, `ModernHomeContent.kt`, `ClassicHomeContent.kt`, `GridHomeContent.kt`, `HomeUiState.kt`, `HomeViewModelPresentationPipeline.kt` | 8 |
| Settings / preference | `LayoutPreferenceDataStore.kt`, `LayoutHomeSettings.kt`, `LayoutSettingsViewModel.kt` | 3 |
| Player / phụ đề | `PlayerRuntimeController.kt`, `PlayerRuntimeControllerAutomaticSubtitleSync.kt`, `PlayerRuntimeControllerInitialization.kt`, `PlayerRuntimeControllerMpv.kt`, `PlayerRuntimeControllerPlaybackEvents.kt`, `PlayerRuntimeControllerScrobble.kt`, `PlayerRuntimeControllerStreams.kt`, `PlayerRuntimeControllerTrackSelection.kt`, `PlayerRuntimeControllerTracks.kt`, `autosync/AutoSyncRunPolicy.kt` | 10 |
| Skip Intro / Anime-Skip | `SkipIntroApi.kt`, `SkipIntroRepository.kt`, `AnimeSkipIntervalsTest.kt` | 3 |
| Bản dịch | `values-ja`, `values-bn`, `values-hu`, `values-tr`, `values-fr`, `values-vi`, `values`, `values-bn/strings_party.xml`, `values-bn/strings_servers.xml` | 9 |
| Version | `app/build.gradle.kts` | 1 |

### 4.3 File giao nhau với custom của `tet2` (20 file)

```text
app/build.gradle.kts
app/src/main/java/com/nuvio/tv/data/local/LayoutPreferenceDataStore.kt
app/src/main/java/com/nuvio/tv/ui/components/HeroCarousel.kt
app/src/main/java/com/nuvio/tv/ui/screens/home/ClassicHomeContent.kt
app/src/main/java/com/nuvio/tv/ui/screens/home/GridHomeContent.kt
app/src/main/java/com/nuvio/tv/ui/screens/home/HomeViewModelPresentationPipeline.kt
app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeContent.kt
app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeHero.kt
app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeController.kt
app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt
app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt
app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerStreams.kt
app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerTrackSelection.kt
app/src/main/java/com/nuvio/tv/ui/screens/settings/LayoutHomeSettings.kt
app/src/main/java/com/nuvio/tv/ui/screens/settings/LayoutSettingsViewModel.kt
app/src/main/res/values-fr/strings.xml
app/src/main/res/values-hu/strings.xml
app/src/main/res/values-ja/strings.xml
app/src/main/res/values-vi/strings.xml
app/src/main/res/values/strings.xml
```

Chỉ 2 file xung đột văn bản; 18 file còn lại auto-merge nhưng **vẫn phải review ngữ nghĩa** theo checklist mục 8.

---

## 5. Bản đồ bảo toàn tùy chỉnh của `tet2`

Custom delta của `tet2` so với base: **352 file, +42.247 / −3.598**. Các khu vực phải giữ:

| Khu vực tùy chỉnh | File / nhóm file | Cách bảo vệ |
|---|---|---|
| App identity, endpoint, updater, signing | `app/build.gradle.kts` | Giữ nguyên toàn bộ; chỉ xung đột ở version, giữ `1175 / 1.3.3` |
| TorrServer / Torrent | `core/torrent/TorrServer*.kt`, `Torrent*.kt`, `PlayerRuntimeControllerTorrent.kt`, `TorrServerSettings*` | Auto-merge; kiểm tra symbol `torrServerRemoteApi`, `torrServerAddonConfig`, `isTorrServerStream` |
| Modern Leanback Home / sidebar / neon glow | `LeanbackModernRow.kt`, `ModernHome*.kt`, `NativeSidebarView.kt`, `SidebarNavigation.kt`, theme files | Auto-merge; kiểm tra `modernSidebarEnabled` luôn `true`, `LocalGpuOffscreenCompositing` |
| Audio passthrough / IEC / surround | `core/player/Audio*`, `Surround*`, `player/iec/*`, `PlaybackAudioSettings.kt`, `PlayerRuntimeControllerSurroundResolve.kt` | Auto-merge; kiểm tra symbol và logic |
| Live TV / IPTV | `features/livetv/*`, `LiveTvRepository.kt`, `LiveTvSettingsScreen.kt`, `LiveTvConfigServer.kt` | Auto-merge; kiểm tra `isLiveTv` trong player init |
| Stream search bar + stream ordering/badges | `StreamScreen.kt`, `StreamScreenViewModel.kt`, `StreamScreenUiState.kt`, `StreamRepositoryImpl.kt` | Auto-merge; kiểm tra inline search bar + `showStreamSearch` |
| Custom player runtime | `PlayerRuntimeController.kt`, `PlayerRuntimeControllerStreams.kt`, `PlayerRuntimeControllerObservers.kt`, `PlayerRuntimeControllerInitialization.kt`, `PlayerTunnelAvSyncPolicy.kt`, `StartupWatchdogDecision.kt`, `BufferMemoryKeeper.kt` | Auto-merge; kiểm tra symbol, compile, logic overrun mới |
| Custom subtitle outline/style | `PlayerSidecarSubtitles.kt`, `SubtitleStyle*`, `PlayerSubtitleUtils` | Auto-merge; kiểm tra `applyOutlineWidth` |
| Vietnamese localization + custom fonts | `values-vi/strings.xml`, `values/strings.xml`, `res/font/oswald.ttf`, `res/font/fontphimmoi.ttf` | Auto-merge; kiểm tra key/giá trị |
| Xóa chuỗi P2P tiếng Nhật | `values-ja/strings.xml` | Xung đột trực tiếp; giữ trạng thái đã xóa 10 key P2P; sau merge còn 22 key (2 hide-stats + 20 key P2P mới của upstream) |
| `app_name` = `Nuvio TV` | Tất cả `values-*/strings.xml` có custom | Kiểm tra sau merge, không để upstream ghi đè về `Nuvio` |
| GPU offscreen compositing opt-in | `LayoutPreferenceDataStore.kt`, `ModernHomeHero.kt`, `HeroCarousel.kt` | Auto-merge; kiểm tra setting mặc định tắt |
| Version 1.3.3 đang chờ | `app/build.gradle.kts` | Commit trước merge; giữ `1175 / 1.3.3` |

---

## 6. Báo cáo xung đột chi tiết (trước khi sửa)

Dự báo bằng `git merge-tree --write-tree --name-only tet2 origin/dev`:

```text
CONFLICT (content): Merge conflict in app/build.gradle.kts
CONFLICT (content): Merge conflict in app/src/main/res/values-ja/strings.xml
```

Tree dự báo: `cca2293893c7b9cba9b577dc2efa8649db12cd0c`.

> Lưu ý: dự báo trên chạy với HEAD `df8b0480c` (version 1174). Sau khi commit thay đổi cục bộ, xung đột thực tế ở `app/build.gradle.kts` sẽ là `1175 / 1.3.3` (ours) so với `1075 / 1.1.0-beta.12` (theirs). Bản chất và hướng xử lý không đổi.

### 6.1 `app/build.gradle.kts`

- **Loại:** content conflict, 1 hunk tại `defaultConfig`.
- **Ours (`tet2` + thay đổi cục bộ):**

  ```kotlin
  versionCode = 1175
  versionName = "1.3.3"
  ```

- **Theirs (`dev`):**

  ```kotlin
  versionCode = 1075
  versionName = "1.1.0-beta.12"
  ```

- **Nguyên nhân:** hai nhánh dùng scheme version độc lập. `tet2` đang ở `1.3.x`; upstream ở `1.1.0-beta.x`.
- **Hướng xử lý:** giữ **ours** `1175 / 1.3.3`.
- **Lý do:** bảo toàn version scheme và bản release riêng của fork. Lấy upstream sẽ làm giảm versionCode từ 1175 → 1075 và lùi versionName về beta.
- **Phần còn lại của file:** upstream chỉ thay đổi đúng 2 dòng version trong commit `fe5c314bd`, nên không có thay đổi nào khác bị bỏ sót.
- **Rủi ro:** không có về build; cần đảm bảo không push nhầm version thấp hơn.

### 6.2 `app/src/main/res/values-ja/strings.xml`

- **Loại:** content conflict, 1 hunk tại vùng chuỗi P2P (sau `stream_type_youtube`).
- **Ours (`tet2`):** chỉ còn 2 key:

  ```xml
  <string name="settings_p2p_hide_stats_title">トレント統計を非表示</string>
  <string name="settings_p2p_hide_stats_subtitle">読み込み中と再生中にバッファ、シード、ピア、ダウンロード速度を非表示</string>
  ```

- **Theirs (`dev`):** bản dịch tiếng Nhật mới có tổng cộng **32 key P2P**:
  - 10 key P2P cũ mà `tet2` đã xóa: `essential_p2p_streams`, `essential_p2p_streams_subtitle`, `p2p_consent_title`, `p2p_consent_body`, `p2p_consent_enable`, `p2p_consent_cancel`, `settings_p2p_title`, `settings_p2p_subtitle`, `p2p_download_title`, `p2p_download_subtitle`.
  - 2 key hide-stats mà `tet2` vẫn giữ: `settings_p2p_hide_stats_title`, `settings_p2p_hide_stats_subtitle`.
  - 20 key P2P mới của upstream (profile/cache/clear-cache): `settings_p2p_profile_title`, `settings_p2p_profile_soft`, `settings_p2p_profile_soft_description`, `settings_p2p_profile_balanced`, `settings_p2p_profile_balanced_description`, `settings_p2p_profile_fast`, `settings_p2p_profile_fast_description`, `settings_p2p_cache_size_title`, `settings_p2p_cache_size_description`, `settings_p2p_cache_none`, `settings_p2p_cache_2_gb`, `settings_p2p_cache_5_gb`, `settings_p2p_cache_10_gb`, `settings_p2p_clear_cache_title`, `settings_p2p_clear_cache_usage`, `settings_p2p_clear_cache_usage_pending`, `settings_p2p_clear_cache_clearing`, `settings_p2p_clear_cache_done`, `settings_p2p_clear_cache_failed`, `settings_p2p_clear_cache_playback_active`.

- **Nguyên nhân:** `tet2` đã chủ động xóa 10 key P2P khỏi `values-ja` (giữ lại 2 key hide-stats) trong lịch sử custom. Upstream vừa cập nhật lớn bản dịch tiếng Nhật (+819 / −83), đưa 10 key P2P đã xóa trở lại vị trí cũ, đồng thời thêm 20 key P2P mới (profile/cache/clear-cache) mà `tet2` chưa từng có.
- **Hướng xử lý đề xuất:**
  1. Lấy **toàn bộ bản dịch tiếng Nhật mới của `dev`** cho tất cả các vùng (khoảng 725 key mới).
  2. Xóa lại **đúng 10 key P2P cũ** mà `tet2` đã chủ động xóa.
  3. Giữ 2 key `settings_p2p_hide_stats_*` và toàn bộ 20 key P2P mới của upstream (profile/cache/clear-cache), vì đây là bản dịch mới chưa từng bị custom loại bỏ.
  4. Giữ `app_name` = `Nuvio TV` (dòng này nằm ngoài hunk xung đột, auto-merge giữ custom).
- **Lý do:** tôn trọng tùy chỉnh đã có của `tet2` nhưng không đánh mất 20 bản dịch P2P mới của upstream. Đây là xung đột vị trí chèn, không phải xung đột logic.
- **Ảnh hưởng / trade-off cần báo cáo:**
  - Người dùng tiếng Nhật sẽ thấy 10 chuỗi P2P bằng tiếng Anh fallback thay vì tiếng Nhật.
  - Nếu người dùng muốn ưu tiên bản dịch mới hơn bản sắc custom, có thể đổi quyết định sang lấy theirs cho hunk này. Kế hoạch mặc định giữ custom.
- **Kiểm tra sau khi sửa:**
  - Không còn `<<<<<<<`, `=======`, `>>>>>>>`.
  - `app_name` vẫn là `Nuvio TV`.
  - Đúng 10 key P2P cũ không quay lại; 2 key hide-stats và 20 key P2P mới vẫn còn (tổng 22 key).
  - Các key tiếng Nhật mới của upstream vẫn hiện diện.
  - XML parse thành công, không duplicate key.

### 6.3 Vì sao các file giao nhau còn lại không xung đột văn bản?

Git auto-merge được vì các thay đổi nằm ở vùng khác nhau. Tuy nhiên, auto-merge **không đảm bảo đúng ngữ nghĩa**; 18 file này sẽ được review theo checklist mục 8, đặc biệt:

- `HomeViewModelPresentationPipeline.kt`: upstream thêm flow thứ 3 vào `combine`; custom đã đổi `debounce` có điều kiện. Kết quả merge phải giữ **cả hai**.
- `HeroCarousel.kt`: upstream thêm `AutoScrollingText` + hold auto-advance; custom đã tắt Offscreen compositing, đổi crossfade theo `animationsEnabled`, tắt crossfade logo. Kết quả merge phải giữ **cả hai**.
- `ModernHomeHero.kt`: upstream thêm `AutoScrollingText` + `paused = trailerPlaying`; custom đã thêm `animationsEnabled`, `snap`, debounce backdrop, `LocalGpuOffscreenCompositing`. Kết quả merge phải giữ **cả hai**.
- `PlayerRuntimeControllerPlaybackEvents.kt`: upstream thêm `checkExoPlaybackOverrun`; custom đã sửa hiển thị torrent/TorrServer. Kết quả merge phải giữ **cả hai**.
- `PlayerRuntimeControllerAutomaticSubtitleSync.kt`: upstream thêm search-after-selected-failed; custom có logic phụ đề riêng. Phải giữ logic custom và chèn đúng nhánh fallback mới.

---

## 7. Chiến lược xử lý xung đột

| File | Quyết định | Lý do | Mức rủi ro |
|---|---|---|---|
| `app/build.gradle.kts` | Giữ `1175 / 1.3.3` | Bảo toàn version fork | Thấp |
| `values-ja/strings.xml` | Lấy bản dịch mới của `dev`, nhưng giữ xóa 10 key P2P | Bảo toàn custom P2P removal, vẫn nhận 725 key dịch mới | Trung bình (text) |

Không có xung đột nào liên quan đến logic TorrServer, audio, Live TV, stream search, custom player runtime hay subtitle outline.

---

## 8. Checklist review ngữ nghĩa 18 file auto-merge

| File | Cần kiểm tra |
|---|---|
| `app/src/main/java/com/nuvio/tv/data/local/LayoutPreferenceDataStore.kt` | `heroDescriptionAutoScrollEnabled` mới + custom `gpuOffscreenCompositingEnabled` + `modernSidebarEnabled = flowOf(true)` cùng tồn tại; không mất key custom |
| `app/src/main/java/com/nuvio/tv/ui/components/HeroCarousel.kt` | `AutoScrollingText`, `descriptionScrollHoldIndex` + custom `animationsEnabled`, `crossfade(false)`, bỏ Offscreen |
| `app/src/main/java/com/nuvio/tv/ui/screens/home/HomeViewModelPresentationPipeline.kt` | `combine` 3 flow + `debounce { if (layoutPreferencesReady) 300 else 0 }`; `heroDescriptionAutoScrollEnabled` được truyền vào `HomeUiState` |
| `app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeHero.kt` | `AutoScrollingText`, `paused = trailerPlaying` + custom `animationsEnabled`, `snap`, debounce backdrop, GPU offscreen opt-in |
| `app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeContent.kt` | Cả 2 call site `HeroCarousel` và `HeroTitleBlock` đều nhận `descriptionAutoScrollEnabled` |
| `app/src/main/java/com/nuvio/tv/ui/screens/home/ClassicHomeContent.kt` | `HeroCarousel` nhận `descriptionAutoScrollEnabled` |
| `app/src/main/java/com/nuvio/tv/ui/screens/home/GridHomeContent.kt` | Cả 2 call site `HeroCarousel` nhận `descriptionAutoScrollEnabled` |
| `app/src/main/java/com/nuvio/tv/ui/screens/settings/LayoutHomeSettings.kt` | Toggle mới nằm đúng section; custom sidebar section không bị mất |
| `app/src/main/java/com/nuvio/tv/ui/screens/settings/LayoutSettingsViewModel.kt` | State/event/setter mới + custom `showStreamSearch`, sidebar luôn bật |
| `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeController.kt` | `exoEndOverrunHandled`, `loadedTrackPreference` + custom TorrServer/audio/tunnel/watchdog fields |
| `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt` | Reset `exoEndOverrunHandled` + custom Live TV buffer/DRM/subtitle style |
| `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt` | `checkExoPlaybackOverrun` được gọi trong progress loop + custom TorrServer status |
| `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerStreams.kt` | Reset cờ overrun khi switch episode + custom TorrServer/torrent logic |
| `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerTrackSelection.kt` | `currentTrackPreferenceForPersistence` dùng `loadedTrackPreference` + custom track logic |
| `app/src/main/res/values-fr/strings.xml` | 3 key mới, không trùng, `app_name` custom còn nguyên |
| `app/src/main/res/values-hu/strings.xml` | 45 dòng thay đổi, không trùng key |
| `app/src/main/res/values-vi/strings.xml` | 2 key mới của upstream + bản dịch Việt custom còn nguyên |
| `app/src/main/res/values/strings.xml` | 2 key hero auto-scroll + custom strings còn nguyên |

### 8.1 Kiểm tra symbol cụ thể

- `heroDescriptionAutoScrollEnabled` có trong: `LayoutPreferenceDataStore`, `HomeUiState`, `HomeViewModelPresentationPipeline`, `LayoutSettingsViewModel`, `HeroCarousel`, `ModernHomeHero`, `ClassicHomeContent`, `GridHomeContent`, `ModernHomeContent`.
- `AutoScrollingText` có trong `HeroCarousel` và `ModernHomeHero`.
- `exoEndOverrunHandled` được set `false` trong: init player, attach MPV, initialize MPV, switch episode stream, reset loading overlay, event `OnPlaybackStateChanged`/new stream.
- `checkExoPlaybackOverrun` được gọi trong `startProgressUpdates`.
- `loadedTrackPreference` được gán trong `preparePlaybackBeforeStart` và dùng trong `currentTrackPreferenceForPersistence`.
- `SELECTED_THEN_SEARCH` + `searchAfterSelectedFailed` có trong `AutoSyncRunPolicy`.
- `bestAnimeSkipIntervals` + `toAnimeSkipIntervals` + `baseDuration` có trong `SkipIntroRepository`/`SkipIntroApi`.
- `tunnelFrameReportDecoders` và `addTunnelFrameReportDecoder` vẫn còn.
- `torrServerRemoteApi`, `torrServerAddonConfig`, `isTorrServerStream` vẫn còn.
- `applyOutlineWidth` vẫn còn trong `PlayerSidecarSubtitles.kt`.
- `LocalGpuOffscreenCompositing` + `gpuOffscreenCompositingEnabled` vẫn còn.
- `modernSidebarEnabled` vẫn là `flowOf(true)`.
- `showStreamSearch` vẫn còn trong `LayoutSettingsViewModel`/`StreamBadgeSettings`.
- `app_name` = `Nuvio TV` ở các locale có custom.
- 10 key P2P đã xóa không quay lại `values-ja`.

### 8.2 Kiểm tra tĩnh XML / key

1. Parse toàn bộ `app/src/main/res/values*/strings*.xml` bằng XML parser.
2. Đếm `name="..."` theo từng file, phát hiện duplicate.
3. So sánh key `values-ja` sau merge với `origin/dev`:
   - Phải có đủ key mới của upstream.
   - Phải thiếu đúng 10 key P2P đã xóa.
4. Kiểm tra `values-bn/strings_party.xml` và `strings_servers.xml` tồn tại.
5. Kiểm tra không còn conflict marker.
6. `git diff --check` không báo lỗi whitespace.

---

## 9. Các bước thực thi

### Bước 0 — An toàn

1. Tạo backup branch:

   ```bash
   git branch backup-tet2-before-merge-20261107 df8b0480c
   ```

2. Xuất patch dự phòng (bao gồm thay đổi cục bộ đang có):

   ```bash
   git diff --binary > C:/Users/NongTuan/AppData/Local/Temp/opencode/tet2-pre-merge-20261107.patch
   ```

3. Ghi lại trạng thái:

   ```bash
   git status --porcelain
   git rev-parse HEAD
   ```

### Bước 1 — Bảo toàn thay đổi cục bộ

Commit `app/build.gradle.kts` (version 1175 / 1.3.3) thành commit riêng:

```bash
git add app/build.gradle.kts
git commit -m "chore: preserve local version bump to 1.3.3 (1175) before dev sync"
```

> Nếu người dùng muốn giữ thay đổi này uncommitted, có thể `git reset --soft HEAD~1` sau khi merge. Backup branch vẫn giữ trạng thái trước đó.

### Bước 2 — Cập nhật `dev` local

```bash
git fetch origin dev:dev
```

`dev` local đang ở `1206736fd`, là ancestor của `origin/dev` (`fe5c314bd`), nên lệnh này fast-forward an toàn.

### Bước 3 — Merge

```bash
git merge --no-ff dev -m "Merge branch 'dev' into tet2: sync upstream commits (2026-11-05 to 2026-11-06) with hero description auto-scroll, Exo end-overrun fix, restored subtitle preference search, Anime-Skip fixes, i18n updates, and preserved custom TorrServer/UI/Audio configurations"
```

### Bước 4 — Xử lý 2 xung đột

Theo mục 6:

1. `app/build.gradle.kts` → giữ `1175 / 1.3.3`.
2. `values-ja/strings.xml` → lấy bản dịch mới của `dev`, giữ xóa 10 key P2P; giữ `app_name` = `Nuvio TV`.

Sau đó:

```bash
git add app/build.gradle.kts app/src/main/res/values-ja/strings.xml
git diff --check
```

### Bước 5 — Validate tĩnh

1. Parse toàn bộ XML resource.
2. Kiểm tra duplicate key trong tất cả `values*/strings*.xml`.
3. Kiểm tra conflict marker.
4. Kiểm tra các symbol custom/upstream theo mục 8.1.
5. Kiểm tra 10 key P2P đã xóa không quay lại `values-ja`.
6. `git diff --stat` so với upstream và base.

### Bước 6 — Build & test

```bash
./gradlew.bat :app:compileFullDebugKotlin --console=plain
```

Nếu compile OK, chạy test trọng tâm:

```bash
./gradlew.bat :app:testFullDebugUnitTest ^
  --tests "com.nuvio.tv.data.repository.AnimeSkipIntervalsTest" ^
  --tests "com.nuvio.tv.ui.screens.player.autosync.*" ^
  --tests "com.nuvio.tv.data.repository.TraktEpisodeMappingServiceTest" ^
  --console=plain
```

Tùy thời gian, chạy thêm:

```bash
./gradlew.bat :app:assembleFullDebug --console=plain
```

### Bước 7 — Commit merge & tài liệu

- Merge commit đã được tạo ở Bước 3.
- Thêm (do `docs/` nằm trong `.gitignore`, phải dùng `-f`):
  - `docs/merges/2026-11-07-dev-sync-plan.md`
  - `docs/merges/2026-11-07-dev-sync-conflict-report.md`
  - `docs/merges/2026-11-07-dev-sync-report.md`

```bash
git add -f docs/merges/2026-11-07-dev-sync-plan.md
git add -f docs/merges/2026-11-07-dev-sync-conflict-report.md
git add -f docs/merges/2026-11-07-dev-sync-report.md
git commit -m "docs: add 2026-11-07 dev sync plan, conflict report, and result report"
```

### Bước 8 — Không push

Không tự động push. Người dùng kiểm tra rồi quyết định.

---

## 10. Kế hoạch validate chi tiết

| Kiểm tra | Lệnh / cách làm | Tiêu chí đạt |
|---|---|---|
| Không còn conflict marker | `git grep -n "<<<<<<<"` | Không có kết quả |
| XML hợp lệ | Parse mọi `values*/strings*.xml` | Parse thành công |
| Key trùng | Script đếm `name="..."` theo file | Không có duplicate |
| Kotlin compile | `:app:compileFullDebugKotlin` | BUILD SUCCESSFUL |
| Unit test trọng tâm | AnimeSkip + autosync + Trakt | PASS |
| Custom TorrServer | grep `torrServerRemoteApi`, `isTorrServerStream` | Có mặt |
| Custom audio | grep `applyOutlineWidth`, `AudioPassthroughPolicy` | Có mặt |
| Custom UI | grep `LeanbackModernRow`, `LocalGpuOffscreenCompositing` | Có mặt |
| Hero auto-scroll | grep `heroDescriptionAutoScrollEnabled` trong 9 file | Có mặt đầy đủ |
| Exo overrun | grep `checkExoPlaybackOverrun`, `exoEndOverrunHandled` | Có mặt |
| Subtitle preference | grep `loadedTrackPreference`, `SELECTED_THEN_SEARCH` | Có mặt |
| Anime-Skip | grep `bestAnimeSkipIntervals`, `baseDuration` | Có mặt |
| P2P removal ja | So key với `origin/dev` | Thiếu đúng 10 key P2P |
| `app_name` | grep trong `values-ja`, `values-vi`, `values` | `Nuvio TV` |
| Version | `grep versionCode app/build.gradle.kts` | `1175` / `1.3.3` |
| Diff upstream | `git diff --stat upstream/dev..HEAD` | Chỉ còn custom + docs |
| Diff base | `git diff --stat 1206736fd..HEAD` | Custom lớn vẫn còn |

---

## 11. Rollback

### Nếu merge chưa commit

```bash
git merge --abort
```

### Nếu đã commit merge

```bash
git reset --hard backup-tet2-before-merge-20261107
```

### Nếu chỉ muốn bỏ commit bảo toàn thay đổi cục bộ

```bash
git reset --soft HEAD~1
```

### Khôi phục patch dự phòng

```bash
git apply C:/Users/NongTuan/AppData/Local/Temp/opencode/tet2-pre-merge-20261107.patch
```

---

## 12. Rủi ro và quyết định cần ghi nhận

| Rủi ro | Mức | Giảm thiểu |
|---|---|---|
| Conflict version làm giảm versionCode | Cao | Giữ 1175/1.3.3, không lấy 1075 |
| Mất custom P2P removal trong `values-ja` | Trung bình | Giữ ours cho hunk P2P; kiểm tra key-level sau merge |
| Mất custom `app_name` | Trung bình | Kiểm tra tất cả locale bị upstream sửa |
| Auto-merge làm mất logic custom trong player | Cao | Compile + grep symbol + review 10 file runtime |
| Auto-merge làm mất custom hero animations/offscreen | Trung bình | Review `HeroCarousel`, `ModernHomeHero`, `LayoutPreferenceDataStore` |
| Hero auto-scroll gây tốn CPU/RAM trên TV box yếu | Trung bình | Mặc định tắt; test trên thiết bị; có thể tắt trong Settings |
| Exo end-overrun kích hoạt nhầm | Trung bình | Chỉ kích hoạt khi audio đã kết thúc, quá duration ≥ 1s, đã render first frame; compile/test |
| Anime-Skip đổi thứ tự ưu tiên gây khác hành vi cũ | Trung bình | Chạy `AnimeSkipIntervalsTest`; kiểm tra logic merge |
| Chuỗi mới trùng key | Thấp | Script duplicate key |
| Build fail do thiếu JDK/SDK | Thấp | Đã xác minh JDK 21 + SDK path |
| Push nhầm | Thấp | Không push |

---

## 13. Deliverables

1. Merge commit trên `tet2`.
2. `docs/merges/2026-11-07-dev-sync-plan.md` — kế hoạch này.
3. `docs/merges/2026-11-07-dev-sync-conflict-report.md` — báo cáo xung đột trước khi sửa.
4. `docs/merges/2026-11-07-dev-sync-report.md` — báo cáo sau merge: commit, conflict đã xử lý, file thay đổi, kết quả build/test, danh sách tùy chỉnh được giữ.
5. Backup branch `backup-tet2-before-merge-20261107`.
6. Patch dự phòng trong thư mục temp.

---

## 14. Tiêu chí hoàn thành

- [ ] 26 commit của `dev` sau mốc `1206736fd` đã nằm trong `tet2`.
- [ ] Không còn conflict marker.
- [ ] 2 xung đột đã xử lý đúng hướng dẫn mục 6.
- [ ] `:app:compileFullDebugKotlin` thành công.
- [ ] Test trọng tâm pass (hoặc ghi rõ test nào fail vì lý do môi trường).
- [ ] Custom TorrServer/UI/Audio/Live TV/Stream search/Vietnamese strings còn nguyên.
- [ ] Custom xóa 10 key P2P tiếng Nhật được giữ.
- [ ] `app_name` = `Nuvio TV` được giữ.
- [ ] Version 1.3.3 (1175) được giữ.
- [ ] Báo cáo sau merge được tạo.
- [ ] Không push.
