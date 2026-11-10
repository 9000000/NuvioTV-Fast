# Báo cáo Phân tích Xung đột & Tương tác Ngữ nghĩa: `dev` → `tet2` (2026-11-10)

> Tài liệu này phân tích chi tiết **3 xung đột văn bản trực tiếp (Git Conflicts)** và **1 điểm tương tác ngữ nghĩa (Semantic Interaction)** khi đồng bộ 50 commit từ `upstream/dev` (`fe5c314bd..65538103f`) sang nhánh `tet2`.

---

## Tổng quan Xung đột

| # | File | Số vùng xung đột | Mức độ | Quyết định xử lý |
|---|---|---:|---|---|
| 1 | [`app/build.gradle.kts`](file:///f:/MyGithub/NuvioTV-Fast/app/build.gradle.kts#L135-L140) | 1 | Thấp | **Giữ `tet2` (`ours`)**: `versionCode = 1176`, `versionName = "1.3.4"` |
| 2 | [`ParallelRangeDataSource.kt`](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/ParallelRangeDataSource.kt#L918-L930) | 1 | Trung bình | **Kết hợp (Dùng bản vá của `dev` + Giữ custom `tet2`)**: Áp dụng `wasBootstrap` short-chunk guard từ `18c256fb6`, bảo toàn 100% custom RAM/OOM/subtitle của `tet2` |
| 3 | [`values-nl/strings.xml`](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/res/values-nl/strings.xml#L215-L245) | 1 | Thấp | **Nhận bản dịch mới của `dev`, giữ xóa 2 key P2P Nhóm A** (`p2p_download_title`, `p2p_download_subtitle`) |
| 4 | [`LeanbackModernRow.kt`](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt#L818-L915) *(Tương tác ngữ nghĩa)* | 0 (Auto-merge) | Trung bình | **Nâng cấp phối hợp**: Dùng `imageCacheKey = item.imageUrl ?: item.key` để tương thích hoàn hảo với `continueWatchingItemKey` mới (`"cw_${item.contentId()}"`) từ commit `4eb7dda70` |

---

## 1. Xung đột #1: `app/build.gradle.kts`

### 1.1 Nội dung xung đột dự báo (`git merge-tree`)

```kotlin
<<<<<<< tet2
        versionCode = 1176
        versionName = "1.3.4"
=======
        versionCode = 1077
        versionName = "1.1.0"
>>>>>>> upstream/dev
```

### 1.2 Phân tích & Quyết định
- Nhánh `tet2` (`NuvioTV-Fast`) sử dụng chuỗi phiên bản độc lập `1.3.x` (`versionCode = 1176`, `versionName = "1.3.4"`).
- Hai commit `3eeb8b3d0` và `12d96bced` trên `upstream/dev` chỉ tăng `versionCode` của nhánh gốc từ `1075` lên `1077` (`1.1.0`).
- **Quyết định**: Giữ nguyên `versionCode = 1176` và `versionName = "1.3.4"` của `tet2`.

---

## 2. Xung đột #2: `app/src/main/java/com/nuvio/tv/ui/screens/player/ParallelRangeDataSource.kt`

### 2.1 Nội dung xung đột dự báo (tại hàm `read(buffer: ByteArray, offset: Int, length: Int)`)

```kotlin
        val chunk = currentChunk ?: return C.RESULT_END_OF_INPUT
        val available = chunk.size - currentChunkReadOffset
        if (available <= 0) {
<<<<<<< tet2
            // Current chunk exhausted, move to next
            if (chunk === bootstrapChunk) {
=======
            val wasBootstrap = chunk === bootstrapChunk
            if (wasBootstrap) {
>>>>>>> upstream/dev
                bootstrapChunk = null
                bootstrapStartPosition = C.TIME_UNSET
            }
            currentChunk = null
            if (!wasBootstrap && position / chunkSize == currentChunkIndex) return C.RESULT_END_OF_INPUT
            return read(buffer, offset, length)
        }
```

### 2.2 Phân tích Công dụng (Vì sao code mới của `dev` tốt hơn)
- Commit `18c256fb6` (`fix(player): end parallel reads at a short chunk instead of recursing`) trên `upstream/dev` giải quyết một lỗi nghiêm trọng:
  - Khi một chunk thường (`!wasBootstrap`) trả về số byte ít hơn `chunkSize` (short chunk, ví dụ ở cuối luồng stream hoặc khi server trả thiếu byte), `available = chunk.size - currentChunkReadOffset` sẽ `<= 0`, nhưng phép chia nguyên `position / chunkSize` vẫn ra đúng `currentChunkIndex` vừa đọc xong.
  - Ở code cũ, hàm gán `currentChunk = null` rồi gọi đệ quy `return read(buffer, offset, length)`, nạp lại chính chunk ngắn đó và rơi vào đệ quy vô tận gây `StackOverflowError`.
  - Code mới thêm biến `val wasBootstrap = chunk === bootstrapChunk` và kiểm tra `if (!wasBootstrap && position / chunkSize == currentChunkIndex) return C.RESULT_END_OF_INPUT` để kết thúc luồng đọc an toàn.
- Lưu ý: Tại hàm `read(buffer: ByteBuffer, length: Int)` (dòng 1677), Git đã tự động merge thành công đoạn sửa này; chỉ tại dòng 921 là bị vướng dòng comment `// Current chunk exhausted, move to next` của `tet2`.
- **Quyết định**: Áp dụng code mới của `upstream/dev` tại dòng 920–929 (bỏ dòng comment cũ gây xung đột), đồng thời giữ nguyên 100% các tùy chỉnh riêng của `tet2` trong file này (`sessionChunkCap` theo `MemoryBudget.isLowRamTier`, lọc query `nuvio_type=subtitle`, và bắt `OutOfMemoryError` gọi `resources.drainIdleBuffers`).

---

## 3. Xung đột #3: `app/src/main/res/values-nl/strings.xml`

### 3.1 Nội dung xung đột dự báo

```xml
    <string name="settings_p2p_hide_stats_title">Torrentstatistieken verbergen</string>
    <string name="settings_p2p_hide_stats_subtitle">Verberg buffer, seeds, peers en downloadsnelheid tijdens het laden en afspelen</string>
<<<<<<< tet2
=======
    <string name="settings_p2p_profile_title">Torrentprofiel</string>
    <string name="settings_p2p_profile_soft">Zuinig</string>
    <string name="settings_p2p_profile_balanced">Gebalanceerd</string>
    <string name="settings_p2p_profile_fast">Snel</string>
    <string name="settings_p2p_profile_soft_description">Gebruikt minder peerverbindingen om batterij en netwerk te ontlasten</string>
    <string name="settings_p2p_profile_balanced_description">Aanbevolen balans tussen opstartsnelheid en stabiele verbindingen</string>
    <string name="settings_p2p_profile_fast_description">Gebruikt meer peerverbindingen voor lastige torrents of torrents met een hoge bitrate</string>
    <string name="settings_p2p_cache_size_title">Grootte torrentcache</string>
    <string name="settings_p2p_cache_size_description">Gedownloade torrentgegevens die op dit apparaat worden bewaard om sneller opnieuw af te spelen</string>
    <string name="settings_p2p_cache_none">Geen blijvende cache</string>
    <string name="settings_p2p_cache_2_gb">2 GB</string>
    <string name="settings_p2p_cache_5_gb">5 GB</string>
    <string name="settings_p2p_cache_10_gb">10 GB</string>
    <string name="settings_p2p_clear_cache_title">Torrentcache wissen</string>
    <string name="settings_p2p_clear_cache_usage">Momenteel %1$s in gebruik</string>
    <string name="settings_p2p_clear_cache_usage_pending">Het gebruik wordt gemeten nadat P2P is gestart; selecteer om gecachete torrentgegevens te wissen</string>
    <string name="settings_p2p_clear_cache_clearing">Inactieve torrentgegevens wissen…</string>
    <string name="settings_p2p_clear_cache_playback_active">Stop P2P-afspelen voordat je de cache wist</string>
    <string name="settings_p2p_clear_cache_done">%1$s aan inactieve torrentgegevens gewist</string>
    <string name="settings_p2p_clear_cache_failed">Kon de torrentcache niet wissen</string>
    <string name="p2p_download_title">P2P-engine downloaden</string>
    <string name="p2p_download_subtitle">Dit is een eenmalige download die nodig is voor P2P-streaming.</string>
>>>>>>> upstream/dev
```

### 3.2 Phân tích & Quyết định
- Theo quy ước từ các đợt đồng bộ trước trên `tet2`:
  - Giữ lại 20 key `settings_p2p_profile_*` / `settings_p2p_cache_*` / `settings_p2p_clear_cache_*` mới.
  - Không đưa lại các key thuộc nhóm 10 key P2P cũ đã bị `tet2` chủ động xóa khỏi các file ngôn ngữ (`p2p_download_title`, `p2p_download_subtitle`, `p2p_consent_*`, `settings_p2p_title`, `settings_p2p_subtitle`, `essential_p2p_streams*`).
  - Bảo toàn `<string name="app_name">Nuvio TV</string>`.

---

## 4. Tương tác Ngữ nghĩa #4: `ModernHomeModels.kt` (`dev`) ↔ `LeanbackModernRow.kt` (`tet2`)

### 4.1 Thay đổi từ `upstream/dev` (Commit `4eb7dda70`)
Trong [`ModernHomeModels.kt`](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeModels.kt#L588-L595), `upstream/dev` thay đổi hàm tạo key cho thẻ Continue Watching:
```kotlin
// Trước đây trên tet2:
internal fun continueWatchingItemKey(item: ContinueWatchingItem): String {
    return item.shuffleFocusKey ?: when (item) {
        is ContinueWatchingItem.InProgress ->
            "cw_inprogress_${item.progress.contentId}_${item.progress.season ?: -1}_${item.progress.episode ?: -1}"
        is ContinueWatchingItem.NextUp ->
            "cw_nextup_${item.info.contentId}_${item.info.season}_${item.info.episode}"
    }
}

// Mới trên upstream/dev (4eb7dda70):
internal fun continueWatchingItemKey(item: ContinueWatchingItem): String {
    return item.shuffleFocusKey ?: "cw_${item.contentId()}"
}
```

### 4.2 Tác động lên `LeanbackModernRow.kt` (Tùy chỉnh độc quyền của `tet2`)
- **Điểm lợi lớn**: Khi người dùng xem xong một tập phim của Series và quay về màn hình chính (Home), thẻ Continue Watching chuyển từ tập vừa xem sang tập kế tiếp (`NextUp`). Nhờ `item.key` giữ nguyên là `"cw_${item.contentId()}"`, `DiffUtil` trong `LeanbackRowAdapter` nhận diện đây là cùng một thẻ (`areItemsTheSame == true`) và cập nhật nội dung tại chỗ (`notifyItemChanged`), **giúp giữ nguyên focus trên đúng thẻ Series đó thay vì bị nhảy focus sang thẻ bên cạnh**.
- **Điểm cần điều chỉnh nhẹ trong `LeanbackModernRow.kt` ([dòng 818–908](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt#L818-L908))**:
  - Hiện tại `LeanbackCardViewHolder.bind` đang dùng `item.key` để kiểm tra xem ảnh có thay đổi không (`if (currentItem?.key != item.key) posterImage.setImageDrawable(null)`) và dùng `item.key` làm khóa cache màu (`LeanbackColorEngine.getCachedColor(item.key)`).
  - Khi `item.key` cố định theo series (`"cw_tt123"`), nếu tập mới có ảnh thumbnail mới (`item.imageUrl` thay đổi), việc chỉ so sánh `item.key` sẽ khiến cache màu trả về màu cũ của tập trước.
  - **Giải pháp phối hợp**: Trong `LeanbackCardViewHolder.bind` (`LeanbackModernRow.kt`), sử dụng:
    ```kotlin
    if (currentItem?.key != item.key || currentItem?.imageUrl != item.imageUrl) {
        posterImage.setImageDrawable(null)
    }
    val colorCacheKey = item.imageUrl ?: item.key
    ```
    Nhờ đó vừa tận dụng trọn vẹn ưu điểm giữ focus của commit `4eb7dda70` trên `dev`, vừa đảm bảo `LeanbackModernRow` của `tet2` luôn cập nhật đúng thumbnail và màu hào quang Neon theo từng tập phim!

