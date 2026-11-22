# Nhật Ký Chi Tiết Các Lỗi Leanback Đã Sửa (Rollback Reference)

> **Mục đích**: Tài liệu ghi nhận chi tiết từng lỗi/tối ưu đã sửa trên nhánh `tet2` theo kế hoạch [PLAN-leanback-ux-ui-audit.md](file:///f:/MyGithub/NuvioTV-Fast/PLAN-leanback-ux-ui-audit.md).
> **Cách dùng khi cần khôi phục (Rollback)**: Nếu phát hiện lỗi hoặc hành vi không mong muốn ở bất kỳ mục nào, bạn chỉ cần nhắn ví dụ: **"Khôi phục `BUG-03` về như cũ"** hoặc **"Khôi phục `BUG-17` về như cũ"** — mình sẽ hoàn tác chính xác từng dòng của mục đó mà không ảnh hưởng các mục khác.

---

## Mục Lục Nhanh Theo Mã Lỗi

| Mã mục | Mức độ | Tóm tắt ngắn | File bị tác động |
| :--- | :---: | :--- | :--- |
| **[BUG-01](#bug-01)** | 🔴 P0 | Chống kẹt `isHoldScrolling` & nạp lại poster đen trống sau cuộn nhanh | `ModernHomeRowsList.kt`, `LeanbackModernRowAdapter.kt`, `LeanbackUniversalAdapter.kt`, `LeanbackBaseViewHolder.kt` |
| **[BUG-02](#bug-02)** | 🔴 P0 | Sửa công thức `isLastRow` khóa `DPAD_DOWN` ở hàng áp chót Grid | `InteropVerticalGridView.kt` |
| **[BUG-03](#bug-03)** | 🔴 P0 | Chống `ACTION_UP` giật ngược focus khi thoát Grid & chuẩn hóa 4 biên | `InteropVerticalGridView.kt` |
| **[BUG-04](#bug-04)** | 🟠 P1 | Cập nhật tức thì badge "Đã xem" (Watched) khi đổi trạng thái | `CatalogRowSection.kt`, `CatalogSeeAllScreen.kt` |
| **[BUG-05](#bug-05)** | 🟠 P1 | Nối `Watched`, `Blur Unwatched CW`, `coverEmoji` xuống Modern Home | `ModernHomeRowsList.kt`, `ModernHomeRows.kt`, `LeanbackModernRow.kt`, `LeanbackModernRowAdapter.kt` |
| **[BUG-06](#bug-06)** | 🟠 P1 | Thêm `requestInitialFocus` chống Grid tự cướp focus khỏi Filter/Tab | `LeanbackNativeGrid.kt`, `SearchDiscoverSection.kt`, `FolderDetailScreen.kt` |
| **[BUG-07](#bug-07)** | 🟠 P1 | Tính số cột động `computeAdaptiveGridColumns` chống tràn màn hình | `LeanbackNativeGrid.kt`, `CatalogSeeAllScreen.kt`, `SearchDiscoverSection.kt`, `FolderDetailScreen.kt` |
| **[BUG-08](#bug-08)** | 🟠 P1 | Tắt software blur glow của Modern Floating Ring khi `animationsEnabled = false` | `ModernRowFloatingFocusRingView.kt`, `LeanbackModernRow.kt`, `ModernHomeRowsList.kt` |
| **[BUG-09](#bug-09)** | 🟠 P1 | Gỡ gọi trùng phím `UP`/`DOWN`/`BACK` & thêm phím `KEYCODE_MENU` | `LeanbackBaseViewHolder.kt`, `LeanbackModernRowAdapter.kt` |
| **[BUG-10](#bug-10)** | 🟠 P1 | Bảo vệ `updateConfig()` khỏi crash `IllegalStateException` khi đang layout | `LeanbackUniversalAdapter.kt`, `LeanbackModernRowAdapter.kt` |
| **[BUG-11](#bug-11)** | 🟡 P2 | Bo góc lớp phủ gradient đen dưới cùng trên thẻ Tập phim (Episode) | `LeanbackEpisodeViewHolder.kt` |
| **[BUG-12](#bug-12)** | 🟡 P2 | Chống giật ngược vị trí cuộn ngang về `initialFocusPosition` khi recompose | `LeanbackNativeRow.kt`, `LeanbackModernRow.kt` |
| **[BUG-13](#bug-13)** | 🟡 P2 | Chuyển `leftMargin`/`rightMargin` → `marginStart`/`marginEnd` hỗ trợ RTL | `LeanbackPosterViewHolder.kt`, `LeanbackEpisodeViewHolder.kt`, `LeanbackModernRowAdapter.kt` |
| **[BUG-14](#bug-14)** | 🟡 P2 | Co giãn vùng nhãn theo `fontScale` & bỏ khoảng trống thừa 44dp ở Episode | `LeanbackNativeRow.kt`, `LeanbackModernRow.kt` |
| **[BUG-15](#bug-15)** | 🟡 P2 | Thẻ `See All` có icon mũi tên `→` + nhãn rõ ràng (kể cả khi ẩn labels) | `LeanbackCardItem.kt`, `LeanbackPosterViewHolder.kt`, `CatalogRowSection.kt` |
| **[BUG-16](#bug-16)** | 🟡 P2 | Thêm debounce `250ms` + kiểm tra `itemCount` cho `onLoadMore` Universal | `LeanbackUniversalAdapter.kt`, `InteropHorizontalGridView.kt`, `InteropVerticalGridView.kt` |
| **[BUG-17](#bug-17)** | 🟡 P2 | Hỗ trợ an toàn `Hardware Bitmaps` (không ép `.allowHardware(false)` mặc định) | `LeanbackArtworkLoader.kt`, `LeanbackColorEngine.kt` |
| **[UX-THEME](#ux-theme)** | ✨ UX | Đồng bộ `NuvioTheme` / `PosterBorderStyle`, Marquee, Fallback Avatar/Poster, TalkBack | `LeanbackThemeStyle.kt` (mới), `LeanbackBaseViewHolder.kt`, `LeanbackPosterViewHolder.kt`, `LeanbackAvatarViewHolder.kt`, `LeanbackEpisodeViewHolder.kt`, `ModernRowFloatingFocusRingView.kt`, `LeanbackNativeRow.kt`, `LeanbackNativeGrid.kt`, `LeanbackModernRow.kt` |
| **[CLEAN-DEAD](#clean-dead)** | 🧹 Dọn | Gộp `LeanbackBlurFilterCache`, bỏ 2 map trùng ở ColorEngine, xóa dead params | `LeanbackBlurFilterCache.kt` (mới), `PlayStoreGlowView.kt`, `LeanbackColorEngine.kt`, `ModernRowFloatingFocusRingView.kt`, `ModernInteropHorizontalGridView.kt`, `InteropHorizontalGridView.kt`, `LeanbackUniversalAdapter.kt`, `LeanbackModernRowAdapter.kt` |

---

## Chi Tiết Từng Mục (Code Cũ vs Code Mới)

### <a id="bug-01"></a>1. `BUG-01` — Chống kẹt `isHoldScrolling` & phục hồi ảnh đen trống sau cuộn nhanh
- **Tệp 1**: [ModernHomeRowsList.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeRowsList.kt) (`refreshAllVisibleRowImages`, `finishOverlayRingToCurrentGrid`, `endVerticalHoldScroll`)
  - **Code CŨ**:
    ```kotlin
    // finishOverlayRingToCurrentGrid()
    if (!overlayActive) return
    ...
    isVerticalGlideRef.set(false)
    latestOnFastScrollingChanged.value(false)
    overlayHost?.deactivateGlideFocusAndHideRing()
    // (KHÔNG reset LeanbackArtworkLoader.isHoldScrolling = false)

    // endVerticalHoldScroll()
    val landingGrid = rowGrids[landingRow.key]
    triggerLandingRowImageRefresh(landingGrid)
    // (Chỉ refresh 1 hàng landingGrid, bỏ qua các hàng visible khác)
    ```
  - **Code MỚI**:
    ```kotlin
    fun refreshAllVisibleRowImages(excludeGrid: androidx.leanback.widget.HorizontalGridView? = null) {
        rowGrids.values.forEach { grid ->
            if (grid !== excludeGrid && grid.isAttachedToWindow) {
                triggerLandingRowImageRefresh(grid)
            }
        }
    }

    // Trong finishOverlayRingToCurrentGrid():
    val wasHoldScrolling = LeanbackArtworkLoader.isHoldScrolling
    if (wasHoldScrolling) LeanbackArtworkLoader.isHoldScrolling = false
    if (!overlayActive) {
        if (wasHoldScrolling) refreshAllVisibleRowImages()
        return
    }
    ...
    LeanbackArtworkLoader.isHoldScrolling = false
    refreshAllVisibleRowImages()

    // Trong endVerticalHoldScroll():
    val landingGrid = rowGrids[landingRow.key]
    triggerLandingRowImageRefresh(landingGrid)
    refreshAllVisibleRowImages(excludeGrid = landingGrid)
    ```
- **Tệp 2**: [LeanbackModernRowAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRowAdapter.kt) (`onScrollSettled`), [LeanbackUniversalAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/adapter/LeanbackUniversalAdapter.kt) (`onScrollSettled`, `rebindMissingImages`), [LeanbackBaseViewHolder.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/holder/LeanbackBaseViewHolder.kt) (`needsImageRebind`)
  - **Code CŨ**: `onScrollSettled()` chỉ gọi `updateFocusVisual` & prefetch, không kiểm tra ViewHolder visible bị thiếu ảnh (`drawable == null && imageDisposable == null`).
  - **Code MỚI**: Duyệt child đang gắn của `rv`, nếu `vh.needsImageRebind()` (Universal) hoặc `vh.posterImage.drawable == null && vh.imageDisposable == null` (Modern) thì gọi lại `onBindViewHolder(vh, pos)`.
- **Khôi phục**: Xóa `refreshAllVisibleRowImages`, bỏ reset `isHoldScrolling` trong `finishOverlayRingToCurrentGrid` và bỏ `rebindMissingImages` trong `onScrollSettled()`.

---

### <a id="bug-02"></a>2. `BUG-02` — Sửa công thức `isLastRow` trong `InteropVerticalGridView`
- **Tệp**: [InteropVerticalGridView.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/container/InteropVerticalGridView.kt) (`focusSearch`, `dispatchKeyEvent`, `isVerticalGridLastRow`)
- **Code CŨ**:
  ```kotlin
  val totalItems = universalAdapter?.itemCount ?: 0
  val isLastRow = actualPos >= 0 && actualPos >= (totalItems - cols)
  ```
- **Code MỚI**:
  ```kotlin
  val totalItems = universalAdapter?.itemCount ?: 0
  val isLastRow = isVerticalGridLastRow(actualPos, totalItems, cols)

  internal fun isVerticalGridLastRow(actualPos: Int, totalItems: Int, cols: Int): Boolean {
      val safeCols = cols.coerceAtLeast(1)
      return totalItems > 0 && actualPos >= 0 && (actualPos / safeCols) >= (totalItems - 1) / safeCols
  }
  ```
- **Khôi phục**: Đổi lại `val isLastRow = actualPos >= 0 && actualPos >= (totalItems - cols)` ở cả `focusSearch` và `dispatchKeyEvent`.

---

### <a id="bug-03"></a>3. `BUG-03` — Chống `ACTION_UP` giật ngược focus & chuẩn hóa phím biên trong `InteropVerticalGridView`
- **Tệp**: [InteropVerticalGridView.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/container/InteropVerticalGridView.kt) (`isNavigatingOutOfGrid`, `moveToPosition`, `onFocusChanged`, `dispatchKeyEvent`, `handleBoundaryNavigation`)
- **Code CŨ**:
  ```kotlin
  // ACTION_UP luôn gọi requestFocus() không kiểm tra hasFocus() / isNavigatingOutOfGrid
  // ACTION_DOWN ở 4 biên luôn return true ngay cả khi callback == null
  if (isFirstRow) {
      if (event.repeatCount == 0 && onNavigateUpOnFirstRow?.invoke() == true) {
          return true
      }
      return true
  }
  ```
- **Code MỚI**:
  ```kotlin
  private var isNavigatingOutOfGrid: Boolean = false
  // ACTION_UP:
  if (!hasFocus() || isNavigatingOutOfGrid) {
      isHoldingVerticalKey = false
      pendingTargetPosition = RecyclerView.NO_POSITION
      isNavigatingOutOfGrid = false
      return false
  }
  // ACTION_DOWN ở 4 biên dùng:
  private fun handleBoundaryNavigation(repeatCount: Int, callback: (() -> Boolean)?): Boolean {
      if (repeatCount > 0) return true
      if (callback == null) return false
      if (callback.invoke()) {
          isNavigatingOutOfGrid = true
      }
      return true
  }
  ```
- **Khôi phục**: Xóa `isNavigatingOutOfGrid` + `handleBoundaryNavigation`, khôi phục nhánh `ACTION_UP` và 4 biên `ACTION_DOWN` cũ.

---

### <a id="bug-04"></a>4. `BUG-04` — Reactivity trạng thái Watched ở `CatalogRowSection` và `CatalogSeeAllScreen`
- **Tệp 1**: [CatalogRowSection.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/components/CatalogRowSection.kt)
  - **Code CŨ**:
    ```kotlin
    val leanbackItems = remember(catalogRow.items, showSeeAll, isLandscape, strSeeAll, showImdbRatings) {
        ... isWatched = latestIsItemWatched(item)
    }
    ```
  - **Code MỚI**:
    ```kotlin
    val watchedSignature = catalogRow.items.map { latestIsItemWatched(it) }
    val leanbackItems = remember(catalogRow.items, showSeeAll, isLandscape, strSeeAll, showImdbRatings, watchedSignature) {
        ... isWatched = watchedSignature[index]
    }
    ```
- **Tệp 2**: [CatalogSeeAllScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/CatalogSeeAllScreen.kt)
  - **Code CŨ**: `remember(catalogRow.items, isSearchMode, isLandscape, uiState.movieWatchedStatus)` (thiếu `searchWatchedSeriesIds?.value`, `searchWatchedMovieIds?.value`).
  - **Code MỚI**: Thêm `watchedSeriesIds` và `watchedMovieIds` vào keys của `remember(...)`.

---

### <a id="bug-05"></a>5. `BUG-05` — Khôi phục `Watched`, `Blur Unwatched CW`, `coverEmoji` trên Modern Home
- **Tệp**: [ModernHomeRowsList.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeRowsList.kt), [ModernHomeRows.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeRows.kt), [LeanbackModernRow.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt), [LeanbackModernRowAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRowAdapter.kt)
- **Code CŨ**: `ModernHomeRowsList` không truyền `isCatalogItemWatched`, `blurUnwatchedEpisodes` xuống `ModernRowSection`; `LeanbackCardViewHolder` không có `watchedBadgeView`, không có `emojiCoverView`, không truyền `blurred` vào `LeanbackArtworkLoader`.
- **Code MỚI**: Nối `isCatalogItemWatched` và `blurUnwatchedEpisodes` xuyên suốt xuống `LeanbackRowAdapter` & `LeanbackCardViewHolder`; hiển thị dấu tích `watchedBadgeView` (góc top-start), `emojiCoverView` khi `CollectionFolder` có `coverEmoji` mà không có ảnh bìa, và `blurred = true` cho `ContinueWatchingItem.NextUp` khi `blurUnwatchedEpisodes == true`.

---

### <a id="bug-06"></a>6. `BUG-06` — Kiểm soát tự động cướp focus `requestInitialFocus` ở `LeanbackNativeGrid`
- **Tệp**: [LeanbackNativeGrid.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/LeanbackNativeGrid.kt), [SearchDiscoverSection.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/search/SearchDiscoverSection.kt), [FolderDetailScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/collection/FolderDetailScreen.kt)
- **Code CŨ**: `LaunchedEffect(gridKey, items.isNotEmpty(), gridViewRef)` luôn gọi `awaitAndExecuteGridFocus` mỗi khi đổi `gridKey`.
- **Code MỚI**: Thêm tham số `requestInitialFocus: Boolean = true`; `SearchDiscoverSection` truyền `focusResults || shouldRestoreFocusedItem`; `FolderDetailScreen` truyền `tabFocusState.hasSavedFocus || uiState.tabs.size <= 1`.

---

### <a id="bug-07"></a>7. `BUG-07` — Tính số cột lưới động `computeAdaptiveGridColumns` chống tràn màn hình
- **Tệp**: [LeanbackNativeGrid.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/LeanbackNativeGrid.kt), [CatalogSeeAllScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/CatalogSeeAllScreen.kt), [SearchDiscoverSection.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/search/SearchDiscoverSection.kt), [FolderDetailScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/collection/FolderDetailScreen.kt)
- **Code CŨ**: `val numCols = if (isLandscape) 4 else 6` cố định cứng trong `Box(Modifier.fillMaxSize())`.
- **Code MỚI**: Dùng `BoxWithConstraints` + `computeAdaptiveGridColumns(availableWidth, cardWidth, horizontalSpacing)` để tự co giảm số cột khi Poster Size lớn.

---

### <a id="bug-08"></a>8. `BUG-08` — Tắt Software Blur Glow trên Modern Home khi `animationsEnabled == false`
- **Tệp**: [ModernRowFloatingFocusRingView.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/ModernRowFloatingFocusRingView.kt), [LeanbackModernRow.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt), [ModernHomeRowsList.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeRowsList.kt)
- **Code CŨ**: `moveToTarget` và `showFixedOverlayAt` luôn gán cứng `isGlowEnabled = true`, ép bật `LAYER_TYPE_SOFTWARE` kể cả khi tắt animation.
- **Code MỚI**: Gán `focusRing.isGlowEnabled = animationsEnabled` từ `LeanbackModernRow` & `VerticalRingOverlayHost`; khi `isGlowEnabled == false` thì chuyển về `LAYER_TYPE_NONE` và chỉ vẽ viền sắc nét HWUI.

---

### <a id="bug-09"></a>9. `BUG-09` — Xóa gọi trùng callback phím & hỗ trợ phím `KEYCODE_MENU`
- **Tệp**: [LeanbackBaseViewHolder.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/holder/LeanbackBaseViewHolder.kt), [LeanbackModernRowAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRowAdapter.kt)
- **Code CŨ**: `root.setOnKeyListener` bắt trùng `KEYCODE_BACK` (Universal) và `KEYCODE_DPAD_UP/DOWN/BACK` (Modern) vốn đã xử lý trong `dispatchKeyEvent` của container.
- **Code MỚI**: Gỡ các phím trùng lặp, thêm `KeyEvent.KEYCODE_MENU` (`ACTION_DOWN && repeatCount == 0`) gọi `adapter.onItemLongClick(it)`.

---

### <a id="bug-10"></a>10. `BUG-10` — Guard `isComputingLayout || isInLayout` cho `updateConfig()`
- **Tệp**: [LeanbackUniversalAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/adapter/LeanbackUniversalAdapter.kt), [LeanbackModernRowAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRowAdapter.kt)
- **Code CŨ**: Gọi thẳng `notifyItemRangeChanged(0, items.size)` trong `updateConfig()`.
- **Code MỚI**: Nếu `rv.isComputingLayout || rv.isInLayout` thì đẩy qua `rv.post { if (attachedRecyclerView === rv) notifyItemRangeChanged(0, items.size) }`.

---

### <a id="bug-11"></a>11. `BUG-11` — Bo góc lớp phủ Gradient trên thẻ Tập phim (Episode)
- **Tệp**: [LeanbackEpisodeViewHolder.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/holder/LeanbackEpisodeViewHolder.kt)
- **Code CŨ**: `GradientDrawable(TOP_BOTTOM, ...)` không gán `cornerRadius` → 2 góc dưới màu đen lòi vuông ra ngoài bo góc thẻ.
- **Code MỚI**: Gán `gradientDrawable.cornerRadius = cornerRadiusPx` khi tạo và cập nhật đồng bộ trong `updateCornerRadius()`.

---

### <a id="bug-12"></a>12. `BUG-12` — Chống giật ngược scroll về `initialFocusPosition` khi recompose
- **Tệp**: [LeanbackNativeRow.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/LeanbackNativeRow.kt), [LeanbackModernRow.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt)
- **Code CŨ**: `if (initialFocusPosition > 0 && !gridView.hasFocus() && gridView.selectedPosition != initialFocusPosition) gridView.setSelectedPosition(initialFocusPosition)`
- **Code MỚI**: Theo dõi `lastAppliedInitialPosition` — chỉ gọi `setSelectedPosition(initialFocusPosition)` khi `initialFocusPosition != lastAppliedInitialPosition && !gridView.hasFocus()`.

---

### <a id="bug-13"></a>13. `BUG-13` — Chuẩn hóa `marginStart`/`marginEnd` cho RTL
- **Tệp**: [LeanbackPosterViewHolder.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/holder/LeanbackPosterViewHolder.kt), [LeanbackEpisodeViewHolder.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/holder/LeanbackEpisodeViewHolder.kt), [LeanbackModernRowAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRowAdapter.kt)
- **Code CŨ**: Dùng `leftMargin` / `rightMargin` cùng `Gravity.START` / `END`.
- **Code MỚI**: Đổi toàn bộ sang `marginStart` / `marginEnd`.

---

### <a id="bug-14"></a>14. `BUG-14` — Co giãn chiều cao vùng nhãn theo `fontScale` & bỏ khoảng trống thừa 44dp ở Episode
- **Tệp**: [LeanbackNativeRow.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/LeanbackNativeRow.kt), [LeanbackModernRow.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt)
- **Code CŨ**: Hardcode `44.dp` (Universal) và `26.dp` (Modern) không nhân `fontScale`, không kiểm tra Episode.
- **Code MỚI**:
  - `LeanbackNativeRow`: `if (showLabels && items.firstOrNull() !is LeanbackCardItem.Episode) 44.dp * density.fontScale.coerceIn(1f, 1.4f) else 0.dp`
  - `LeanbackModernRow`: `if (showLabels) (26f * density.fontScale.coerceIn(1f, 1.4f)).dp else 0.dp`

---

### <a id="bug-15"></a>15. `BUG-15` — Thẻ "Xem tất cả" (`See All`) trực quan có mũi tên `→`
- **Tệp**: [LeanbackCardItem.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/model/LeanbackCardItem.kt), [LeanbackPosterViewHolder.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/holder/LeanbackPosterViewHolder.kt), [CatalogRowSection.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/components/CatalogRowSection.kt)
- **Code CŨ**: Dùng `LeanbackCardItem.Poster(imageUrl = null, badgeText = "ALL")` → ô xám trống không có mũi tên.
- **Code MỚI**: Thêm `val isSeeAll: Boolean = false`, hiển thị `seeAllOverlay` căn giữa (icon `→` + nhãn) nổi bật kể cả khi tắt `showLabels`.

---

### <a id="bug-16"></a>16. `BUG-16` — Debounce `onLoadMore` `250ms` + kiểm tra `itemCount` ở Universal Adapter
- **Tệp**: [LeanbackUniversalAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/adapter/LeanbackUniversalAdapter.kt), [InteropHorizontalGridView.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/container/InteropHorizontalGridView.kt), [InteropVerticalGridView.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/container/InteropVerticalGridView.kt)
- **Code CŨ**: Gọi trực tiếp `universalAdapter?.onLoadMore?.invoke()` ở mỗi sự kiện focus gần cuối danh sách.
- **Code MỚI**: Gọi `universalAdapter?.requestLoadMore()` có khóa `lastLoadMoreItemCount` và cửa sổ debounce `250ms`.

---

### <a id="bug-17"></a>17. `BUG-17` — Tôn trọng cài đặt `Hardware Bitmaps` an toàn
- **Tệp 1**: [LeanbackArtworkLoader.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/core/LeanbackArtworkLoader.kt) (`loadIntoImageView`, `prefetch`)
  - **Code CŨ**: Luôn gọi `.allowHardware(false)` cho mọi request.
  - **Code MỚI**: Chỉ gọi `.allowHardware(false)` khi `blurred == true`; các ảnh thường để `HardwareBitmapSettingInterceptor` quyết định theo Settings của người dùng.
- **Tệp 2**: [LeanbackColorEngine.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/core/LeanbackColorEngine.kt) (`extractDominantColor`)
  - **Code MỚI**: Bọc `bitmap.copy(Bitmap.Config.ARGB_8888, false)` trong `runCatching { ... }.getOrNull()` cho `Bitmap.Config.HARDWARE`, tự `recycle()` bản copy tạm trong `finally` để không bao giờ crash `IllegalStateException`.

---

### <a id="ux-theme"></a>18. `UX-THEME` — Đồng bộ `NuvioTheme` / `PosterBorderStyle`, Marquee, Fallback & TalkBack
- **Tệp mới**: [LeanbackThemeStyle.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/core/LeanbackThemeStyle.kt) (`LeanbackThemeStyle`, `rememberLeanbackThemeStyle`)
- **Áp dụng tại**: `LeanbackBaseViewHolder`, `LeanbackPosterViewHolder`, `LeanbackEpisodeViewHolder`, `LeanbackAvatarViewHolder`, `LeanbackNativeRow`, `LeanbackNativeGrid`, `LeanbackModernRow`, `ModernRowFloatingFocusRingView`, `ModernHomeRowsList`.
- **Cải tiến bổ sung**:
  - Bật chạy chữ `MARQUEE` khi focus (`titleText.isSelected = hasFocus`) ở cả `LeanbackPosterViewHolder` và `LeanbackCardViewHolder`.
  - Thêm chữ cái đầu (Initials, VD `"Tom Hanks" → "TH"`) cho `LeanbackAvatarViewHolder` khi thiếu ảnh, và tiêu đề fallback căn giữa khi ảnh Poster lỗi mà `!showLabels`.
  - Gán `root.contentDescription` đầy đủ cho TalkBack trên cả 4 ViewHolder.

---

### <a id="clean-dead"></a>19. `CLEAN-DEAD` — Gộp tiện ích lõi & Dọn Dead Code
- **Tệp mới**: [LeanbackBlurFilterCache.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/core/LeanbackBlurFilterCache.kt) — thay thế 2 cache `BlurMaskFilter` trùng lặp ở `PlayStoreGlowView` và `ModernRowFloatingFocusRingView`.
- **Dọn dẹp**:
  - `LeanbackColorEngine.kt`: Xóa `dominantColorCache` trùng lặp và unused import `android.util.LruCache`, chỉ giữ 1 LRU `LinkedHashMap`.
  - `ModernInteropHorizontalGridView.kt` & `ModernRowFloatingFocusRingView.kt`: Xóa tham số `isLandscape` không dùng; bỏ side-effect gọi `value?.invoke()` trong setter `canTakeFocus`; đồng nhất `setItemViewCacheSize(8)`.
  - `InteropHorizontalGridView.kt` & `LeanbackNativeRow.kt`: Đồng nhất 1 chỗ gọi `setItemViewCacheSize(8)`.
  - `LeanbackUniversalAdapter.kt`: Xóa `onNavigateRight` chết không dùng.
  - `LeanbackModernRowAdapter.kt`: Xóa 2 tham số chết `borderWidthPx`, `glowMarginPx` khỏi `LeanbackCardViewHolder`.

---

### <a id="release-fix-01"></a>20. `RELEASE-FIX-01` — Sửa Crash bản Release ngay lúc mở app (R8 obfuscate Gson disk cache)
- **Nguyên nhân**: `CachedInProgressItem` / `CachedNextUpItem` (và `PendingProgressUpsert` / `PendingWatchedUpsert`, Live TV Gson models) thiếu `@Keep` và thiếu rule trong `proguard-rules.pro`. Mỗi lần build Release mới, R8 đổi tên field khiến Gson đọc file cache cũ ra `contentId = null` → nổ `NullPointerException: Parameter specified as non-null is null: parameter contentId` trong `HomeViewModel.loadContinueWatching()`.
- **Tệp đã sửa**:
  - [ContinueWatchingEnrichmentCache.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/data/local/ContinueWatchingEnrichmentCache.kt): Thêm `@Keep` cho `CachedNextUpItem`, `CachedInProgressItem` và hàm tự lọc/xóa file cache obfuscated cũ (`sanitizeNextUpItem`, `sanitizeInProgressItem`).
  - [WatchStateMutationStore.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/core/sync/WatchStateMutationStore.kt): Thêm `@Keep` cho `PendingProgressUpsert`, `PendingWatchedUpsert`.
  - [HomeViewModel.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/HomeViewModel.kt): Bọc khối khôi phục Continue Watching disk cache trong `runCatching { ... }.onFailure { ... }`.
  - [proguard-rules.pro](file:///f:/MyGithub/NuvioTV-Fast/app/proguard-rules.pro): Thêm `-keep @androidx.annotation.Keep class * { *; }` và `-keep` cho các model Gson local cache / sync / Live TV.
- **Kiểm chứng thực tế**: Build `./gradlew :app:assembleFullRelease` + cài đè trực tiếp trên emulator (`com.nuvio.tv.fast`) với dữ liệu cũ → mở app thành công (`Displayed +3s524ms`, `ZERO CRASH`).

---

### <a id="release-fix-02"></a>21. `RELEASE-FIX-02` — Sửa Crash bản Release khi bấm vào mục Live TV (tự chữa lành dữ liệu Gson cũ bị R8 obfuscate)
- **Nguyên nhân**: Sau khi thêm `-keep` cho các model Live TV ở bản Release, dữ liệu Live TV đã lưu từ bản Release trước (`playlists_blob`, `livetv_settings`, `stalker_settings`, `xtream_settings`, `live_tv_channels_cache.json`) mang tên field obfuscated cũ nên Gson đọc qua Unsafe để các trường non-null (`id`, `source`, `type`, `streamUrl`, `portalUrl`, `guideSize`...) thành `null` → nổ `NullPointerException` ngay khi bấm vào Live TV. Đồng thời `CatchupInfo`, `DrmInfo`, `LiveTvPlaylistType` chưa được `-keep`.
- **Tệp đã sửa**:
  - [LiveTvModels.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/features/livetv/LiveTvModels.kt) & [proguard-rules.pro](file:///f:/MyGithub/NuvioTV-Fast/app/proguard-rules.pro): Gắn `@Keep` và `-keep` đầy đủ cả `CatchupInfo`, `DrmInfo`, `EpgAssignment`, `LiveTvPlaylistType`.
  - [LiveTvRepository.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/features/livetv/LiveTvRepository.kt): Sanitize `decodePlaylists()` qua `Any?` chống `null` field; nếu blob cũ hỏng sẽ tự fallback khôi phục URL từ `playlist_url`.
  - [LiveTvStorage.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/features/livetv/LiveTvStorage.kt): Sanitize `loadStalkerSettings()`, `loadXtreamSettings()`, `loadLiveTvSettings()` chống `null` ở mọi trường `String`.
  - [LiveTvCacheManager.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/features/livetv/LiveTvCacheManager.kt): Sanitize `loadChannels()` chống `null` ở `id`/`name`/`streamUrl`/`headers`/`catchup`/`drm` và tự xóa file cache kênh cũ hỏng để nạp mới sạch sẽ.


---

## 🔄 Vòng 2 — Rà Soát Sâu & Hoàn Thiện Triệt Để (`PLAN-leanback-deep-review-r2.md`)

### `R2-BUG-01` — Khôi phục `PosterBorderStyle.RAINBOW`, tách chuẩn màu `SOLID_*`/`THEME`/`NONE`, áp dụng `borderWidthDp` & đồng bộ `AppFont` (`LBR2-05`)
- **Mức độ**: 🔴 P1 (High)
- **File & Dòng đã sửa**:
  - [Theme.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/theme/Theme.kt): Expose thêm `LocalAppFont` và `LocalPosterBorderStyle` qua `CompositionLocalProvider` và `NuvioTheme`.
  - [FocusRingStyle.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/theme/FocusRingStyle.kt): Mở `val gradientColors: List<Color>` trên `NuvioFocusRingStyle`.
  - [LeanbackThemeStyle.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/core/LeanbackThemeStyle.kt): Bổ sung `focusGradientColors`, `posterBorderStyle`, `appFont`, `typeface` (cache qua `ResourcesCompat.getFont`) và `LeanbackFocusBorderDrawable` vẽ cả viền đơn sắc lẫn dải màu cầu vồng (`LinearGradient`) với 0 cấp phát trong `draw()`. Giữ thuần sắc cho `SOLID_WHITE/RED/BLUE/GOLD` (không bị pha lệch bởi màu poster).
  - [LeanbackBaseViewHolder.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/holder/LeanbackBaseViewHolder.kt): Sử dụng `LeanbackFocusBorderDrawable`, tính độ dày viền theo `style.borderWidthDp`, và tắt cả `glowView` khi `style.hideFocusBorder == true` (`PosterBorderStyle.NONE`).
  - [ModernRowFloatingFocusRingView.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/ModernRowFloatingFocusRingView.kt): Bổ sung `syncRainbowShader()` cache `LinearGradient` theo kích thước ring khi `themeStyle.isRainbowBorder == true`.
  - [LeanbackPosterViewHolder.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/holder/LeanbackPosterViewHolder.kt), [LeanbackEpisodeViewHolder.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/holder/LeanbackEpisodeViewHolder.kt), [LeanbackAvatarViewHolder.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/holder/LeanbackAvatarViewHolder.kt), [LeanbackModernRowAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRowAdapter.kt): Áp dụng `style.typeface` vào các `TextView`.
- **Cách khôi phục riêng mục này**:
  - Đổi `focusBorderDrawable` trong `LeanbackBaseViewHolder.kt` về `GradientDrawable`, bỏ `syncRainbowShader()` trong `ModernRowFloatingFocusRingView.kt`, và thu gọn `LeanbackThemeStyle.kt` về bản chỉ đọc `solidColor`.

---

### `R2-BUG-02` — Reactivity trạng thái Watched trên Modern Home & `FolderDetailScreen`, đồng bộ `blurred` prefetch & màu ProgressBar (`LBR2-01`)
- **Mức độ**: 🔴 P1 (High)
- **File & Dòng đã sửa**:
  - [LeanbackModernRowAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRowAdapter.kt): Thêm mảng theo dõi `watchedStates: BooleanArray` và `syncWatchedStateChanges()` trong `submitItems()` để cập nhật ngay badge `✓` tại chỗ khi `isCatalogItemWatched` thay đổi mà danh sách `ModernCarouselItem` không đổi reference; truyền `blurred = shouldBlur` trong `prefetchItemAt()` cho `ContinueWatchingItem.NextUp`; đồng bộ `style.accentColor` cho `progressBar`.
  - [ModernHomeRowsList.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeRowsList.kt): Truyền `blurred = shouldBlur` trong vertical image prefetch cho `ContinueWatchingItem.NextUp`.
  - [FolderDetailScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/collection/FolderDetailScreen.kt): Đưa `watchedMaskHash` vào key `remember(...)` của `leanbackItems`.
- **Cách khôi phục riêng mục này**:
  - Bỏ `syncWatchedStateChanges()` trong `LeanbackModernRowAdapter.kt`, bỏ `blurred = shouldBlur` ở 2 chỗ gọi `prefetch()`, và bỏ `watchedMaskHash` khỏi `remember` trong `FolderDetailScreen.kt`.

---

### `R2-BUG-03` — Khôi phục `initialFocusPosition` khi dữ liệu `items` nạp chậm bất đồng bộ (`LBR2-02`)
- **Mức độ**: 🔴 P1 (High)
- **File & Dòng đã sửa**:
  - [LeanbackNativeRow.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/LeanbackNativeRow.kt) & [LeanbackModernRow.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt): Khởi tạo `lastAppliedInitialPosition = -1` (thay vì `initialFocusPosition`), chỉ cập nhật sau khi `setSelectedPosition` thực sự được áp dụng trên danh sách `items` không rỗng, kèm `OnAttachStateChangeListener` trong `LaunchedEffect`.
- **Cách khôi phục riêng mục này**:
  - Đổi `mutableIntStateOf(-1)` về `mutableIntStateOf(initialFocusPosition)` ở 2 file trên.

---

### `R2-BUG-04` & `R2-BUG-06` — Chuẩn hóa `InteropVerticalGridView.focusSearch`, Focus Guard & `unbind()` nhánh Universal (`LBR2-03`)
- **Mức độ**: 🔴 P1 (High)
- **File & Dòng đã sửa**:
  - [InteropVerticalGridView.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/container/InteropVerticalGridView.kt): Thêm `.coerceAtMost(totalItems - 1)` vào `focusSearch(View.FOCUS_DOWN)` để rơi xuống đúng phần tử cuối của hàng cuối thiếu cột; tái sử dụng `isVerticalGridLastRow(...)` trong `dispatchKeyEvent`.
  - [LeanbackNativeGrid.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/LeanbackNativeGrid.kt): Expose thêm `onNavigateRightOnLastColumn` đồng bộ với 3 biên còn lại.
  - [LeanbackUniversalAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/adapter/LeanbackUniversalAdapter.kt): Thêm kiểm tra `deferredMayTake` ở lần `rv.post` thứ 2 trong Placeholder-to-Data Focus Guard.
  - [LeanbackPosterViewHolder.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/holder/LeanbackPosterViewHolder.kt) & [LeanbackEpisodeViewHolder.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/holder/LeanbackEpisodeViewHolder.kt): Reset sạch `isSelected = false` và ẩn `badgeView`/`watchedBadgeView`/`progressBar`/`dateText` trong `unbind()`; lọc `!imageUrl.trim().isPlaceholder()` cho Episode thumbnail.
- **Cách khôi phục riêng mục này**:
  - Hoàn tác từng khối tương ứng trong 5 file trên.

---

### `R2-BUG-05` — Sửa 3 điểm rớt Focus ở `SearchDiscoverSection`, `FolderDetailScreen` và `CollectionSection` (`LBR2-04`)
- **Mức độ**: 🔴 P1 (High)
- **File & Dòng đã sửa**:
  - [SearchDiscoverSection.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/search/SearchDiscoverSection.kt): Gắn `modifier = Modifier.focusRequester(firstItemFocusRequester)` vào `LeanbackNativeGrid` trong `DiscoverGrid`.
  - [FolderDetailScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/collection/FolderDetailScreen.kt): Tự động focus vào Tab đang chọn khi mở Folder nhiều tab lần đầu (`!tabFocusState.hasSavedFocus`); nối `gridScrollToTopTrigger` vào `restoreFocusNonce` của `LeanbackNativeGrid`.
  - [CollectionSection.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/detail/CollectionSection.kt): Gắn `Modifier.focusProperties { canFocus = false }` và truyền `isFocusEnabled = !blockDefaultRestore` xuống `LeanbackNativeRow`.
- **Cách khôi phục riêng mục này**:
  - Hoàn tác các dòng tương ứng ở `SearchDiscoverSection.kt`, `FolderDetailScreen.kt`, `CollectionSection.kt`.

---

### `R2-BUG-07`, `R2-BUG-08`, `R2-BUG-09` — RTL `progressBar`, Fallback Title & A11y Modern Home, tối ưu `watchedMaskHash` & Hardware Bitmap Hold-Scroll Guard (`LBR2-06`)
- **Mức độ**: 🟡 P2 (Medium)
- **File & Dòng đã sửa**:
  - [LeanbackModernRowAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRowAdapter.kt): Đổi `leftMargin`/`rightMargin` sang `marginStart`/`marginEnd` cho `progressBar`; thêm `fallbackTitleText` căn giữa khi ảnh lỗi và `showLabels == false`; thêm `", Watched"` vào `root.contentDescription`.
  - [CatalogRowSection.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/components/CatalogRowSection.kt): Thay `catalogRow.items.map { latestIsItemWatched(it) }` bằng `watchedMaskHash: Int` (0 cấp phát List trên mỗi recomposition).
  - [LeanbackColorEngine.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/core/LeanbackColorEngine.kt): Bỏ qua `bitmap.copy(ARGB_8888, false)` trên `Bitmap.Config.HARDWARE` khi `LeanbackArtworkLoader.isHoldScrolling == true`.
- **Cách khôi phục riêng mục này**:
  - Hoàn tác từng đoạn tương ứng ở 3 file trên.

---

### `R2-CLEAN-01` & `R2-CLEAN-02` — Xóa 4 callback điều hướng chết trên cả 2 Adapter & bỏ cấp phát thừa `listState` ở 4 Detail Sections (`LBR2-07`)
- **Mức độ**: 🧹 Code Hygiene / Performance
- **File & Dòng đã sửa**:
  - [LeanbackUniversalAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/adapter/LeanbackUniversalAdapter.kt), [LeanbackNativeRow.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/LeanbackNativeRow.kt), [LeanbackNativeGrid.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/leanback/LeanbackNativeGrid.kt), [LeanbackModernRowAdapter.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRowAdapter.kt), [LeanbackModernRow.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt): Xóa 4 thuộc tính chết `onNavigateDown`, `onNavigateUp`, `onNavigateLeft`/`onNavigateLeftOnFirstItem`, `onNavigateBack` khỏi 2 Adapter; xóa `setItemViewCacheSize(12)` gọi trùng ở `LeanbackNativeGrid.kt`.
  - [CastSection.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/detail/CastSection.kt), [CollectionSection.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/detail/CollectionSection.kt), [MoreLikeThisSection.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/detail/MoreLikeThisSection.kt), [TrailerSection.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/detail/TrailerSection.kt): Đổi `listState: LazyListState = rememberLazyListState()` thành `@Suppress("UNUSED_PARAMETER") listState: LazyListState? = null`.
  - [CastDetailScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/cast/CastDetailScreen.kt): Xóa `hasRequestedInitialFocus` và `upFocusRequester` thừa trong `FilmographyRow`, đặt `rowKey = "cast_filmography_${filmographyFilter.name}"`.
- **Cách khôi phục riêng mục này**:
  - Hoàn tác các tham số tương ứng nếu cần.
