# Báo cáo xung đột trước khi sửa — đồng bộ `dev` → `tet2` (2026-11-05)

> Tài liệu này được tạo **trước khi thực hiện merge và trước khi sửa bất kỳ xung đột nào**.
> Công cụ dự báo: `git merge-tree --write-tree --name-only tet2 dev`.
> Kết quả: đúng **3 file** xung đột nội dung.

---

## Tổng quan

| # | File | Số hunk | Mức độ | Hướng xử lý |
|---|---|---|---|---|
| 1 | `app/build.gradle.kts` | 1 | Thấp | Giữ version của `tet2` (1174 / 1.3.2) |
| 2 | `app/src/main/java/com/nuvio/tv/data/local/DeviceLocalPlayerPreferences.kt` | 1 | Thấp | Giữ cả hai block độc lập |
| 3 | `app/src/main/res/values-pt-rBR/strings.xml` | 2 | Trung bình | Lấy upstream cho 2 hunk; giữ custom `app_name` + P2P removal |

Không có xung đột ở 24 file giao nhau còn lại. Tuy nhiên, 24 file này vẫn được review ngữ nghĩa theo checklist trong `2026-11-05-dev-sync-plan.md`.

---

## 1. `app/build.gradle.kts`

### Nội dung xung đột

```text
<<<<<<< tet2
        versionCode = 1174
        versionName = "1.3.2"
=======
        versionCode = 1074
        versionName = "1.1.0-beta.11"
>>>>>>> dev
```

### Phân tích

- `tet2` dùng scheme version riêng `1.3.x`; bản cục bộ đang chuẩn bị `1.3.2` (1174).
- `dev` dùng scheme `1.1.0-beta.x`; bản mới nhất là `1.1.0-beta.11` (1074).
- Đây không phải xung đột logic, chỉ là hai scheme version khác nhau.
- Nếu lấy upstream, versionCode giảm từ 1174 → 1074 và versionName lùi về beta; không phù hợp với fork.

### Quyết định

Giữ:

```kotlin
versionCode = 1174
versionName = "1.3.2"
```

### Ảnh hưởng

- Không ảnh hưởng code.
- Giữ đúng bản release riêng của `tet2`.
- Sẽ ghi rõ trong báo cáo sau merge.

---

## 2. `app/src/main/java/com/nuvio/tv/data/local/DeviceLocalPlayerPreferences.kt`

### Nội dung xung đột

```text
    private val playerStatsHudActiveKey = booleanPreferencesKey("player_stats_hud_active")
<<<<<<< tet2
    private val tunnelFrameReportDecodersKey =
        androidx.datastore.preferences.core.stringSetPreferencesKey("tunnel_frame_report_decoders")

    /** Video decoders (with the firmware) seen reporting tunnelled frames on this box. */
    val tunnelFrameReportDecoders: Flow<Set<String>> = store.data.map { prefs ->
        prefs[tunnelFrameReportDecodersKey] ?: emptySet()
    }

    suspend fun addTunnelFrameReportDecoder(key: String) {
        store.edit { prefs ->
            prefs[tunnelFrameReportDecodersKey] =
                (prefs[tunnelFrameReportDecodersKey] ?: emptySet()) + key
        }
    }
=======
    private val releaseDecoderOnBackgroundKey =
        booleanPreferencesKey("release_decoder_on_background")
>>>>>>> dev
```

Phần cuối file (auto-merge, không xung đột) đã có sẵn:

```kotlin
    val releaseDecoderOnBackground: Flow<Boolean> = store.data.map { prefs ->
        prefs[releaseDecoderOnBackgroundKey] ?: false
    }

    suspend fun setReleaseDecoderOnBackground(enabled: Boolean) {
        store.edit { prefs ->
            prefs[releaseDecoderOnBackgroundKey] = enabled
        }
    }
```

### Phân tích

- `tet2` thêm cơ chế ghi nhận decoder nào đã báo cáo tunnel frame (`tunnelFrameReportDecoders`).
- `dev` thêm setting opt-in giải phóng decoder khi app xuống background (`releaseDecoderOnBackground`).
- Hai tính năng hoàn toàn độc lập, chỉ trùng vị trí chèn key.

### Quyết định

Giữ **cả hai**:

1. `tunnelFrameReportDecodersKey`
2. `tunnelFrameReportDecoders` flow
3. `addTunnelFrameReportDecoder(...)`
4. `releaseDecoderOnBackgroundKey`
5. `releaseDecoderOnBackground` flow (đã auto-merge ở cuối file)
6. `setReleaseDecoderOnBackground(...)` (đã auto-merge ở cuối file)

### Kiểm tra sau khi sửa

- Không có `<<<<<<<`, `=======`, `>>>>>>>`.
- Cả 2 key đều được khai báo đúng kiểu.
- Không lặp `store.edit` hoặc hàm setter.
- `DeviceLocalPlayerPreferences` vẫn compile.

---

## 3. `app/src/main/res/values-pt-rBR/strings.xml`

### Hunk 1 — `collections_editor_tmdb_watch_providers_helper`

```text
<<<<<<< tet2
    <string name="collections_editor_tmdb_watch_providers_helper">Use os IDs das plataformas no TMDB. Separe múltiplos por vírgula para \"E\", ou barras verticais (|) para \"OU\".</string>
=======
    <string name="collections_editor_tmdb_watch_providers_helper">Use os IDs das plataformas no TMDB. Separe múltiplos por vírgula para "E", ou barras verticais (|) para "OU".</string>
>>>>>>> dev
```

**Phân tích:** khác biệt chỉ là escape dấu nháy. Cả hai đều hợp lệ với Android string resource. Bản của `dev` là bản dịch mới.

**Quyết định:** lấy bản của `dev`.

---

### Hunk 2 — nhóm `dv7_libdovi_mode_*`

```text
<<<<<<< tet2
    <string name="dv7_libdovi_mode_caption">Modo de conversão (Automático). Não altere a menos que seja instruído. Escolha uma opção para forçá-la ou \"Nenhum\" para o padrão. Funciona apenas se a conversão para DV 8.1 estiver ativa.</string>
    <string name="dv7_libdovi_mode_row_title">Modo de conversão</string>
    <string name="dv7_libdovi_mode_none_title">Nenhum</string>
    <string name="dv7_libdovi_mode_none_sub">Sem alterações forçadas. Usa o modo de conversão escolhido automaticamente pelo sistema.</string>
    <string name="dv7_libdovi_mode_4_title">Modo 4 — Perfil 8.4 (Estático)</string>
    <string name="dv7_libdovi_mode_4_sub">Converte os dados de imagem para o perfil estático 8.4.</string>
=======
    <string name="dv7_libdovi_mode_caption" translatable="false">Modo de conversão (Automático). Não altere a menos que seja instruído. Escolha uma opção para forçá-la ou "Nenhum" para o padrão. Funciona apenas se a conversão para DV 8.1 estiver ativa.</string>
    <string name="dv7_libdovi_mode_row_title" translatable="false">Modo de conversão</string>
    <string name="dv7_libdovi_mode_none_title" translatable="false">Nenhum</string>
    <string name="dv7_libdovi_mode_none_sub" translatable="false">Sem alterações forçadas. Usa o modo de conversão escolhido automaticamente pelo sistema.</string>
    <string name="dv7_libdovi_mode_4_title" translatable="false">Modo 4 — Perfil 8.4 (Estático)</string>
    <string name="dv7_libdovi_mode_4_sub" translatable="false">Converte os dados de imagem para o perfil estático 8.4.</string>
>>>>>>> dev
```

**Phân tích:**

- `dev` thêm `translatable="false"` cho các chuỗi DV7. Đây là metadata dịch thuật có chủ đích của upstream.
- Nội dung tiếng Bồ Đào Nha không đổi về nghĩa.
- `tet2` chỉ escape dấu nháy.

**Quyết định:** lấy bản của `dev` (giữ `translatable="false"` và nội dung dịch mới). Ghi nhận trong báo cáo sau merge.

---

### Các thay đổi custom đã auto-merge (phải giữ)

- `app_name` = `Nuvio TV` (không lấy `Nuvio` của upstream).
- Đã xóa các key P2P khỏi pt-BR:
  - `essential_p2p_streams`
  - `essential_p2p_streams_subtitle`
  - `p2p_consent_title`
  - `p2p_consent_body`
  - `p2p_consent_enable`
  - `p2p_consent_cancel`
  - `settings_p2p_title`
- Các file mới của upstream vẫn được thêm:
  - `values-pt-rBR/strings_party.xml`
  - `values-pt-rBR/strings_servers.xml`
- Các chuỗi mới của upstream (autosync, release decoder, Trakt, v.v.) vẫn được thêm.

### Kiểm tra sau khi sửa

- `app_name` vẫn là `Nuvio TV`.
- Không có key P2P nào quay lại pt-BR.
- Không có duplicate key.
- XML parse thành công.
- `strings_party.xml` và `strings_servers.xml` tồn tại.

---

## Kết luận

- 3 xung đột đều có hướng xử lý rõ ràng, không cần hỏi thêm.
- Không có xung đột nào liên quan đến logic TorrServer, audio, Live TV, stream search hay UI custom.
- Sau khi sửa sẽ chạy compile + test trọng tâm trước khi commit merge.
