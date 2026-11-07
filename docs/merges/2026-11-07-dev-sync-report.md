# Báo cáo sau merge — đồng bộ `dev` → `tet2` (2026-11-07)

> Tài liệu này được tạo **sau khi merge và xử lý xong xung đột**, trước khi push.
> Mọi số liệu đều lấy từ repository `F:\MyGithub\NuvioTV-Fast` tại thời điểm lập báo cáo.
> Kế hoạch trước merge: `docs/merges/2026-11-07-dev-sync-plan.md`.
> Báo cáo xung đột trước khi sửa: `docs/merges/2026-11-07-dev-sync-conflict-report.md`.

---

## 1. Tóm tắt điều hành

| Hạng mục | Kết quả |
|---|---|
| Nhánh đích | `tet2` |
| HEAD trước merge | `634257f5f` — chore: preserve local version bump to 1.3.3 (1175) |
| Nguồn đồng bộ | `dev` = `origin/dev` = `fe5c314bd` — bump version (2026-11-06) |
| Merge base | `1206736fd` — mốc đồng bộ ngày 2026-11-05 |
| Commit được đồng bộ | **26 commit** (16 commit thường + 10 merge commit) |
| Merge commit | `d7d03a87d` |
| Parents của merge commit | `634257f5f` (tet2) + `fe5c314bd` (dev) |
| Xung đột văn bản | **2 file**, đã xử lý đúng kế hoạch |
| Thay đổi thực tế của merge so với HEAD | **33 file**, `+1.832 / −200` |
| Thay đổi upstream dự báo | 34 file, `+1.836 / −204` |
| Tổng thay đổi `tet2` so với base sau merge | **366 file**, `+44.079 / −3.798` |
| Compile | `:app:compileFullDebugKotlin` — **BUILD SUCCESSFUL** (5m 8s) |
| Unit test trọng tâm | 12 suite, **78 test**, 0 fail, 0 error, 0 skip |
| Version giữ lại | `versionCode = 1175`, `versionName = "1.3.3"` |
| P2P tiếng Nhật | `dev` có 32 key; giữ 22 key; xóa đúng 10 key cũ |
| Push | **Không push** |

**Kết luận:** đã đồng bộ đầy đủ 26 commit mới nhất của `dev` vào `tet2`, xử lý đúng 2 xung đột dự báo, giữ toàn bộ tùy chỉnh của fork, compile thành công và không có xung đột logic bị bỏ sót.

---

## 2. Trạng thái Git sau merge

```text
Branch:        tet2
Merge commit:  d7d03a87d
Parents:       634257f5f (tet2) + fe5c314bd (dev)
Merge base:    1206736fd
Backup branch: backup-tet2-before-merge-20261107 -> df8b0480c
Patch dự phòng:
  C:\Users\NongTuan\AppData\Local\Temp\opencode\tet2-pre-merge-20261107.patch
```

Kiểm tra then chốt:

```text
git rev-list --count HEAD..dev   -> 0   (sau khi commit merge)
git rev-list --count MERGE_HEAD..dev -> 0 (tại thời điểm merge)
git status --short               -> sạch sau commit tài liệu
```

> `docs/` nằm trong `.gitignore` (dòng 70), nên 3 tài liệu merge được thêm bằng `git add -f` và commit riêng sau merge commit.

---

## 3. Phạm vi commit đã đồng bộ

26 commit sau mốc `1206736fd`, nhóm theo khu vực:

### 3.1 Hero description auto-scroll / Home (PR #4224)

```text
5d925b210  Auto-scroll truncated hero descriptions on Home
2670d72de  Address review feedback on auto-scrolling hero descriptions
42843eabc  Fix stuck carousel hold and visible jump on trailer pause
136392bd9  Merge pull request #4224 from marezezelj/feat/hero-description-auto-scroll
```

- Thêm `AutoScrollingText.kt` (229 dòng).
- Thêm setting `hero_description_auto_scroll_enabled` (mặc định **tắt**) vào `LayoutPreferenceDataStore`.
- Thêm state vào `HomeUiState` + pipeline Home, toggle trong `LayoutHomeSettings`.
- `HeroCarousel` và `ModernHomeHero` dùng `AutoScrollingText`; carousel hold auto-advance đến khi đoạn mô tả dài cuộn xong lượt đầu.
- Tôn trọng `NuvioTheme.animationsEnabled`; dừng cuộn khi trailer đang phát (`paused = trailerPlaying`).

### 3.2 Player / phụ đề / Exo end-overrun (PR #4494, #4495)

```text
19b8a0c2f  fix(player): keep restored subtitle preference and let AutoSync search further
105490c2d  fix(player): end Exo playback when video renderer never reaches end of stream
fe8dd2e44  Merge pull request #4495 from skoruppa/fix/exo-end-overrun
248ed08bf  Merge pull request #4494 from skoruppa/fix/track-pref-wiped-after-restore
```

- Thêm `exoEndOverrunHandled` + `checkExoPlaybackOverrun(...)`: kết thúc playback tự nhiên khi audio đã hết nhưng video renderer không bao giờ đọc được EOS.
- Reset cờ overrun khi khởi tạo player, attach/initialize MPV, chuyển stream, chọn episode mới và reset loading overlay.
- Thêm `loadedTrackPreference` để giữ preference phụ đề đã khôi phục, tránh bị AutoSync ghi đè.
- Thêm scope `SELECTED_THEN_SEARCH` + `searchAfterSelectedFailed(...)` cho AutoSync.

### 3.3 Anime-Skip / Skip Intro (PR #4501)

```text
6312eb2a8  fix(skip-intro): drop open-ended Anime-Skip segments and prefer AniSkip
a3192c3d2  fix(skip-intro): pick the most complete Anime-Skip entry for an episode
01f7c3cea  Merge pull request #4501 from skoruppa/fix/animeskip-open-ended-segments
cb06272bd  fix(skip-intro): query Anime-Skip for Kitsu/MAL even without an IntroDB mapping
```

- Thêm `baseDuration` vào model `AnimeSkipEpisode`.
- Anime-Skip không còn phụ thuộc mapping IntroDB; query được khi chỉ có AniList ID.
- Ưu tiên merge: IntroDB → AniSkip → Anime-Skip.
- Loại segment mở-ended không an toàn; chọn entry Anime-Skip đầy đủ nhất.
- Thêm test `AnimeSkipIntervalsTest` (122 dòng).

### 3.4 Bản dịch / chuỗi

```text
eb3ee01d9  update latest missing strings
0501488d7  fix(i18n): update Bengali localization and sync missing strings
439bc8b83  i18n: add French strings for decoder release and autosync no-fit toast
ffa71adfc  Merge pull request #4487 from blueocean2308/dev
d4f2e9d7f  Merge pull request #4488 from TanvirSdq/dev
0e320e3c3  Merge pull request #4489 from SK-Gits/localization/update-fr-strings
3c7fed5b4  i18n(hu): Add translation for latest strings
6e0dd55a0  Merge pull request #4491 from ToxicLand/dev
c03870e3e  Update Japanese strings.xml translation
73ebee1e8  Update strings.xml
c6e9c6395  Add missing Turkish translations and update rewritten strings
0b37c38e6  Merge pull request #4500 from Joe00011/dev
149e56ce6  Merge pull request #4504 from halibiram/i18n/update-tr-strings
```

- `values-ja`: cập nhật lớn (~725 key tiếng Nhật mới), giữ custom xóa 10 key P2P.
- `values-bn`: cập nhật + thêm mới `strings_party.xml` (+79) và `strings_servers.xml` (+61).
- `values-hu`, `values-tr`, `values-fr`, `values-vi`, `values/strings.xml`: cập nhật theo upstream.

### 3.5 Version

```text
fe5c314bd  bump version 1074/1.1.0-beta.11 -> 1075/1.1.0-beta.12
```

- **Không lấy** version upstream; giữ `1175 / 1.3.3` của fork.

---

## 4. Kết quả merge và thống kê thay đổi

### 4.1 So sánh dự báo và thực tế

| Chỉ số | Dự báo | Thực tế | Ghi chú |
|---|---:|---:|---|
| File thay đổi | 34 | 33 | `app/build.gradle.kts` giữ ours nên không đổi so với HEAD |
| Dòng thêm | +1.836 | +1.832 | Chênh 4 dòng do version + custom P2P |
| Dòng xóa | −204 | −200 | Chênh 4 dòng do version + custom P2P |
| Xung đột văn bản | 2 | 2 | Đúng dự báo |

Lệnh xác minh:

```text
git diff --cached --shortstat HEAD
  -> 33 files changed, 1832 insertions(+), 200 deletions(-)
```

### 4.2 Tổng thay đổi so với mốc đồng bộ trước

```text
git diff --shortstat 1206736fd HEAD~1   (trước merge)
  -> 352 files changed, 42247 insertions(+), 3598 deletions(-)

git diff --cached --shortstat 1206736fd (kết quả merge)
  -> 366 files changed, 44079 insertions(+), 3798 deletions(-)
```

- 352 file custom của `tet2` + 34 file upstream − 20 file giao nhau = **366 file**.
- Toàn bộ custom delta của fork vẫn nằm trong kết quả merge.

### 4.3 File mới từ upstream

| File | Dòng | Vai trò |
|---|---:|---|
| `app/src/main/java/com/nuvio/tv/ui/components/AutoScrollingText.kt` | 229 | Component auto-scroll mô tả hero |
| `app/src/test/java/com/nuvio/tv/data/repository/AnimeSkipIntervalsTest.kt` | 122 | Test logic Anime-Skip |
| `app/src/main/res/values-bn/strings_party.xml` | 79 | Chuỗi Watch Party tiếng Bengal |
| `app/src/main/res/values-bn/strings_servers.xml` | 61 | Chuỗi server tiếng Bengal |

---

## 5. Xử lý xung đột

### 5.1 `app/build.gradle.kts`

- **Xung đột:** version của fork (`1175 / 1.3.3`) vs upstream (`1075 / 1.1.0-beta.12`).
- **Quyết định:** giữ **ours** — `versionCode = 1175`, `versionName = "1.3.3"`.
- **Lý do:** bảo toàn scheme version và bản release riêng của fork; lấy upstream sẽ làm giảm versionCode và lùi versionName về beta.
- **Kết quả:** file không xuất hiện trong diff của merge commit vì giữ nguyên so với HEAD trước merge.

### 5.2 `app/src/main/res/values-ja/strings.xml`

- **Xung đột:** upstream cập nhật lớn bản dịch tiếng Nhật và đưa các key P2P trở lại vùng `stream_type_youtube`; fork đã chủ động xóa 10 key P2P cũ.
- **Quyết định:**
  1. Lấy **toàn bộ bản dịch mới của `dev`** (~725 key mới).
  2. Xóa lại **đúng 10 key P2P cũ** mà fork đã xóa.
  3. Giữ 2 key `settings_p2p_hide_stats_*` và **20 key P2P mới** của upstream (profile/cache/clear-cache).
  4. Giữ `app_name = "Nuvio TV"` và các thuộc tính custom `formatted="false"`.

#### Kiểm tra key-level

```text
Số key P2P trong origin/dev:  32
Số key P2P trong kết quả:     22
Số key bị thiếu:              10
```

10 key bị thiếu (đúng danh sách custom đã xóa):

```text
essential_p2p_streams
essential_p2p_streams_subtitle
p2p_consent_title
p2p_consent_body
p2p_consent_enable
p2p_consent_cancel
settings_p2p_title
settings_p2p_subtitle
p2p_download_title
p2p_download_subtitle
```

22 key còn lại = 2 key `settings_p2p_hide_stats_*` + 20 key P2P mới của upstream.

> **Không có key P2P nào của fork bị mất oan.** So sánh hai chiều cho kết quả: `EXTRA_IN_CUR` rỗng, tức kết quả không có key nào mà `dev` không có.

- **Trade-off:** người dùng tiếng Nhật sẽ thấy 10 chuỗi P2P cũ bằng tiếng Anh fallback. 20 chuỗi P2P mới vẫn có bản dịch tiếng Nhật.
- **Kết quả diff:** `values-ja` thay đổi `+817 / −81` so với HEAD trước merge.

### 5.3 Có thay thế custom bằng code upstream không?

**Không.** Không có tùy chỉnh nào của `tet2` bị ghi đè bởi upstream. Các tính năng mới của upstream được tích hợp thêm vào, còn các nhánh custom vẫn tồn tại song song:

- Version fork vẫn là `1175 / 1.3.3`.
- 10 key P2P tiếng Nhật vẫn bị xóa theo custom.
- `app_name` vẫn là `Nuvio TV`.
- TorrServer, audio passthrough/IEC, Live TV, stream search, Vietnamese localization, GPU offscreen compositing, modern sidebar, custom fonts và subtitle outline đều còn nguyên.

---

## 6. Review ngữ nghĩa 18 file auto-merge

Không có xung đột văn bản ở 18 file giao nhau còn lại, nhưng đã review để đảm bảo không mất logic custom:

| File | Kết quả kiểm tra |
|---|---|
| `LayoutPreferenceDataStore.kt` | Có `heroDescriptionAutoScrollEnabled`, `gpuOffscreenCompositingEnabled`, `modernSidebarEnabled = flowOf(true)` |
| `HeroCarousel.kt` | Có `AutoScrollingText`, `descriptionScrollHoldIndex`, hold auto-advance; giữ `animationsEnabled`, `crossfade(false)`, bỏ Offscreen |
| `HomeViewModelPresentationPipeline.kt` | `combine` 3 flow + custom `debounce { if (layoutPreferencesReady) 300 else 0 }`; truyền state hero auto-scroll |
| `ModernHomeHero.kt` | Có `AutoScrollingText`, `paused = trailerPlaying`; giữ `animationsEnabled`, `snap`, debounce backdrop, GPU offscreen opt-in |
| `ModernHomeContent.kt` | Cả 2 call site `HeroCarousel`/`HeroTitleBlock` nhận `descriptionAutoScrollEnabled` |
| `ClassicHomeContent.kt` | `HeroCarousel` nhận `descriptionAutoScrollEnabled` |
| `GridHomeContent.kt` | Cả 2 call site `HeroCarousel` nhận `descriptionAutoScrollEnabled` |
| `LayoutHomeSettings.kt` | Toggle mới nằm đúng section; custom sidebar section còn nguyên |
| `LayoutSettingsViewModel.kt` | State/event/setter mới + custom `showStreamSearch`, sidebar luôn bật |
| `PlayerRuntimeController.kt` | Có `exoEndOverrunHandled`, `loadedTrackPreference` + custom TorrServer/audio/tunnel/watchdog fields |
| `PlayerRuntimeControllerInitialization.kt` | Reset cờ overrun + custom Live TV buffer/DRM/subtitle style |
| `PlayerRuntimeControllerPlaybackEvents.kt` | `checkExoPlaybackOverrun(...)` được gọi trong progress loop + custom TorrServer status |
| `PlayerRuntimeControllerStreams.kt` | Reset cờ overrun + custom TorrServer/torrent logic |
| `PlayerRuntimeControllerTrackSelection.kt` | `currentTrackPreferenceForPersistence` dùng `loadedTrackPreference` + custom track logic |
| `values-fr/strings.xml` | 3 key mới, không trùng, `app_name` custom còn nguyên |
| `values-hu/strings.xml` | Cập nhật theo upstream, không trùng key |
| `values-vi/strings.xml` | 2 key mới của upstream + bản dịch Việt custom còn nguyên |
| `values/strings.xml` | 2 key hero auto-scroll + custom strings còn nguyên |

### 6.1 Kiểm tra symbol sau merge

| Symbol | Số file khớp |
|---|---:|
| `checkExoPlaybackOverrun` | 1 |
| `exoEndOverrunHandled` | 5 |
| `loadedTrackPreference` | 3 |
| `SELECTED_THEN_SEARCH` | 2 |
| `searchAfterSelectedFailed` | 2 |
| `bestAnimeSkipIntervals` | 1 |
| `baseDuration` | 2 |
| `AutoScrollingText` | 3 |
| `heroDescriptionAutoScrollEnabled` | 8 |
| `torrServerRemoteApi` | 5 |
| `torrServerAddonConfig` | 6 |
| `isTorrServerStream` | 9 |
| `tunnelFrameReportDecoders` | 1 |
| `applyOutlineWidth` | 3 |
| `LocalGpuOffscreenCompositing` | 9 |
| `gpuOffscreenCompositingEnabled` | 4 |
| `showStreamSearch` | 5 |

---

## 7. Kiểm tra tĩnh

| Kiểm tra | Kết quả |
|---|---|
| Conflict marker (`<<<<<<<`, `=======`, `>>>>>>>`) | Không có |
| `git diff --cached --check` | Pass (exit 0) |
| Parse XML resource | 66 file, **0 lỗi** |
| Tổng resource name | 100.926 |
| Duplicate resource name | **0** |
| P2P key `values-ja` | 32 → 22, thiếu đúng 10 key custom |
| `app_name` (`values-ja`, `values-vi`, `values`) | `Nuvio TV` |
| `versionCode` / `versionName` | `1175` / `"1.3.3"` |
| `values-bn/strings_party.xml` | Tồn tại |
| `values-bn/strings_servers.xml` | Tồn tại |

---

## 8. Build và test

### 8.1 Compile

```bash
.\gradlew.bat :app:compileFullDebugKotlin --console=plain
```

Kết quả:

```text
BUILD SUCCESSFUL in 5m 8s
18 actionable tasks: 14 executed, 4 up-to-date
```

- Chỉ có warning (Kapt/Moshi deprecated), không có lỗi compile.
- Log: `C:\Users\NongTuan\AppData\Local\Temp\opencode\tet2-compile-20261107.log`.

### 8.2 Unit test trọng tâm

```bash
.\gradlew.bat :app:testFullDebugUnitTest ^
  --tests "com.nuvio.tv.data.repository.AnimeSkipIntervalsTest" ^
  --tests "com.nuvio.tv.ui.screens.player.autosync.*" ^
  --tests "com.nuvio.tv.data.repository.TraktEpisodeMappingServiceTest" ^
  --console=plain
```

Kết quả:

```text
BUILD SUCCESSFUL in 3m 16s
33 actionable tasks: 10 executed, 23 up-to-date

12 test suite:
  - AnimeSkipIntervalsTest
  - TraktEpisodeMappingServiceTest
  - 10 suite autosync (AutoSyncActivityPlateau, AssDialogueFilter, NoFitTracker,
    ReferenceConsistency, SubtitleCueParser, SubtitleHttp, SubtitleTextRetimer,
    TimelineRetime, EmbeddedSubtitleTimelineLoader, RetimedAssSubtitleParser)
Tổng: 78 test, 0 failure, 0 error, 0 skipped
```

- Log: `C:\Users\NongTuan\AppData\Local\Temp\opencode\tet2-tests-20261107.log`.

### 8.3 Chưa thực hiện

- Chưa chạy `:app:assembleFullDebug` (đóng gói APK đầy đủ).
- Chưa test runtime trên TV box/emulator: hero auto-scroll, Exo end-overrun, Anime-Skip fallback.

---

## 9. Tùy chỉnh của fork được giữ nguyên

| Khu vực | Trạng thái |
|---|---|
| App identity, endpoint, updater, signing (`app/build.gradle.kts`) | Giữ nguyên; version `1175 / 1.3.3` |
| TorrServer / Torrent (`TorrServer*`, `Torrent*`, `PlayerRuntimeControllerTorrent`) | Còn nguyên |
| Modern Leanback Home / sidebar / neon glow | Còn nguyên; sidebar luôn bật |
| Audio passthrough / IEC / surround | Còn nguyên |
| Live TV / IPTV | Còn nguyên |
| Stream search bar + stream ordering/badges | Còn nguyên |
| Custom player runtime / tunnel A/V sync / watchdog | Còn nguyên |
| Custom subtitle outline/style | Còn nguyên |
| Vietnamese localization + custom fonts | Còn nguyên |
| P2P tiếng Nhật | Xóa đúng 10 key cũ; giữ 22 key |
| `app_name = "Nuvio TV"` | Còn nguyên |
| GPU offscreen compositing opt-in | Còn nguyên, mặc định tắt |

---

## 10. Rủi ro còn lại và khuyến nghị

| Rủi ro | Mức | Khuyến nghị |
|---|---|---|
| Hero auto-scroll tốn CPU/RAM trên TV box yếu | Trung bình | Setting mặc định **tắt**; test trên thiết bị trước khi phát hành |
| Exo end-overrun kích hoạt nhầm | Trung bình | Test các luồng kết thúc tự nhiên, tua sát cuối, audio-only |
| Anime-Skip đổi thứ tự ưu tiên | Trung bình | Đã pass `AnimeSkipIntervalsTest`; test thêm vài anime thực tế |
| 10 chuỗi P2P tiếng Nhật fallback tiếng Anh | Thấp | Chấp nhận theo custom; có thể khôi phục nếu người dùng đổi quyết định |
| Chưa test runtime trên thiết bị | Trung bình | Chạy APK trên TV box trước khi push/phát hành |

---

## 11. Rollback

### Nếu cần bỏ toàn bộ merge và quay về trước đồng bộ

```bash
git reset --hard backup-tet2-before-merge-20261107
```

### Nếu chỉ bỏ commit merge, giữ commit version

```bash
git reset --hard 634257f5f
```

### Nếu muốn khôi phục patch dự phòng

```bash
git apply C:/Users/NongTuan/AppData/Local/Temp/opencode/tet2-pre-merge-20261107.patch
```

> Backup branch `backup-tet2-before-merge-20261107` trỏ tới `df8b0480c` — trạng thái `tet2` trước khi commit version và trước merge.

---

## 12. Checklist hoàn thành

- [x] 26 commit của `dev` sau mốc `1206736fd` đã nằm trong `tet2`.
- [x] Không còn conflict marker.
- [x] 2 xung đột đã xử lý đúng kế hoạch.
- [x] `:app:compileFullDebugKotlin` thành công.
- [x] Unit test trọng tâm pass.
- [x] Custom TorrServer/UI/Audio/Live TV/Stream search/Vietnamese strings còn nguyên.
- [x] Custom xóa 10 key P2P tiếng Nhật được giữ; 20 key P2P mới vẫn có bản dịch.
- [x] `app_name` = `Nuvio TV` được giữ.
- [x] Version 1.3.3 (1175) được giữ.
- [x] Báo cáo sau merge được tạo.
- [x] Không push.

---

## 13. Trạng thái cuối

```text
Merge commit:      d7d03a87d
Merge parents:     634257f5f (tet2) + fe5c314bd (dev)
HEAD..dev:         0
HEAD..origin/dev:  0
Working tree:      sạch sau commit tài liệu
Push:              không thực hiện
```
