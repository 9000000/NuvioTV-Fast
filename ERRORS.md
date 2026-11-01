# System Error & Learning Log

## [2026-11-01 11:35] - GC Churn Causing 1.01-Second UI Freezes & Janks on TV Box

- **Type**: Runtime / Performance
- **Severity**: High
- **File**: `app/src/main/java/com/nuvio/tv/ui/screens/home/HomeViewModelPresentationPipeline.kt:588`
- **Agent**: mon
- **Root Cause**: During rapid D-pad card navigation on Android TV, `onItemFocusPipeline` and `preloadAdjacentItemPipeline` immediately launched un-debounced background network requests (`metaRepository.getMetaFromAllAddons`) and continuous `_uiState.update` reconstructing entire row collections. This triggered ART concurrent copying GC freeing ~300,000 objects (12MB heap churn), halting the UI thread for 1.010s (rendering frames with 318ms jank).
- **Error Message**: 
  ```
  Background concurrent copying GC freed 296881(12MB) AllocSpace objects, 14(288KB) LOS objects, 49% free, 12MB/24MB, paused 3.204ms total 1.010s
  W/NuvioPerformance: JANK: 318ms on frame 41049
  ```
- **Fix Applied**: 
  1. Debounced detail meta prefetch with `DETAIL_META_PREFETCH_DEBOUNCE_MS = 1200L` managed via single `detailMetaPrefetchJob`.
  2. Increased focus debounce from 220ms to 350ms, and adjacent debounce from 120ms to 500ms.
  3. Removed unnecessary `metaRepository.getMetaFromAllAddons` calls for adjacent items.
  4. Enabled `LocalFastHorizontalNavigationEnabled = compositionLocalOf { true }` (48ms repeat rate).
  5. Expanded Settings vertical rail width from 220dp to 240dp to prevent text truncation on "Nội dung & Khám phá".
- **Prevention**: Never trigger un-debounced full entity multi-addon network fetches or global list copy mutations while users are actively scrolling/navigating with D-pad.
- **Status**: Fixed

---

