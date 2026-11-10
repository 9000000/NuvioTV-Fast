# Báo cáo Kết quả Đồng bộ Nhánh `dev` → `tet2` (2026-11-10)

> **Trạng thái**: Đã hoàn tất đồng bộ, giải quyết xung đột, kiểm chứng biên dịch và Unit Tests (`BUILD SUCCESSFUL`).

---

## 1. Tóm tắt Kết quả

| Hạng mục | Kết quả |
|---|---|
| **Nhánh đích** | `tet2` |
| **Nhánh sao lưu (Backup)** | `backup-tet2-before-merge-20261110` (`098034c5a`) |
| **Mốc cơ sở (`merge-base`)** | `fe5c314bd` (2026-11-06) |
| **Mốc `dev` đã đồng bộ** | `65538103f` (2026-11-09) |
| **Số lượng commit đồng bộ** | **50 commit** (33 commit thường + 17 merge commit) |
| **Số file thay đổi từ `dev`** | **45 file** |
| **Xung đột văn bản đã giải quyết** | **3 file**: [`app/build.gradle.kts`](file:///f:/MyGithub/NuvioTV-Fast/app/build.gradle.kts), [`ParallelRangeDataSource.kt`](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/ParallelRangeDataSource.kt), [`values-nl/strings.xml`](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/res/values-nl/strings.xml) |
| **Nâng cấp tương thích ngữ nghĩa** | **1 file**: [`LeanbackModernRow.kt`](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt) (`colorCacheKey = imageUrl ?: item.key`) |
| **Kiểm tra XML & Conflict Markers** | **0 conflict marker**, **0 duplicate string key** trên toàn bộ `res/values*/strings*.xml` |
| **Biên dịch (`compileFullDebugKotlin`)** | **`BUILD SUCCESSFUL`** |
| **Unit Tests (`testFullDebugUnitTest`)** | **`BUILD SUCCESSFUL`** (`TraktEpisodeMappingServiceTest`, `ContinueWatchingNextUpReconciliationTest`, `CloudStreamCompatStubTest`) |
| **Trạng thái Push** | Chưa push (để người dùng kiểm tra trước) |

---

## 2. Chi tiết Xử lý Xung đột & Tương thích

1. **[`app/build.gradle.kts`](file:///f:/MyGithub/NuvioTV-Fast/app/build.gradle.kts#L144-L153)**:
   - Giữ nguyên cấu hình phiên bản của `tet2`: `applicationId = "com.nuvio.tv.fast"`, `versionCode = 1176`, `versionName = "1.3.4"`.
2. **[`ParallelRangeDataSource.kt`](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/player/ParallelRangeDataSource.kt#L1368-L1380)**:
   - Áp dụng bản vá `wasBootstrap` và điều kiện dừng `if (!wasBootstrap && position / chunkSize == currentChunkIndex) return C.RESULT_END_OF_INPUT` từ commit `18c256fb6` của `dev` để ngăn chặn đệ quy vô hạn khi đọc phải short chunk.
   - Bảo toàn 100% các tùy chỉnh riêng của `tet2` (`sessionChunkCap` theo `MemoryBudget.isLowRamTier`, lọc query `nuvio_type=subtitle`, và bắt `OutOfMemoryError` gọi `resources.drainIdleBuffers`).
3. **[`values-nl/strings.xml`](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/res/values-nl/strings.xml#L1780-L1805)**:
   - Tích hợp toàn bộ bản dịch tiếng Hà Lan mới sau bản 1.1, loại bỏ 2 key P2P cũ (`p2p_download_title`, `p2p_download_subtitle`) theo chuẩn của `tet2`, và bảo toàn `<string name="app_name">Nuvio TV</string>`.
4. **[`LeanbackModernRow.kt`](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt#L818-L915)**:
   - Cập nhật `LeanbackCardViewHolder.bind` sử dụng `val colorCacheKey = imageUrl ?: item.key` và làm mới ảnh khi `currentItem?.key != item.key || prevImageUrl != imageUrl`, phối hợp hoàn hảo với `continueWatchingItemKey` mới (`"cw_${item.contentId()}"`) từ commit `4eb7dda70`.

---

## 3. Bảo toàn Tùy chỉnh của `tet2`

- **Android Leanback Native & Search D-pad**: Toàn bộ 27 file tối ưu hóa Leanback Native (`LeanbackColorEngine`, `LeanbackBaseViewHolder`, `LeanbackPosterViewHolder`, `LeanbackEpisodeViewHolder`, `LeanbackAvatarViewHolder`, `LeanbackUniversalAdapter`, `LeanbackNativeRow`, `LeanbackNativeGrid`, `InteropHorizontalGridView`, `InteropVerticalGridView`, `SearchScreen`, `LeanbackModernRow`, `CatalogRowSection`, các section Detail/Browse) được bảo toàn nguyên vẹn 100%.
- **Home & Navigation**: `NativeSidebarView`, khóa `LocalOpenSidebar` cho root routes, `MemoryBudget`, cùng đồng bộ sớm `StartupSyncService.requestNavigationSync()` khi rời Player hoặc về Home hoạt động song hành.
- **Player & Audio**: `forcePcmForBluetooth`, `forceOpticalPassthrough` (với `DefaultAudioSink.Builder()` ghim capabilities AC-3), TorrServer, Live TV buffer, DRM, Subtitle style và AutoSync đều nguyên vẹn.

