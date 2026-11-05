# Kế hoạch đồng bộ nhánh `dev` → `tet2` (2026-11-05)

> Tài liệu này được tạo **trước khi merge**. Mọi con số, commit và xung đột dưới đây đã được kiểm tra bằng lệnh Git thật trên repository `F:\MyGithub\NuvioTV-Fast`.

---

## 1. Tóm tắt điều hành

| Hạng mục | Giá trị |
|---|---|
| Nhánh đích | `tet2` |
| HEAD hiện tại | `2888dc7f4` — Merge branch 'dev' into tet2 (2026-11-03) |
| Trạng thái so với remote | Ahead `origin/tet2` 84 commit |
| Nguồn đồng bộ | `origin/dev` = `upstream/dev` = `1206736fd` — bump version (2026-11-05) |
| Merge base | `a102f6bf5` — Merge pull request #4443 (2026-11-03) |
| Phạm vi người dùng yêu cầu | 2026-10-26 → 2026-11-05 |
| Phạm vi thực tế còn thiếu | 47 commit (33 commit thường + 14 merge commit), tác giả 2026-10-31 → 2026-11-05 |
| Thay đổi upstream | 58 file, +1.546 / −352 dòng |
| Thay đổi custom của `tet2` so với base | 351 file, +42.024 / −3.600 dòng |
| File giao nhau | 27 file |
| Xung đột văn bản dự báo | 3 file |
| Chiến lược | Backup → commit thay đổi cục bộ → merge `--no-ff` → xử lý 3 xung đột → review ngữ nghĩa 24 file auto-merge → build/test → báo cáo |

### Kết luận kiểm tra phạm vi ngày

Yêu cầu ban đầu ghi 2026-11-01 → hôm nay, sau đó nhắc lại 2026-10-26 → hôm nay. Tôi đã kiểm tra toàn bộ lịch sử:

- Merge `2888dc7f4` ngày 2026-11-03 đã đưa `a102f6bf5` vào `tet2`.
- Tất cả commit của `dev` trong khoảng 2026-10-26 → 2026-11-03 **đã có** trong `tet2`.
- 47 commit còn lại có tác giả từ 2026-10-31 → 2026-11-05 nhưng chỉ được merge vào `dev` sau `a102f6bf5`, nên chưa có trong `tet2`.
- Vì vậy không cần merge lại khoảng 2026-10-26 → 2026-11-03; chỉ cần merge 47 commit mới nhất.

---

## 2. Xác minh ref và môi trường

```text
origin  https://github.com/9000000/NuvioTV-Fast.git
upstream https://github.com/NuvioMedia/NuvioTV.git

tet2                 2888dc7f4
origin/tet2          a21d5c4e7  (tet2 ahead 84)
origin/dev           1206736fd
upstream/dev         1206736fd
merge-base(tet2, dev) a102f6bf5
```

- Git: `2.56.0.windows.4`
- Gradle: `8.14.5`
- JDK dùng bởi Gradle: `jbr-21.0.11` (JDK 21)
- Android SDK: `C:\Users\NongTuan\AppData\Local\Android\Sdk`
- Biến thể build dự kiến để kiểm tra: `:app:compileFullDebugKotlin`

### Thay đổi cục bộ đang có (phải bảo toàn)

```text
modified:   app/build.gradle.kts                  (versionCode 1173→1174, versionName 1.3.1→1.3.2)
deleted:    tai_lieu_tham_khao/gemini-code-1793632109953.kt
deleted:    tai_lieu_tham_khao/tham_khao.txt
```

Kế hoạch: tạo backup branch + patch, sau đó commit các thay đổi này thành một commit bảo toàn trước khi merge. Bản gốc của hai file tài liệu vẫn nằm trong backup branch.

---

## 3. Danh sách 47 commit sẽ merge

### 3.1 Nhóm Autosync / libass / phụ đề

| Hash | Ngày | Nội dung |
|---|---|---|
| `9e311d710` | 2026-10-31 | sync ASS/SSA add-on subtitles drawn by libass |
| `6194be925` | 2026-11-04 | match ASS/SSA subtitles by their dialogue |
| `17707bad9` | 2026-11-04 | keep the add-on subtitle the user chose for the series |
| `8da207853` | 2026-11-04 | stay silent unless the subtitle does not fit the video |
| `3d65d94d6` | 2026-11-04 | shorten the Polish synced label |
| `f860daeb4` | 2026-11-04 | confirm a no-fit only from full references |
| `00754edfb` | 2026-11-04 | let only ASS/SSA candidates compete on the libass path |
| `995f57ab0` | 2026-11-04 | compile the ASS override block regex once |
| `fd95a7caf` | 2026-11-04 | bump nuvio-libass to v0.0.13 and swap tracks with replaceTrack |
| `38f820e46` | 2026-11-04 | fall back to SRT/VTT when no ASS/SSA subtitle fits |
| `1cb85edbc` | 2026-11-04 | reapply the synced result after a player rebuild |
| `e0d4adcc1` | 2026-11-04 | merge PR #4410 (autosync) |
| `af4aa4625` | 2026-11-04 | merge fixes-2 into pr/4410 |

**Tác động chính:** thêm `AutoSyncAssDialogueFilter.kt`, `AutoSyncLibassSubtitles.kt`; sửa lớn `PlayerRuntimeControllerAutomaticSubtitleSync.kt`, `AutoSyncSubtitleCueParser.kt`, `AutomaticSubtitleSync.kt`; cập nhật AAR `nuvio-libass-release.aar` lên v0.0.13; thêm test autosync.

### 3.2 Nhóm Player / playback

| Hash | Ngày | Nội dung |
|---|---|---|
| `f4c482e64` | 2026-10-31 | Fix player episode selection delay |
| `8f3112655` | 2026-11-05 | merge PR #4406 (episode autoplay loading) |
| `3412e2803` | 2026-11-05 | make background decoder release opt-in |
| `eee447a99` | 2026-11-05 | merge PR #4479 (background decoder setting) |
| `ca8bdce45` | 2026-11-05 | remove start threshold, match mobile (continue watching) |
| `bbf2d4388` | 2026-11-05 | drop stale Stream screen on back after binge skip |
| `a6a826687` | 2026-11-05 | merge PR #4486 (binge back + CW progress) |
| `f07acf603` | 2026-11-04 | Trakt: keep addon episode numbers when addon has the episode |
| `4b7da3744` | 2026-11-05 | merge PR #4470 (Trakt episode remap long shows) |
| `212940d5d` | 2026-11-04 | profiles: let Back leave the profile PIN screen |
| `6c858a102` | 2026-11-04 | merge PR #4460 (profile PIN back) |

**Tác động chính:** thêm setting “release decoder on background” (mặc định tắt); sửa autoplay episode; sửa Continue Watching threshold; sửa back stack sau binge; sửa Trakt remap cho show dài; sửa Back ở màn hình PIN profile.

### 3.3 Nhóm UI

| Hash | Ngày | Nội dung |
|---|---|---|
| `2ba905a8d` | 2026-11-01 | match shuffle button to other actions |
| `42cfca0ec` | 2026-11-01 | shorten tab dividers |

**Tác động chính:** thay `ShuffleButton` bằng `ActionIconButton` trong `HeroSection.kt`; xóa `ShuffleButton.kt`; đổi divider tab trong `MetaDetailsScreen.kt` từ text `"|"` sang `Spacer` 1dp.

### 3.4 Nhóm bản dịch / chuỗi

| Hash | Ngày | Nội dung |
|---|---|---|
| `6500920a2` | 2026-11-03 | merge PR #4451 (pt-BR translate) |
| `3c33a5891` | 2026-11-03 | Add files via upload (pt-BR) |
| `aeb3ed629` | 2026-11-03 | Delete values-pt-rBR/strings.xml |
| `b4b6cb2b7` | 2026-11-03 | Add files via upload (pt-BR) |
| `49d83006a` | 2026-11-03 | Add files via upload (pt-BR) |
| `cd7a3c839` | 2026-11-03 | merge PR #4447 |
| `dc9a9fb66` | 2026-11-03 | Update strings.xml |
| `14a6d759f` | 2026-11-04 | merge PR #4467 (es-419) |
| `e985563f5` | 2026-11-04 | Translate missing Latin American Spanish strings |
| `a4d4f5054` | 2026-11-04 | Translate new Watch party chat note |
| `4b74d724a` | 2026-11-04 | Preserve resource formatting in Spanish strings |
| `1b6acb8ee` | 2026-11-04 | Translate new Watch party chat note |
| `c1c3ca78f` | 2026-11-04 | merge PR #4458 (Slovak) |
| `19886da73` | 2026-11-04 | Update strings.xml (Slovak) |
| `23c229109` | 2026-11-04 | Update Slovak subtitles strings |
| `747f7610c` | 2026-11-04 | merge NuvioMedia:dev into dev |
| `bae406453` | 2026-11-05 | merge PR #4476 (missing strings) |
| `bc4dfde92` | 2026-11-05 | update latest missing strings |

**Tác động chính:** cập nhật lớn `values-pt-rBR/strings.xml`; thêm `values-pt-rBR/strings_party.xml`, `values-pt-rBR/strings_servers.xml`; cập nhật es-419, sk, pl, vi, fr, bg, hu, it, iw, es; thêm chuỗi mới cho release decoder, autosync, Trakt, v.v.

### 3.5 Nhóm version / merge

| Hash | Ngày | Nội dung |
|---|---|---|
| `99fbd65d8` | 2026-11-04 | bump version |
| `31400cf86` | 2026-11-04 | merge NuvioMedia dev into dev |
| `1206736fd` | 2026-11-05 | bump version |

---

## 4. Tác động file của upstream

```text
58 files changed, 1546 insertions(+), 352 deletions(-)
```

### File mới quan trọng

- `app/src/main/java/com/nuvio/tv/ui/screens/player/autosync/AutoSyncAssDialogueFilter.kt`
- `app/src/main/java/com/nuvio/tv/ui/screens/player/autosync/AutoSyncLibassSubtitles.kt`
- `app/src/test/java/com/nuvio/tv/data/repository/TraktEpisodeMappingServiceTest.kt`
- `app/src/test/java/com/nuvio/tv/ui/screens/player/autosync/AutoSyncAssDialogueFilterTest.kt`
- `app/src/main/res/values-pt-rBR/strings_party.xml`
- `app/src/main/res/values-pt-rBR/strings_servers.xml`

### File bị xóa

- `app/src/main/java/com/nuvio/tv/ui/screens/detail/ShuffleButton.kt` — thay bằng `ActionIconButton` trong `HeroSection.kt`.

### File nhị phân thay đổi

- `app/libs/nuvio-libass-release.aar` — 5.245.316 → 5.245.885 bytes (v0.0.13).

### Nhóm file chính

| Nhóm | File tiêu biểu |
|---|---|
| Autosync | `PlayerRuntimeControllerAutomaticSubtitleSync.kt`, `AutoSync*.kt`, `PlayerLibassCompat.kt`, `PlayerSidecarSubtitles.kt` |
| Player | `PlayerRuntimeController*.kt`, `PlayerRuntimeControllerMpv.kt`, `PlayerRuntimeControllerTracks.kt` |
| Settings | `PlaybackPlayerSettings.kt`, `PlaybackSettingsScreen.kt`, `PlaybackSettingsSections.kt`, `PlaybackSettingsViewModel.kt`, `DeviceLocalPlayerPreferences.kt` |
| Detail/Home/Nav | `EpisodesSection.kt`, `HeroSection.kt`, `MetaDetailsScreen.kt`, `MetaDetailsViewModel.kt`, `HomeViewModelContinueWatching.kt`, `NuvioNavHost.kt` |
| Trakt/progress | `TraktEpisodeMappingService.kt`, `WatchProgress.kt` |
| Profile | `ProfileSelectionScreen.kt` |
| Stream | `StreamScreen.kt` |
| Chuỗi | 20+ file `values-*/strings.xml` |

---

## 5. Bản đồ bảo toàn tùy chỉnh của `tet2`

Custom delta của `tet2` so với base: **351 file, +42.024 / −3.600**. Các khu vực phải giữ:

| Khu vực tùy chỉnh | File / nhóm file | Cách bảo vệ |
|---|---|---|
| App identity, endpoint, updater, signing | `app/build.gradle.kts` | Giữ nguyên toàn bộ; chỉ xung đột ở version |
| TorrServer / Torrent | `core/torrent/TorrServer*.kt`, `Torrent*.kt`, `PlayerRuntimeControllerTorrent.kt`, `TorrServerSettings*` | Auto-merge; kiểm tra symbol còn đủ |
| Modern Leanback Home / sidebar / neon glow | `LeanbackModernRow.kt`, `ModernHome*.kt`, `NativeSidebarView.kt`, `SidebarNavigation.kt`, theme files | Auto-merge; kiểm tra UI |
| Audio passthrough / IEC / surround | `core/player/Audio*`, `Surround*`, `player/iec/*`, `PlaybackAudioSettings.kt`, `PlayerRuntimeControllerSurroundResolve.kt` | Auto-merge; kiểm tra symbol |
| Live TV / IPTV | `features/livetv/*`, `LiveTvRepository.kt`, `LiveTvSettingsScreen.kt`, `LiveTvConfigServer.kt` | Auto-merge; kiểm tra |
| Stream search bar + stream ordering/badges | `StreamScreen.kt`, `StreamScreenViewModel.kt`, `StreamScreenUiState.kt`, `StreamRepositoryImpl.kt` | Auto-merge; kiểm tra inline search bar |
| Custom player runtime | `PlayerRuntimeController.kt`, `PlayerRuntimeControllerStreams.kt`, `PlayerRuntimeControllerObservers.kt`, `PlayerRuntimeControllerInitialization.kt`, `PlayerTunnelAvSyncPolicy.kt`, `StartupWatchdogDecision.kt`, `BufferMemoryKeeper.kt` | Auto-merge; kiểm tra symbol và logic |
| Custom subtitle outline/style | `PlayerSidecarSubtitles.kt`, `SubtitleStyle*`, `PlayerSubtitleUtils` | Auto-merge; kiểm tra `applyOutlineWidth` |
| Vietnamese localization + custom fonts | `values-vi/strings.xml`, `values/strings.xml`, `res/font/oswald.ttf`, `res/font/fontphimmoi.ttf` | Auto-merge; kiểm tra key/giá trị |
| Version 1.3.2 đang chờ | `app/build.gradle.kts` | Commit trước merge; giữ 1174/1.3.2 |
| Hai tài liệu tham khảo đã xóa | `tai_lieu_tham_khao/*` | Giữ trạng thái xóa; backup branch chứa bản gốc |

---

## 6. Báo cáo xung đột chi tiết (trước khi sửa)

Dự báo bằng `git merge-tree --write-tree --name-only tet2 dev`:

```text
CONFLICT (content): app/build.gradle.kts
CONFLICT (content): app/src/main/java/com/nuvio/tv/data/local/DeviceLocalPlayerPreferences.kt
CONFLICT (content): app/src/main/res/values-pt-rBR/strings.xml
```

### 6.1 `app/build.gradle.kts`

- **Loại:** content conflict, 1 hunk.
- **Ours (`tet2` + thay đổi cục bộ):**
  ```kotlin
  versionCode = 1174
  versionName = "1.3.2"
  ```
- **Theirs (`dev`):**
  ```kotlin
  versionCode = 1074
  versionName = "1.1.0-beta.11"
  ```
- **Nguyên nhân:** hai nhánh dùng scheme version khác nhau. `tet2` đang ở 1.3.x; upstream đang ở 1.1.0-beta.x.
- **Hướng xử lý:** giữ ours `1174 / 1.3.2`.
- **Lý do:** bảo toàn version scheme và bản release riêng của fork; versionCode của upstream thấp hơn và sẽ làm giảm version code nếu lấy. Đây là thay đổi có chủ đích, sẽ ghi trong báo cáo.
- **Rủi ro:** không có về build; cần đảm bảo không push nhầm version thấp hơn.

### 6.2 `DeviceLocalPlayerPreferences.kt`

- **Loại:** content conflict, 1 hunk tại vùng khai báo key.
- **Ours (`tet2`):** thêm `tunnelFrameReportDecodersKey`, flow `tunnelFrameReportDecoders`, hàm `addTunnelFrameReportDecoder`.
- **Theirs (`dev`):** thêm `releaseDecoderOnBackgroundKey`, flow `releaseDecoderOnBackground`, hàm `setReleaseDecoderOnBackground`.
- **Nguyên nhân:** hai bên cùng chèn key mới vào cùng vị trí sau `playerStatsHudActiveKey`.
- **Hướng xử lý:** giữ **cả hai**. Không có xung đột logic; hai tính năng độc lập.
- **Kiểm tra sau khi sửa:** cả 2 key, 2 flow và 2 hàm setter đều tồn tại; không có block `store.edit` bị lặp.

### 6.3 `values-pt-rBR/strings.xml`

- **Loại:** content conflict, 2 hunk.
- **Hunk 1:** `collections_editor_tmdb_watch_providers_helper`
  - Ours: dùng dấu nháy đã escape `\"E\"`, `\"OU\"`.
  - Theirs: dùng dấu nháy không escape `"E"`, `"OU"`.
  - Cả hai đều hợp lệ về XML/Android. Theirs là bản dịch mới.
  - **Hướng xử lý:** lấy theirs (bản dịch mới), giữ nguyên nội dung.
- **Hunk 2:** nhóm `dv7_libdovi_mode_caption`, `dv7_libdovi_mode_row_title`, `dv7_libdovi_mode_none_title`, `dv7_libdovi_mode_none_sub`, `dv7_libdovi_mode_4_title`, `dv7_libdovi_mode_4_sub`
  - Ours: có escape dấu nháy, không có `translatable="false"`.
  - Theirs: thêm `translatable="false"` và bỏ escape.
  - **Hướng xử lý:** lấy theirs vì đây là metadata dịch thuật mới của upstream; nội dung tiếng Bồ Đào Nha vẫn giữ.
- **Các thay đổi custom đã auto-merge và phải giữ:**
  - `app_name` = `Nuvio TV` (không phải `Nuvio`).
  - Đã xóa các key P2P khỏi pt-BR: `essential_p2p_streams`, `essential_p2p_streams_subtitle`, `p2p_consent_*`, `settings_p2p_title`.
  - Các key mới của upstream như `strings_party.xml`, `strings_servers.xml` vẫn được thêm.
- **Rủi ro:** nếu dùng sai hunk có thể mất bản dịch mới hoặc mất custom `app_name`/P2P removal. Sẽ kiểm tra bằng script so key và XML parse.

---

## 7. Checklist review ngữ nghĩa 24 file auto-merge

Không có conflict văn bản không có nghĩa là an toàn. Các file sau sẽ được kiểm tra cả hai phía:

| File | Cần kiểm tra |
|---|---|
| `NuvioNavHost.kt` | Logic back sau binge của upstream + custom navigation của tet2 |
| `EpisodesSection.kt` | Badge `showNotStartedBadge` mới + custom focus/scroll/compositing |
| `HeroSection.kt` | `ActionIconButton` thay `ShuffleButton` + custom hero layout |
| `MetaDetailsScreen.kt` | Divider tab mới + custom detail UI |
| `HomeViewModelContinueWatching.kt` | Threshold CW mới + custom debounce |
| `PlayerRuntimeController.kt` | `releaseDecoderOnBackground`, `hasObservedPlayerSettings` + custom fields TorrServer/audio/failover |
| `PlayerRuntimeControllerInitialization.kt` | Gate release decoder mới + custom init/DRM/buffer |
| `PlayerRuntimeControllerObservers.kt` | Hook autosync, settings cache, observer mới + custom observers |
| `PlayerRuntimeControllerStreams.kt` | Cached settings mới + custom stream/TorrServer logic |
| `PlayerSidecarSubtitles.kt` | `AutoSyncLibassSubtitles.clear` + custom outline styling |
| `PlaybackSettingsScreen.kt` | State `releaseDecoderOnBackground` + custom settings tabs |
| `PlaybackSettingsSections.kt` | Param mới + custom sections/custom font |
| `PlaybackSettingsViewModel.kt` | Flow/setter mới + custom settings logic |
| `StreamScreen.kt` | `LoadingOverlay` mới + custom inline search bar |
| `values-bg/strings.xml` | Key mới, không trùng |
| `values-es/strings.xml` | Key mới, không trùng |
| `values-fr/strings.xml` | Key mới, không trùng |
| `values-hu/strings.xml` | Key mới, không trùng |
| `values-it/strings.xml` | Key mới, không trùng |
| `values-iw/strings.xml` | Key mới, không trùng |
| `values-pl/strings.xml` | Key mới, không trùng |
| `values-sk/strings.xml` | Key mới, không trùng |
| `values-vi/strings.xml` | Bản dịch tiếng Việt custom + key mới |
| `values/strings.xml` | Chuỗi English mới + custom strings |

### Kiểm tra symbol cụ thể

- `tunnelFrameReportDecoders` và `addTunnelFrameReportDecoder` vẫn còn.
- `releaseDecoderOnBackground` và `setReleaseDecoderOnBackground` có mặt.
- `isTorrServerStream`, `torrServerRemoteApi`, `torrServerAddonConfig` vẫn còn.
- `observeDeviceLocalReleaseDecoderOnBackground()` được gọi trong `observeDeviceLocalSettings()`.
- `runSelectedAutomaticSubtitleSync(match)` có trong `fetchAddonSubtitles()`.
- `hasObservedPlayerSettings` được set trong `observeSubtitleSettings()` và dùng trong `loadStreamsForEpisode` / `playNextEpisode`.
- `AutoSyncLibassSubtitles.clear(...)` có trong `stopSidecarAddonSubtitle`.
- `applyOutlineWidth(...)` vẫn còn trong `PlayerSidecarSubtitles.kt`.
- `ShuffleButton` không còn tham chiếu nào sau khi upstream xóa file.
- `releaseDecoderOnBackground` được truyền đúng qua `PlaybackSettingsScreen` → `PlaybackSettingsSections` → `PlaybackPlayerSection`.

---

## 8. Các bước thực thi

### Bước 0 — An toàn

1. Tạo backup branch:
   ```bash
   git branch backup-tet2-before-merge-20261105 2888dc7f4
   ```
2. Xuất patch dự phòng:
   ```bash
   git diff --binary > <temp>/tet2-pre-merge-20261105.patch
   ```
3. Ghi lại `git status`, `git rev-parse HEAD`.

### Bước 1 — Bảo toàn thay đổi cục bộ

Commit 3 file đang thay đổi thành một commit riêng:

```text
chore: preserve local changes before dev sync (v1.3.2, reference cleanup)
```

Bao gồm:
- `app/build.gradle.kts` → 1174 / 1.3.2
- xóa `tai_lieu_tham_khao/gemini-code-1793632109953.kt`
- xóa `tai_lieu_tham_khao/tham_khao.txt`

> Nếu người dùng muốn giữ các thay đổi này uncommitted, có thể `git reset --soft HEAD~1` sau khi merge. Backup branch vẫn giữ trạng thái trước đó.

### Bước 2 — Cập nhật `dev` local

```bash
git fetch origin dev:dev
```

`dev` local đang ở `a102f6bf5`, là ancestor của `origin/dev`; lệnh này fast-forward an toàn.

### Bước 3 — Merge

```bash
git merge --no-ff dev -m "Merge branch 'dev' into tet2: sync upstream commits (2026-10-31 to 2026-11-05) with ASS/SSA libass autosync, Trakt episode remap, binge back navigation, background decoder opt-in, and preserved custom TorrServer/UI/Audio configurations"
```

### Bước 4 — Xử lý 3 xung đột

Theo mục 6:
- `app/build.gradle.kts` → giữ 1174 / 1.3.2.
- `DeviceLocalPlayerPreferences.kt` → giữ cả hai block.
- `values-pt-rBR/strings.xml` → lấy upstream cho 2 hunk; giữ custom `app_name` và P2P removal.

Sau đó:

```bash
git add <3 file>
git diff --check
grep -R "<<<<<<<\|=======\|>>>>>>>" <các file đã sửa>
```

### Bước 5 — Validate tĩnh

1. Parse toàn bộ XML resource:
   ```bash
   python scripts/validate_resources.py   # nếu có; nếu không dùng script inline
   ```
2. Kiểm tra key trùng trong tất cả `values*/strings*.xml`.
3. Kiểm tra không còn `ShuffleButton`.
4. Kiểm tra các symbol custom/upstream theo mục 7.
5. `git diff --stat` so với upstream và base.

### Bước 6 — Build & test

```bash
./gradlew.bat :app:compileFullDebugKotlin --console=plain
```

Nếu compile OK, chạy test trọng tâm:

```bash
./gradlew.bat :app:testFullDebugUnitTest \
  --tests "com.nuvio.tv.data.repository.TraktEpisodeMappingServiceTest" \
  --tests "com.nuvio.tv.ui.screens.player.autosync.*" \
  --tests "com.nuvio.tv.data.mdblist.MdbListProgressProjectionTest" \
  --console=plain
```

Tùy thời gian, chạy thêm:

```bash
./gradlew.bat :app:assembleFullDebug --console=plain
```

### Bước 7 — Commit merge & tài liệu

- Merge commit đã được tạo ở Bước 3.
- Thêm:
  - `docs/merges/2026-11-05-dev-sync-plan.md`
  - `docs/merges/2026-11-05-dev-sync-conflict-report.md`
  - `docs/merges/2026-11-05-dev-sync-report.md`
- Commit tài liệu:
  ```text
  docs: add 2026-11-05 dev sync plan, conflict report, and result report
  ```

### Bước 8 — Không push

Không tự động push. Người dùng kiểm tra rồi quyết định.

---

## 9. Kế hoạch validate chi tiết

| Kiểm tra | Lệnh / cách làm | Tiêu chí đạt |
|---|---|---|
| Không còn conflict marker | `git grep -n "<<<<<<<"` | Không có kết quả |
| XML hợp lệ | Python `xml.etree.ElementTree.parse` cho mọi `values*/strings*.xml` | Parse thành công |
| Key trùng | Script đếm `name="..."` theo file | Không có duplicate |
| Kotlin compile | `:app:compileFullDebugKotlin` | BUILD SUCCESSFUL |
| Unit test trọng tâm | Trakt + autosync + MdbList | PASS |
| Custom TorrServer | grep `torrServerRemoteApi`, `isTorrServerStream` | Có mặt |
| Custom audio | grep `applyOutlineWidth`, `AudioPassthroughPolicy` | Có mặt |
| Custom UI | grep `LeanbackModernRow`, `LocalGpuOffscreenCompositing` | Có mặt |
| Release decoder | grep `releaseDecoderOnBackground` trong 5 file | Có mặt đầy đủ |
| Autosync hook | grep `runSelectedAutomaticSubtitleSync` | Có mặt |
| ShuffleButton | grep toàn repo | Không còn |
| Diff upstream | `git diff --stat upstream/dev..HEAD` | Chỉ còn custom + docs |
| Diff base | `git diff --stat a102f6bf5..HEAD` | Custom lớn vẫn còn |

---

## 10. Rollback

### Nếu merge chưa commit

```bash
git merge --abort
```

### Nếu đã commit merge

```bash
git reset --hard backup-tet2-before-merge-20261105
```

### Nếu chỉ muốn bỏ commit bảo toàn thay đổi cục bộ

```bash
git reset --soft HEAD~1
```

### Khôi phục patch dự phòng

```bash
git apply <temp>/tet2-pre-merge-20261105.patch
```

---

## 11. Rủi ro và quyết định cần ghi nhận

| Rủi ro | Mức | Giảm thiểu |
|---|---|---|
| Conflict version làm giảm versionCode | Cao | Giữ 1174/1.3.2, không lấy 1074 |
| Mất custom `app_name` / P2P removal trong pt-BR | Trung bình | Kiểm tra key-level sau merge |
| Auto-merge làm mất logic custom trong player | Cao | Compile + grep symbol + review 4 file runtime |
| AAR libass mới không tương thích custom player | Trung bình | Compile + test autosync; backup AAR cũ trong backup branch |
| `ShuffleButton` bị xóa nhưng còn tham chiếu | Trung bình | `git grep ShuffleButton` |
| Chuỗi mới trùng key | Thấp | Script duplicate key |
| Build fail do thiếu JDK/SDK | Thấp | Đã xác minh JDK 21 + SDK path |
| Mất hai tài liệu tham khảo đã xóa | Thấp | Backup branch + patch |
| Push nhầm | Thấp | Không push |

---

## 12. Deliverables

1. Merge commit trên `tet2`.
2. `docs/merges/2026-11-05-dev-sync-plan.md` — kế hoạch này.
3. `docs/merges/2026-11-05-dev-sync-conflict-report.md` — báo cáo xung đột trước khi sửa.
4. `docs/merges/2026-11-05-dev-sync-report.md` — báo cáo sau merge: commit, conflict đã xử lý, file thay đổi, kết quả build/test, danh sách tùy chỉnh được giữ.
5. Backup branch `backup-tet2-before-merge-20261105`.
6. Patch dự phòng trong thư mục temp.

---

## 13. Tiêu chí hoàn thành

- [ ] 47 commit của `dev` đã nằm trong `tet2`.
- [ ] Không còn conflict marker.
- [ ] 3 xung đột đã xử lý đúng hướng dẫn mục 6.
- [ ] `:app:compileFullDebugKotlin` thành công.
- [ ] Test trọng tâm pass (hoặc ghi rõ test nào fail vì lý do môi trường).
- [ ] Custom TorrServer/UI/Audio/Live TV/Stream search/Vietnamese strings còn nguyên.
- [ ] Version 1.3.2 được giữ.
- [ ] Báo cáo sau merge được tạo.
- [ ] Không push.
