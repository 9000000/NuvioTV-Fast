# Báo cáo xung đột trước khi sửa — đồng bộ `dev` → `tet2` (2026-11-07)

> Tài liệu này được tạo **trước khi thực hiện merge và trước khi sửa bất kỳ xung đột nào**.
> Công cụ dự báo: `git merge-tree --write-tree --name-only tet2 origin/dev`.
> Tree dự báo: `cca2293893c7b9cba9b577dc2efa8649db12cd0c`.
> Kết quả: đúng **2 file** xung đột nội dung.

---

## Tổng quan

| # | File | Số hunk | Mức độ | Hướng xử lý |
|---|---|---|---|---|
| 1 | `app/build.gradle.kts` | 1 | Thấp | Giữ version của `tet2`: `1175 / 1.3.3` |
| 2 | `app/src/main/res/values-ja/strings.xml` | 1 | Trung bình | Lấy bản dịch mới của `dev`, giữ xóa 10 key P2P |

Không có xung đột ở 18 file giao nhau còn lại. Tuy nhiên, 18 file này vẫn được review ngữ nghĩa theo checklist trong `2026-11-07-dev-sync-plan.md`.

### Trạng thái làm việc tại thời điểm dự báo

```text
Branch: tet2
HEAD:   df8b0480c
Local:  app/build.gradle.kts modified (1174 -> 1175, 1.3.2 -> 1.3.3)
Dev:    fe5c314bd
Base:   1206736fd
```

> Dự báo chạy trên HEAD `df8b0480c` (version 1174). Sau khi commit thay đổi cục bộ, xung đột thực tế ở `app/build.gradle.kts` sẽ là `1175 / 1.3.3` (ours) so với `1075 / 1.1.0-beta.12` (theirs). Bản chất, vị trí và hướng xử lý không đổi.

---

## 1. `app/build.gradle.kts`

### 1.1 Nội dung xung đột dự báo

```text
<<<<<<< tet2
        versionCode = 1174
        versionName = "1.3.2"
=======
        versionCode = 1075
        versionName = "1.1.0-beta.12"
>>>>>>> origin/dev
```

Sau khi commit thay đổi cục bộ, ours sẽ là:

```text
<<<<<<< tet2
        versionCode = 1175
        versionName = "1.3.3"
=======
        versionCode = 1075
        versionName = "1.1.0-beta.12"
>>>>>>> origin/dev
```

### 1.2 Phân tích

- `tet2` dùng scheme version riêng `1.3.x`; bản cục bộ đang chuẩn bị `1.3.3` (1175).
- `dev` dùng scheme `1.1.0-beta.x`; bản mới nhất là `1.1.0-beta.12` (1075).
- Đây không phải xung đột logic, chỉ là hai scheme version khác nhau.
- Commit `fe5c314bd` của `dev` chỉ thay đổi đúng 2 dòng version trong file này; không có thay đổi chức năng nào khác bị bỏ sót nếu giữ ours.
- Nếu lấy upstream, versionCode giảm từ 1175 → 1075 và versionName lùi về beta; không phù hợp với fork.

### 1.3 Quyết định

Giữ:

```kotlin
versionCode = 1175
versionName = "1.3.3"
```

### 1.4 Ảnh hưởng

- Không ảnh hưởng code.
- Giữ đúng bản release riêng của `tet2`.
- Sẽ ghi rõ trong báo cáo sau merge.

---

## 2. `app/src/main/res/values-ja/strings.xml`

### 2.1 Nội dung xung đột

Xung đột nằm sau `stream_type_youtube`, tại vùng chuỗi P2P:

```text
<<<<<<< tet2
      <string name="settings_p2p_hide_stats_title">トレント統計を非表示</string>
      <string name="settings_p2p_hide_stats_subtitle">読み込み中と再生中にバッファ、シード、ピア、ダウンロード速度を非表示</string>
=======
      <string name="p2p_consent_title">P2Pストリーミング</string>
      <string name="p2p_consent_body">このストリームはピアツーピア(P2P)技術を使用します。...</string>
      <string name="p2p_consent_enable">P2Pを有効にする</string>
      <string name="p2p_consent_cancel">キャンセル</string>
      <string name="settings_p2p_title">P2Pストリーミング</string>
      <string name="settings_p2p_subtitle">ピアツーピア(トレント)ストリームを許可</string>
      <string name="settings_p2p_hide_stats_title">トレント統計を非表示</string>
      <string name="settings_p2p_hide_stats_subtitle">読み込み中と再生中にバッファ、シード、ピア、ダウンロード速度を非表示</string>
      <string name="settings_p2p_profile_title">トレントプロファイル</string>
      <string name="settings_p2p_profile_soft">ソフト</string>
      <string name="settings_p2p_clear_cache_done">非アクティブなトレントデータを%1$sクリアしました</string>
      <string name="settings_p2p_clear_cache_failed">トレントキャッシュをクリアできませんでした</string>
      <string name="p2p_download_title">P2Pエンジンをダウンロード中</string>
      <string name="p2p_download_subtitle">P2Pストリーミングに必要な1回限りのダウンロードです。</string>
>>>>>>> origin/dev
```

### 2.2 Phân tích key-level

`tet2` hiện chỉ còn 2 key P2P trong `values-ja`:

```text
settings_p2p_hide_stats_title
settings_p2p_hide_stats_subtitle
```

`dev` có tổng cộng **32 key P2P**, chia làm 3 nhóm:

**Nhóm A — 10 key P2P cũ bị `tet2` chủ động xóa:**

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

**Nhóm B — 2 key hide-stats `tet2` vẫn giữ:**

```text
settings_p2p_hide_stats_title
settings_p2p_hide_stats_subtitle
```

**Nhóm C — 20 key P2P mới của upstream (profile/cache/clear-cache):**

```text
settings_p2p_profile_title
settings_p2p_profile_soft
settings_p2p_profile_soft_description
settings_p2p_profile_balanced
settings_p2p_profile_balanced_description
settings_p2p_profile_fast
settings_p2p_profile_fast_description
settings_p2p_cache_size_title
settings_p2p_cache_size_description
settings_p2p_cache_none
settings_p2p_cache_2_gb
settings_p2p_cache_5_gb
settings_p2p_cache_10_gb
settings_p2p_clear_cache_title
settings_p2p_clear_cache_usage
settings_p2p_clear_cache_usage_pending
settings_p2p_clear_cache_clearing
settings_p2p_clear_cache_done
settings_p2p_clear_cache_failed
settings_p2p_clear_cache_playback_active
```

So sánh key set:

- `tet2` không có key nào mà `dev` không có (`tet2` là tập con của `dev`).
- `dev` có khoảng **725 key mới** so với `tet2`, chủ yếu là bản dịch tiếng Nhật cho các tính năng mới.
- Trong 32 key P2P của `dev`, custom `tet2` chỉ chủ động xóa 10 key thuộc Nhóm A; 2 key Nhóm B vẫn giữ và 20 key Nhóm C là bản dịch mới chưa từng bị custom loại bỏ.

### 2.3 Nguyên nhân

- `tet2` đã có lịch sử custom xóa 10 key P2P khỏi `values-ja` (commit `db6266716` "Cập nhật lang", sau đó đi qua các merge).
- Upstream vừa cập nhật lớn bản dịch tiếng Nhật (+819 / −83), đưa các key P2P trở lại đúng vị trí chèn.
- Đây là xung đột vị trí chèn, không phải xung đột logic.

### 2.4 Quyết định

1. Lấy **toàn bộ bản dịch tiếng Nhật mới của `dev`** cho tất cả các vùng.
2. Xóa lại **đúng 10 key P2P cũ thuộc Nhóm A** mà `tet2` đã chủ động xóa.
3. Giữ 2 key `settings_p2p_hide_stats_*` (Nhóm B) và toàn bộ 20 key P2P mới của upstream (Nhóm C).
4. Giữ `app_name` = `Nuvio TV` (dòng này nằm ngoài hunk xung đột, auto-merge giữ custom).

### 2.5 Lý do

- Tôn trọng tùy chỉnh đã có của `tet2` (10 key Nhóm A vẫn bị xóa).
- Vẫn nhận được khoảng 725 key bản dịch tiếng Nhật mới của upstream, bao gồm 20 key P2P mới Nhóm C.
- Không ảnh hưởng compile: các key P2P bị thiếu sẽ fallback về `values/strings.xml` tiếng Anh.

### 2.6 Trade-off cần báo cáo

- Người dùng tiếng Nhật sẽ thấy 10 chuỗi P2P Nhóm A bằng tiếng Anh fallback thay vì tiếng Nhật.
- 20 chuỗi P2P mới Nhóm C vẫn có bản dịch tiếng Nhật vì chưa từng bị custom loại bỏ.
- Nếu người dùng muốn ưu tiên bản dịch mới hơn bản sắc custom, có thể khôi phục 10 key Nhóm A. Kế hoạch mặc định giữ custom.

### 2.7 Kiểm tra sau khi sửa

- Không còn `<<<<<<<`, `=======`, `>>>>>>>`.
- `app_name` vẫn là `Nuvio TV`.
- Đúng 10 key P2P Nhóm A không quay lại; 2 key hide-stats và 20 key P2P mới vẫn còn (tổng 22 key).
- Các key tiếng Nhật mới của upstream vẫn hiện diện.
- XML parse thành công, không duplicate key.

---

## 3. Vì sao 18 file giao nhau còn lại không xung đột văn bản?

Git auto-merge được vì các thay đổi nằm ở vùng khác nhau. Tuy nhiên, auto-merge **không đảm bảo đúng ngữ nghĩa**. Các file sau sẽ được review theo checklist trong plan:

| File | Điểm giao nhau cần review |
|---|---|
| `LayoutPreferenceDataStore.kt` | `heroDescriptionAutoScrollEnabled` mới + custom `gpuOffscreenCompositingEnabled` + `modernSidebarEnabled = flowOf(true)` |
| `HeroCarousel.kt` | `AutoScrollingText` + hold auto-advance + custom `animationsEnabled`, `crossfade(false)`, bỏ Offscreen |
| `HomeViewModelPresentationPipeline.kt` | `combine` 3 flow + custom `debounce` có điều kiện |
| `ModernHomeHero.kt` | `AutoScrollingText` + `paused = trailerPlaying` + custom `animationsEnabled`, `snap`, debounce backdrop, GPU offscreen opt-in |
| `ModernHomeContent.kt` | 2 call site `HeroCarousel`/`HeroTitleBlock` phải nhận `descriptionAutoScrollEnabled` |
| `ClassicHomeContent.kt` | `HeroCarousel` phải nhận `descriptionAutoScrollEnabled` |
| `GridHomeContent.kt` | 2 call site `HeroCarousel` phải nhận `descriptionAutoScrollEnabled` |
| `LayoutHomeSettings.kt` | Toggle mới + custom sidebar section |
| `LayoutSettingsViewModel.kt` | State/event/setter mới + custom `showStreamSearch`, sidebar luôn bật |
| `PlayerRuntimeController.kt` | `exoEndOverrunHandled`, `loadedTrackPreference` + custom TorrServer/audio/tunnel/watchdog fields |
| `PlayerRuntimeControllerInitialization.kt` | Reset cờ overrun + custom Live TV buffer/DRM/subtitle style |
| `PlayerRuntimeControllerPlaybackEvents.kt` | `checkExoPlaybackOverrun` + custom TorrServer status |
| `PlayerRuntimeControllerStreams.kt` | Reset cờ overrun + custom TorrServer/torrent logic |
| `PlayerRuntimeControllerTrackSelection.kt` | `loadedTrackPreference` + custom track logic |
| `values-fr/strings.xml` | 3 key mới, không trùng, `app_name` custom còn nguyên |
| `values-hu/strings.xml` | 45 dòng thay đổi, không trùng key |
| `values-vi/strings.xml` | 2 key mới + bản dịch Việt custom còn nguyên |
| `values/strings.xml` | 2 key hero auto-scroll + custom strings còn nguyên |

### 3.1 Kiểm tra merge-tree trên các file auto-merge

Đã kiểm tra trực tiếp tree dự báo `cca2293893c7b9cba9b577dc2efa8649db12cd0c`:

- `HomeViewModelPresentationPipeline.kt` có đồng thời:
  - `combine(baseLayoutUiPrefsFlow, modernLayoutPrefsFlow, heroDescriptionAutoScrollEnabled)`
  - `debounce { if (_uiState.value.layoutPreferencesReady) 300L else 0L }`
  - `heroDescriptionAutoScrollEnabled = prefs.heroDescriptionAutoScrollEnabled`
- `HeroCarousel.kt` có đồng thời:
  - `AutoScrollingText(...)`
  - `descriptionScrollHoldIndex`
  - `snapshotFlow { ... }.first { !it }`
  - custom `animationSpec = tween(if (NuvioTheme.animationsEnabled) 300 else 0)`
  - custom `crossfade(false)` cho logo/backdrop
- `ModernHomeContent.kt`, `ClassicHomeContent.kt`, `GridHomeContent.kt` đều có `descriptionAutoScrollEnabled = uiState.heroDescriptionAutoScrollEnabled`.
- `LayoutPreferenceDataStore.kt` có đồng thời `heroDescriptionAutoScrollEnabled`, `gpuOffscreenCompositingEnabled`, `modernSidebarEnabled = flowOf(true)`.
- `PlayerRuntimeController.kt` có đồng thời `exoEndOverrunHandled`, `loadedTrackPreference`, `torrServerRemoteApi`, `torrServerAddonConfig`, `tunnelAvSyncWatchdogJob`.
- `PlayerRuntimeControllerPlaybackEvents.kt` có đồng thời `checkExoPlaybackOverrun(...)` và custom `isTorrServerStream` status.

---

## 4. Kết luận

- 2 xung đột đều có hướng xử lý rõ ràng, không cần hỏi thêm.
- Không có xung đột nào liên quan đến logic TorrServer, audio, Live TV, stream search hay UI custom.
- Sau khi sửa sẽ chạy compile + test trọng tâm trước khi commit merge.
- Quyết định đáng chú ý nhất: giữ xóa 10 key P2P tiếng Nhật; trade-off đã được ghi rõ ở mục 2.6.
