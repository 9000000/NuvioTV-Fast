# Kế hoạch đồng bộ nhánh `dev` → `tet2` (2026-11-10)

> **Trạng thái**: Chờ người dùng phê duyệt trước khi thực thi (`PRE-MERGE PLAN`).
> Mọi số liệu, danh sách commit, file thay đổi và xung đột dưới đây đã được kiểm tra trực tiếp bằng Git (`git merge-tree --write-tree tet2 upstream/dev`) trên repository `F:\MyGithub\NuvioTV-Fast`.

---

## 1. Tóm tắt điều hành (Executive Summary)

| Hạng mục | Giá trị |
|---|---|
| **Nhánh đích** | `tet2` |
| **HEAD hiện tại của `tet2`** | `ee4516a20` — `Merge remote-tracking branch 'origin/dev' into tet2` |
| **Thay đổi cục bộ chưa commit trên `tet2`** | **27 file** (Toàn bộ đợt tối ưu Android Leanback Native 4 giai đoạn & sửa lỗi D-pad SearchScreen ngày 2026-11-10; **0 file giao nhau** với `upstream/dev`) |
| **Nguồn đồng bộ** | `upstream/dev` = `origin/dev` = `65538103f` (`Merge pull request #4546 from scheperr/dev`, 2026-11-09) |
| **Merge base (`tet2`, `upstream/dev`)** | `fe5c314bd` — `bump version` (2026-11-06, mốc đồng bộ ngày 2026-11-07) |
| **Phạm vi yêu cầu** | Tất cả commit mới nhất từ nhánh `dev` từ ngày `2026-11-07` đến hôm nay (`2026-11-10`) |
| **Tổng số commit cần đồng bộ** | **50 commit** sau mốc `fe5c314bd` (**33 commit thường** + **17 merge commit**) |
| **Thay đổi từ `upstream/dev`** | **45 file**, `+3.842 / −576` dòng |
| **Thay đổi custom của `tet2` so với base** | **371 file** (đã commit) + **27 file** (working tree Leanback Native) |
| **File giao nhau (Overlapping files)** | **19 file** (giữa lịch sử `tet2` và `upstream/dev`; **0 file** đụng độ với 27 file working tree hiện tại) |
| **Xung đột văn bản dự báo (`CONFLICT`)** | **3 file**: `app/build.gradle.kts`, `app/src/main/java/com/nuvio/tv/ui/screens/player/ParallelRangeDataSource.kt`, `app/src/main/res/values-nl/strings.xml` |
| **Tương tác ngữ nghĩa cần nâng cấp phối hợp** | **1 điểm**: `ModernHomeModels.kt` (`continueWatchingItemKey` theo show) ↔ `LeanbackModernRow.kt` (tách `imageCacheKey` theo `imageUrl ?: key` để vừa giữ focus khi chuyển tập vừa cập nhật đúng ảnh/màu hào quang tập mới) |
| **Chiến lược thực thi** | Tạo nhánh backup + patch dự phòng → Commit 27 file tối ưu Leanback Native hiện tại → Fast-forward nhánh local `dev` lên `65538103f` → Merge `--no-ff` `dev` vào `tet2` → Giải quyết 3 conflict + cập nhật tương thích `LeanbackModernRow.kt` → Kiểm tra XML/key/symbol → Chạy `./gradlew compileFullDebugKotlin` & unit tests trọng tâm → Xuất báo cáo kết quả |

---

## 2. Xác minh Ref, Nhánh và Môi trường

```text
origin          https://github.com/9000000/NuvioTV-Fast.git
upstream        https://github.com/NuvioMedia/NuvioTV.git

tet2 (HEAD)     ee4516a20
local dev       fe5c314bd (sẽ fast-forward lên 65538103f)
origin/dev      65538103f
upstream/dev    65538103f
merge-base      fe5c314bd
```

### Bảo toàn 27 file thay đổi cục bộ chưa commit trên `tet2`

Hiện tại working tree của `tet2` đang chứa 27 file thuộc đợt tối ưu hóa toàn diện **Android Leanback Native** và sửa triệt để **D-pad SearchScreen**:
- `app/src/main/java/com/nuvio/tv/ui/leanback/**` (9 file core/holder/adapter/container)
- `app/src/main/java/com/nuvio/tv/ui/screens/detail/**` (6 file Detail sections)
- `app/src/main/java/com/nuvio/tv/ui/screens/search/SearchScreen.kt`
- `app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt`
- `app/src/main/java/com/nuvio/tv/ui/components/CatalogRowSection.kt`
- `app/src/main/java/com/nuvio/tv/ui/screens/CatalogSeeAllScreen.kt`, `CastDetailScreen.kt`, `FolderDetailScreen.kt`, `StreamScreen.kt`, `TmdbEntityBrowseScreen.kt`, `DpadThrottleModifier.kt`
- `ERRORS.md`, `PLAN-leanback-native-audit.md`

> **Kiểm chứng giao thoa**: Đã đối chiếu danh sách 27 file này với 45 file thay đổi trên `upstream/dev` — kết quả **0 file trùng nhau**. Toàn bộ 27 file sẽ được commit thành một commit riêng biệt trước khi chạy lệnh `git merge --no-ff`.

---

## 3. Phân nhóm chi tiết 50 Commit từ `dev` (2026-11-07 → 2026-11-10)

### 3.1 Nhóm Continue Watching & Đồng bộ Trạng thái Xem (Home & Sync — 6 commit)

| # | Hash | Ngày | Tác giả | Nội dung |
|---|---|---|---|---|
| 1 | `e12bc8752` | 2026-11-08 | kernexshadow | `fix(sync): pull watch state early on startup and after in-app navigation` |
| 2 | `88c27bbde` | 2026-11-08 | kernexshadow | `fix(home): keep a series in Continue Watching while its next-up is rebuilt` |
| 3 | `4eb7dda70` | 2026-11-08 | kernexshadow | `fix(home): key Continue Watching cards by show` |
| 4 | `48bef4f36` | 2026-11-09 | skoruppa | `Merge pull request #4529 from kernexshadow/fix/tv-sync-progress-first` |
| 5 | `24d71a532` | 2026-11-09 | skoruppa | `Merge pull request #4531 from kernexshadow/fix/cw-focus-after-episode-finish` |
| 6 | `b1bf4bd83` | 2026-11-07 | chrichts | `fix(trakt): keep episode numbers on writes when Trakt has the episode` (+ merge `60f85970c`) |

**Công dụng & Phân tích kỹ thuật:**
- **`StartupSyncService.kt` & `MainActivity.kt` (`e12bc8752`)**: Kéo trạng thái xem (`pullWatchState`) lên đầu quy trình đồng bộ khởi động (trước khi tải catalog/addon nặng), và tự động gọi `startupSyncService.requestNavigationSync()` khi người dùng rời khỏi `Screen.Player` hoặc quay về `Screen.Home` trong cùng Activity. Giúp hàng Continue Watching cập nhật tức thì ngay khi thoát trình phát.
- **`ContinueWatchingNextUpReconciliation.kt` & `HomeViewModelContinueWatching.kt` (`88c27bbde`)**: Giữ nguyên thẻ series trong hàng Continue Watching trong lúc tập tiếp theo (`NextUp`) đang được tính toán lại (`awaitingNextUpItems`), loại bỏ hiện tượng thẻ phim biến mất rồi hiện lại gây giật hàng.
- **`ModernHomeModels.kt`, `ContinueWatchingSection.kt`, `GridContinueWatchingSection.kt` (`4eb7dda70`)**: Đổi khóa định danh của thẻ Continue Watching từ theo từng tập sang theo bộ phim (`"cw_${item.contentId()}"`), giúp khi chuyển từ tập đang xem sang tập kế tiếp, thẻ không bị coi là item mới và không làm mất focus sang thẻ bên cạnh.
- **`TraktEpisodeMapping.kt` & `TraktEpisodeMappingService.kt` (`b1bf4bd83`)**: Giữ nguyên số season/episode khi ghi lịch sử lên Trakt nếu Trakt đã có tập đó.

### 3.2 Nhóm Player, Audio, Phụ đề & AutoSync (9 commit)

| # | Hash | Ngày | Tác giả | Nội dung |
|---|---|---|---|---|
| 8 | `18c256fb6` | 2026-11-07 | tapframe | `fix(player): end parallel reads at a short chunk instead of recursing` |
| 9 | `26949b898` | 2026-11-07 | tapframe | `fix(player): skip subtitle width measuring when the cue list empties` |
| 10 | `2fb930767` | 2026-11-07 | tapframe | `fix(autosync): stop timeline load waiters from retrying a cancelled load forever` |
| 11 | `3eb236c40` | 2026-11-07 | tapframe | `fix(player): read a stored playback speed of any number type` |
| 12 | `7bc69ea42` | 2026-11-08 | skoruppa | `feat(subtitles): enable forced subtitles and AutoSync by default` (+ merge `9b778ac47`) |
| 14 | `42b1b64ff` | 2026-11-09 | halibiram | `fix(player): honor pinned audio capabilities for forced AC-3 transcoding` (+ merge `b5c1bca22`) |

**Công dụng & Phân tích kỹ thuật:**
- **`ParallelRangeDataSource.kt` (`18c256fb6`)**: Khi một chunk không phải bootstrap kết thúc sớm hơn `chunkSize` (short chunk), trả về `C.RESULT_END_OF_INPUT` thay vì gọi đệ quy `read(...)` vô hạn gây `StackOverflowError`. **(Xung đột văn bản nhẹ với comment của `tet2` — code mới tốt hơn rõ rệt, sẽ áp dụng logic mới và giữ nguyên các tối ưu RAM/OOM/subtitle của `tet2`)**.
- **`PlayerScreen.kt` (`26949b898`)**: Dùng `maxOfOrNull` thay vì `maxOf` khi đo chiều rộng khối phụ đề để tránh `NoSuchElementException` khi danh sách cue rỗng do race condition.
- **`EmbeddedSubtitleTimelineLoader.kt` (`2fb930767`)**: Xóa `activeLoad` đã bị hủy khỏi `inFlight` trước khi thử tải lại, ngăn vòng lặp đệ quy vô tận giữa các coroutine đang chờ.
- **`TrackPreferenceDataStore.kt` (`3eb236c40`)**: Đọc tốc độ phát (`PLAYBACK_SPEED`) qua `(asMap()[...] as? Number)?.toFloat()` để không bị crash `ClassCastException` nếu dữ liệu cũ lưu dưới dạng `Double` hoặc `Int`.
- **`PlayerSettingsDataStore.kt` & `AutoSyncPreferences.kt` (`7bc69ea42`)**: Bật mặc định phụ đề bắt buộc (`useForcedSubtitles = true`) và tự động đồng bộ phụ đề (`AutoSync = true`) cho người dùng mới.
- **`PlayerRuntimeControllerInitialization.kt` (`42b1b64ff`)**: Sử dụng `DefaultAudioSink.Builder()` không truyền `context` trong nhánh `forceOpticalPassthrough` để Media3 không tạo `AudioCapabilitiesReceiver` ghi đè cấu hình AC-3 đã ghim.

### 3.3 Nhóm CloudStream Plugins, Updater, JobScheduler, Collections & Settings (13 commit)

| # | Hash | Ngày | Tác giả | Nội dung |
|---|---|---|---|---|
| 16 | `1c0fada0f` | 2026-11-07 | halibiram | `fix(updater): add corruption handler to update_settings DataStore` (+ merge `8a70d4b58`) |
| 18 | `43fa2bb9b` | 2026-11-07 | tapframe | `Keep AppCompatActivity in ProGuard rules for CloudStream extensions` |
| 19 | `476d1d95a` | 2026-11-07 | tapframe | `fix(jobs): stop crashing when the job scheduler rejects or fails a request` |
| 20 | `0f2fe22b9` | 2026-11-07 | tapframe | `fix(updater): fall back to the system installer when the confirm screen is missing` |
| 21 | `179469c8b` | 2026-11-07 | tapframe | `fix(updater): open security settings when the unknown sources screen is missing` |
| 22 | `fce6ad835` | 2026-11-07 | tapframe | `guard the system installer fallback` |
| 23 | `1a5a5eadf` | 2026-11-07 | tapframe | `fix(nav): accept a missing title on the stream and player routes` |
| 24 | `3dcd45d66` | 2026-11-07 | tapframe | `fix(collections): hide file export below Android 10` |
| 25 | `4692380a9` | 2026-11-07 | tapframe | `fix(auth): ignore storage errors in the diagnostics report queue` |
| 26 | `39e9fa1e4` | 2026-11-07 | tapframe | `fix(collections): keep editor source keys unique for repeated sources` |
| 27 | `5a6f9a0b7` | 2026-11-07 | tapframe | `fix(settings): drop duplicate catalogs from the hero catalog picker` (+ merge `861d8673d`) |
| 29 | `9a8468e0f` | 2026-11-09 | halibiram | `fix(plugins): stub cloudstream MainActivity to stop .cs3 lifecycle crash` (+ merge `3f005fe8c`) |

**Công dụng & Phân tích kỹ thuật:**
- **CloudStream `.cs3` (`43fa2bb9b`, `9a8468e0f`)**: Thêm stub `com.lagradost.cloudstream3.MainActivity` và giữ `androidx.appcompat.app.**` trong ProGuard để các plugin `.cs3` kiểm tra `activity is MainActivity` không bị crash `NoClassDefFoundError` / `ClassCastException`.
- **JobScheduler & Updater (`1c0fada0f`, `476d1d95a`, `0f2fe22b9`, `179469c8b`, `fce6ad835`)**: Bọc an toàn `JobScheduler.scheduleSafely` / `hasPendingJob` (tránh crash khi OS giới hạn số lượng job), thêm `ReplaceFileCorruptionHandler` cho `update_settings` DataStore, và fallback an toàn khi ROM TV thiếu màn hình xác nhận cài đặt APK hoặc màn hình Unknown Sources.
- **Navigation, Collections & Settings (`1a5a5eadf`, `3dcd45d66`, `4692380a9`, `39e9fa1e4`, `5a6f9a0b7`)**: Cho phép `title` nullable trên route `Stream`/`Player`, ẩn xuất file Collection trên Android < 10, tạo key duy nhất khi lặp nguồn trong Collection Editor, và lọc `.distinctBy { it.key }` cho Hero Catalog Picker.

### 3.4 Nhóm Đa ngôn ngữ / Bản dịch (i18n — 18 commit) & Version Bump (2 commit)

- **Bản dịch mới/cập nhật**:
  - `values-vi/strings.xml` (`c7c6c1e44`, merge `155945acd`): Thêm 2 chuỗi tiếng Việt cho tính năng tự động cuộn mô tả Hero (`layout_hero_description_auto_scroll`, `layout_hero_description_auto_scroll_sub`).
  - `values-el/strings.xml` (`eae207ac3`, merge `3b4c9d82a`): Bổ sung 22 chuỗi tiếng Hy Lạp.
  - `values-fr/strings.xml` (`cbc51b211`, merge `f7dce128d`): Bổ sung 2 chuỗi tiếng Pháp.
  - `values-sv/strings.xml` (`0f2c2b087`, merge `e5c5a5d52`): Bổ sung 152 chuỗi tiếng Thụy Điển.
  - `values-pl/strings.xml` (`2ed5bda76`): Bổ sung 2 chuỗi tiếng Ba Lan.
  - `values-hu/strings.xml` (`f7f3a976d`, `0034a8e44`, merge `a935bcf47`, `ac3e850dd`, `bf6659810`): Thêm 2 chuỗi auto-scroll và chuẩn hóa văn phong tiếng Hungary.
  - `values-lt/strings.xml`, `strings_party.xml`, `strings_servers.xml` (`7e9bb6225`, merge `7bebafdab`): Cập nhật toàn diện tiếng Litva (+2.649 dòng).
  - `values-nl/strings.xml` (`7c30df178`, merge `65538103f`): Cập nhật bản dịch tiếng Hà Lan sau bản 1.1 (+279 dòng, **có 1 vùng xung đột P2P với `tet2`**).
- **Version Bump trên `upstream/dev`**:
  - `3eeb8b3d0` (`1075` → `1076`, `1.1.0-beta.12` → `1.1.0`)
  - `12d96bced` (`1076` → `1077`, `1.1.0`)
  - *(Sẽ giữ nguyên version của `tet2`: `versionCode = 1176`, `versionName = "1.3.4"`)*.

---

## 4. Phân tích 3 File Xung đột (Conflicts) & Đề xuất Cải tiến Phối hợp

Chi tiết từng dòng xung đột được trình bày đầy đủ trong [Báo cáo xung đột 2026-11-10](file:///f:/MyGithub/NuvioTV-Fast/docs/merges/2026-11-10-dev-sync-conflict-report.md). Tóm tắt phương án xử lý:

| # | File | Loại xung đột | Phương án xử lý |
|---|---|---|---|
| 1 | [`app/build.gradle.kts`](file:///f:/MyGithub/NuvioTV-Fast/app/build.gradle.kts#L135-L140) | Xung đột trực tiếp (Version scheme) | **Giữ `tet2` (`ours`)**: Bảo toàn `applicationId = "com.nuvio.tv.fast"`, `versionCode = 1176`, `versionName = "1.3.4"` và toàn bộ cấu hình build/signing/release riêng của `tet2`. |
| 2 | [`ParallelRangeDataSource.kt`](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/ParallelRangeDataSource.kt#L918-L930) | Xung đột trực tiếp (Comment `tet2` vs bản vá short-chunk `dev`) | **Lấy bản vá của `dev` + Giữ toàn bộ custom `tet2`**: Thay đoạn xử lý `available <= 0` tại dòng 920–929 bằng logic `wasBootstrap` và chặn đệ quy `if (!wasBootstrap && position / chunkSize == currentChunkIndex) return C.RESULT_END_OF_INPUT` từ commit `18c256fb6` (vì code mới ngăn chặn lỗi đệ quy vô hạn `StackOverflowError` khi gặp short chunk). Giữ nguyên 100% custom `MemoryBudget`, `nuvio_type=subtitle` và `OutOfMemoryError` guard của `tet2`. |
| 3 | [`values-nl/strings.xml`](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/res/values-nl/strings.xml#L215-L245) | Xung đột trực tiếp (Chuỗi P2P tiếng Hà Lan) | **Lấy bản dịch mới của `dev`, lọc bỏ 2 key P2P Nhóm A đã xóa**: Nhận toàn bộ bản dịch tiếng Hà Lan mới, giữ xóa 2 key `p2p_download_title` và `p2p_download_subtitle` (theo chuẩn xử lý P2P của `tet2`), đồng thời lọc sạch các key P2P Nhóm A nếu xuất hiện trong `values-lt` / `values-sv` và bảo toàn `app_name = Nuvio TV`. |
| 4 | [`LeanbackModernRow.kt`](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt#L818-L915) *(Nâng cấp phối hợp ngữ nghĩa)* | Không xung đột Git, nhưng liên kết với `4eb7dda70` (`ModernHomeModels.kt`) | **Báo cáo & đề xuất sửa 4 dòng trong `LeanbackModernRow.kt`**: Khi `ModernHomeModels.kt` đổi `continueWatchingItemKey` sang `"cw_${item.contentId()}"` để giữ focus trên thẻ show khi chuyển sang tập mới, `LeanbackCardViewHolder.bind` cần dùng `val imageCacheKey = item.imageUrl ?: item.key` để khi tập mới có thumbnail mới (cùng `item.key`), ảnh thumbnail và màu hào quang Neon vẫn được làm mới chính xác. |

---

## 5. Checklist Kiểm tra 16 File Giao nhau Tự động Merge (Auto-merged Overlaps)

| # | File | Nội dung từ `dev` | Tùy chỉnh của `tet2` cần bảo toàn |
|---|---|---|---|
| 1 | `MainActivity.kt` | Thêm `LaunchedEffect(navController)` gọi `startupSyncService.requestNavigationSync()` khi rời Player / về Home | Giữ nguyên `NativeSidebarView`, khóa `LocalOpenSidebar` cho root routes, `MemoryBudget`, ambient idle controller |
| 2 | `UpdateViewModel.kt` | Fallback sang system installer / security settings khi thiếu confirm/unknown-sources screen | Giữ nguyên logic kiểm tra cập nhật tùy chỉnh trên `tet2` |
| 3 | `PlayerSettingsDataStore.kt` | Đổi mặc định `useForcedSubtitles` sang `true` | Giữ nguyên toàn bộ cài đặt TorrServer, Audio, Tunneling, Buffer, Decoder của `tet2` |
| 4 | `ContinueWatchingSection.kt` | Dùng `continueWatchingItemKey(progress)` thống nhất | Giữ nguyên cấu trúc focus và UI card của `tet2` |
| 5 | `NuvioNavHost.kt` | Cho phép `title` nullable trong `Screen.Stream` và `Screen.Player` | Giữ nguyên các route và truyền tham số custom của `tet2` |
| 6 | `HomeViewModelContinueWatching.kt` | Giữ `awaitingNextUpItems` khi đang dựng lại `NextUp` | Giữ nguyên pipeline Continue Watching của `tet2` |
| 7 | `ModernHomeModels.kt` | `continueWatchingItemKey` trả về `"cw_${item.contentId()}"` | Giữ nguyên `catalogRowKey`, `catalogRowTitle` và model Home của `tet2` |
| 8 | `PlayerRuntimeControllerInitialization.kt` | `DefaultAudioSink.Builder()` không truyền `context` khi `forceOpticalPassthrough` | Giữ nguyên `forcePcmForBluetooth`, Live TV buffer, DRM, subtitle style của `tet2` |
| 9 | `PlayerScreen.kt` | Dùng `texts.maxOfOrNull` khi đo chiều rộng phụ đề | Giữ nguyên giao diện Player và overlay custom của `tet2` |
| 10 | `LayoutSettingsViewModel.kt` | Thêm `.distinctBy { it.key }` cho `availableCatalogs` | Giữ nguyên `showStreamSearch` và `modernSidebarEnabled = true` |
| 11–16 | `values-fr`, `values-hu`, `values-lt`, `values-pl`, `values-sv`, `values-vi` | Bổ sung bản dịch mới từ cộng đồng | Giữ nguyên `app_name = Nuvio TV`, bản dịch tiếng Việt custom, không trùng lặp key |

---

## 6. Quy trình Thực thi Từng bước (Sau khi Người dùng Phê duyệt)

- [ ] **Bước 1 — Sao lưu an toàn (Safety Backup)**:
  - Tạo nhánh sao lưu `backup-tet2-before-merge-20261110` tại HEAD hiện tại.
  - Xuất bản vá dự phòng toàn bộ working tree ra file tạm.
- [ ] **Bước 2 — Commit 27 file tối ưu Leanback Native & Search D-pad hiện tại**:
  - Commit toàn bộ 27 file đã kiểm chứng `BUILD SUCCESSFUL` vào nhánh `tet2` để working tree sạch 100% trước khi merge.
- [ ] **Bước 3 — Đồng bộ nhánh local `dev` và chạy `git merge --no-ff`**:
  - Cập nhật nhánh local `dev` lên `upstream/dev` (`65538103f`): `git branch -f dev upstream/dev`.
  - Chạy `git merge --no-ff --no-commit dev` trên nhánh `tet2`.
- [ ] **Bước 4 — Giải quyết 3 xung đột & Nâng cấp tương thích `LeanbackModernRow.kt`**:
  - Giải quyết `app/build.gradle.kts` (giữ `1176` / `"1.3.4"`).
  - Giải quyết `ParallelRangeDataSource.kt` (áp dụng guard short-chunk `wasBootstrap` của `dev`, giữ nguyên các tối ưu của `tet2`).
  - Giải quyết `values-nl/strings.xml` (nhận bản dịch mới, loại bỏ key P2P Nhóm A đã xóa) và kiểm tra P2P Nhóm A trên các locale vừa cập nhật.
  - Cập nhật `imageCacheKey = item.imageUrl ?: item.key` trong `LeanbackModernRow.kt`.
- [ ] **Bước 5 — Kiểm증 Tĩnh, Biên dịch & Chạy Unit Test**:
  - Quét conflict markers (`<<<<<<<`, `=======`, `>>>>>>>`) và kiểm tra XML duplicate keys trên toàn bộ `res/values*/strings*.xml`.
  - Chạy `./gradlew compileFullDebugKotlin --console=plain`.
  - Chạy các Unit Test mới từ `dev`: `TraktEpisodeMappingServiceTest`, `ContinueWatchingNextUpReconciliationTest`, `CloudStreamCompatStubTest`.
- [ ] **Bước 6 — Hoàn tất Commit Merge & Tạo Báo cáo Kết quả**:
  - Tạo commit merge và lưu báo cáo kết quả vào `docs/merges/2026-11-10-dev-sync-report.md` (không tự động push).

