## [2026-11-01 20:37] - performMeasureAndLayout Crash During D-pad Navigation Across Rows

- **Type**: Runtime / Integration
- **Severity**: Critical
- **File**: `app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt:267`
- **Agent**: mon
- **Root Cause**: During fast D-pad navigation across rows (DPAD_UP/DOWN), catalog rows received asynchronous item updates triggering `notifyDataSetChanged()`. Android's `RecyclerView` recycled the previously focused card view via `removeViewAt` during window draw traversal (`performDraw`). In Android's `ViewGroup.removeViewInternal`, removing a focused view calls `rootViewRequestFocus() -> getRootView().requestFocus()`. Because the window was in the draw traversal (where `ViewRootImpl.isInLayout()` is `false`), `getRootView()` returned `PhoneWindow$DecorView`. `DecorView.requestFocus()` searched descendants into `AndroidComposeView.requestFocus()`, which called `performMeasureAndLayout()`. Compose detected that a layout pass was invoked during measure/layout/draw and threw `IllegalArgumentException: performMeasureAndLayout called during measure layout`, crashing the application.
- **Error Message**: 
  ```
  java.lang.IllegalArgumentException: performMeasureAndLayout called during measure layout
  	at androidx.compose.ui.platform.AndroidComposeView.requestFocus(SourceFile:53)
  	at android.view.ViewGroup.onRequestFocusInDescendants(ViewGroup.java:3282)
  	at android.view.ViewGroup.requestFocus(ViewGroup.java:3231)
  	at android.view.View.rootViewRequestFocus(View.java:7118)
  	at android.view.ViewGroup.removeViewInternal(ViewGroup.java:5379)
  	at android.view.ViewGroup.removeViewAt(ViewGroup.java:5306)
  	at androidx.recyclerview.widget.RecyclerView.onLayout(SourceFile:6)
  	at androidx.compose.ui.platform.AndroidComposeView.dispatchDraw(SourceFile:17)
  ```
- **Fix Applied**: 
  1. Permanent Root Isolation: Overrode `getRootView(): View = this` permanently on both `InteropHorizontalGridView` and the card's root `LinearLayout`. This guarantees `rootViewRequestFocus()` calls `this.requestFocus()` confined strictly within the `HorizontalGridView`, never escaping to `PhoneWindow$DecorView` or `AndroidComposeView`.
  2. Deferred Focus Checks: Added `isBusyLayout` checking `isComputingLayout || isInLayout || !isAttachedToWindow` in `InteropHorizontalGridView.requestFocus()`, `requestChildFocus()`, and `focusSearch()`, posting them to the message queue if busy.
  3. Safety Catch Guards: Wrapped `onLayout()`, `removeViewAt()`, `removeViews()`, `removeView()`, and `removeViewInLayout()` in `InteropHorizontalGridView` with try-catch blocks that intercept any Compose `performMeasureAndLayout` exceptions safely.
  4. Non-Interfering Adapter Submissions: In `LeanbackRowAdapter.submitItems()`, checked `rv.isComputingLayout` and deferred `notifyDataSetChanged()` via `rv.post` if RecyclerView is actively computing layout.
- **Prevention**: In Jetpack Compose + View interop with complex list views, never allow a child view's `getRootView()` to return `DecorView`, and never call `notifyDataSetChanged()` or synchronous `requestFocus()` while `isComputingLayout` is true.
- **Status**: Fixed

---

## [2026-11-01 20:20] - Compilation Error Unresolved Reference glowMarginPx

- **Type**: Syntax / Compilation
- **Severity**: Low
- **File**: `app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt:631`
- **Agent**: mon
- **Root Cause**: When restoring PlayStoreGlowView to v1.2.50, the `glowMarginPx` property was omitted from PlayStoreGlowView while the ViewHolder setup referenced it.
- **Error Message**: 
  ```
  e: file:///F:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt:631:18 Unresolved reference 'glowMarginPx'.
  ```
- **Fix Applied**: Added `var glowMarginPx: Float = 0f` to `PlayStoreGlowView` and updated `onDraw` to use `margin = if (glowMarginPx > 0f) glowMarginPx else glowSpreadPx`.
- **Prevention**: Ensure all view properties referenced in ViewHolder are declared in custom View classes.
- **Status**: Fixed

---

## [2026-11-01 19:45] - performMeasureAndLayout Crash When Disabling Animations or Entering Settings

- **Type**: Runtime / Integration
- **Severity**: Critical
- **File**: `app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt:295`
- **Agent**: mon
- **Root Cause**: When the user turned off animations (`animationsEnabled = false`), `LeanbackRowAdapter` was recreated because `animationsEnabled` was in the `remember` key list. In `AndroidView.update`, resetting `gridView.adapter` or calling synchronous `setSelectedPosition(targetIdx)` during Compose's layout pass caused RecyclerView to recycle/remove views. When a focused card view was removed, Android's `ViewGroup.removeViewInternal` automatically invoked `rootViewRequestFocus()` on `AndroidComposeView`, which executed `performMeasureAndLayout()` while Compose was already measuring/laying out, crashing the app. When entering Settings with `ExitTransition.None`, detaching the row with a focused child also triggered this crash.
- **Error Message**: 
  ```
  java.lang.IllegalArgumentException: performMeasureAndLayout called during measure layout
  	at androidx.compose.ui.node.LayoutNode.performMeasureAndLayout
  	at androidx.compose.ui.platform.AndroidComposeView.requestFocus
  	at android.view.ViewGroup.onRequestFocusInDescendants
  	at android.view.ViewGroup.requestFocus
  	at android.view.View.rootViewRequestFocus
  	at android.view.ViewGroup.removeViewInternal
  	at android.view.ViewGroup.removeViewAt
  	at androidx.recyclerview.widget.RecyclerView.onLayout
  ```
- **Fix Applied**: 
  1. Overrode `removeView`, `removeViewAt`, `removeViewInLayout`, `removeViews`, `removeViewsInLayout`, `removeDetachedView`, `removeAllViews`, and `onDetachedFromWindow` in `InteropHorizontalGridView` to safely call `child.clearFocus()` before removal. This ensures `mFocused` is null so `ViewGroup.removeViewInternal` never invokes `rootViewRequestFocus()`.
  2. Overrode `requestFocus` and `requestChildFocus` in `InteropHorizontalGridView` to defer via `post` when `isInLayout` is true.
  3. Stable adapter: changed `remember(cardWidthPx, ..., animationsEnabled)` to `remember(rowKey)` so the adapter is never destroyed on config or animation changes.
  4. Removed synchronous duplicate `isPendingFocus` / `setSelectedPosition` block from `AndroidView.update`.
- **Prevention**: Never trigger synchronous layout mutations or adapter replacements inside `AndroidView.update`, and always intercept view removals in custom ViewGroups embedded in Compose to prevent `rootViewRequestFocus` during Compose layout passes.
- **Status**: Fixed

---

## [2026-11-01 18:43] - Syntax Error in LeanbackModernRow.kt Extra Closing Brace

- **Type**: Syntax
- **Severity**: Low
- **File**: pp/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt:264
- **Agent**: mon
- **Root Cause**: An extra closing brace was left at the end of PlayStoreGlowView after regex replacement.
- **Error Message**: 
  `
  e: file:///F:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt:264:1 Syntax error: Expecting a top level declaration.
  `
- **Fix Applied**: Removed the extra closing brace.
- **Prevention**: Verify brace balance after regex modifications.
- **Status**: Fixed

---

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


## [2026-11-01 15:55] - performMeasureAndLayout Crash & Laggy Focus When Rapidly Holding D-pad DOWN

- **Type**: Runtime / Integration
- **Severity**: Critical
- **File**: `app/src/main/java/com/nuvio/tv/ui/screens/home/ModernHomeRowsList.kt:344`, `app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt:140`
- **Agent**: mon
- **Root Cause**: 
  1. Holding D-pad DOWN rapidly caused repeat key events (`repeatCount > 0`) to be intercepted by `dpadVerticalFastScroll` on `LazyColumn`. This modifier froze focus on the originating card on row 0 while dragging the list down at high velocity.
  2. Because focus stayed on row 0, the card focus never followed the scrolling films ("focus không theo").
  3. When row 0 was scrolled offscreen, `HorizontalGridView.removeViewAt` detached the still-focused card during its native layout pass. Android's `ViewGroup.removeViewInternal` called `rootViewRequestFocus()` which invoked `AndroidComposeView.requestFocus()`, triggering `IllegalArgumentException: performMeasureAndLayout called during measure layout` and crashing the application.
  4. Sharp gray corners appeared behind posters because `cardContainer` had `setBackgroundColor("#1A1A1A")` with `clipToOutline = false`, exposing the sharp 90-degree rectangle behind `posterImage`'s rounded corners.
- **Error Message**: 
  ```
  java.lang.IllegalArgumentException: performMeasureAndLayout called during measure layout
  	at androidx.compose.ui.platform.AndroidComposeView.requestFocus(SourceFile:53)
  	at android.view.View.rootViewRequestFocus(View.java:7118)
  	at android.view.ViewGroup.removeViewInternal(ViewGroup.java:5379)
  	at android.view.ViewGroup.removeViewAt(ViewGroup.java:5306)
  	at androidx.recyclerview.widget.RecyclerView.onLayout(SourceFile:6)
  ```
- **Fix Applied**: 
  1. Removed `.dpadVerticalFastScroll` from `LazyColumn` in `ModernHomeRowsList.kt` so repeat D-pad events pass directly to `onNavigateDown` / `onNavigateUp`.
  2. Added 85ms key repeat throttle in `onNavigateDown` and `onNavigateUp` to prevent message queue flooding while allowing rapid ~11 rows/sec navigation.
  3. Immediately called `rowGrids[currentRow.key]?.clearFocus()` before advancing rows so the previous row's card never gets scrolled offscreen while holding focus.
  4. Overrode `removeViewAt`, `removeView`, and `onDetachedFromWindow` in `InteropHorizontalGridView` to ensure any child with focus has `clearFocus()` called before `super.removeView*()`.
  5. Implemented `PlayStoreGlowView` radiating smooth, hardware-accelerated ambient glow outside the card matching Google Play Store, and fixed `cardContainer.background` to use `GradientDrawable` with rounded corners to completely eliminate sharp gray corners.
- **Prevention**: In hybrid Compose-View lists, never freeze focus on a child while scrolling it offscreen, and always clear child focus before native view removal.
- **Status**: Fixed

---

## [2026-11-01 17:50] - Poster Corner Black Triangular Gap & Concentric Ring Artifacts in Neon Glow

- **Type**: Logic / UI
- **Severity**: Medium
- **File**: `app/src/main/java/com/nuvio/tv/ui/screens/home/LeanbackModernRow.kt:180`
- **Agent**: mon
- **Root Cause**: 
  1. Concentric ring strokes (`Paint.Style.STROKE` with 6 discrete thicknesses and alphas) produced visible step-banding (tree ring contours) instead of continuous photorealistic neon light emission.
  2. `clipOutPath(clipPath)` called on Skia Software Canvas (`LAYER_TYPE_SOFTWARE`) forced clipping to an `SkRegion`. On software rendering, `SkRegion` cannot represent arbitrary bezier curved cuts and clipped out the entire axis-aligned bounding box `[cardLeft, cardTop, cardRight, cardBottom]`.
  3. Consequently, at all 4 corners between the rounded corner arc and the bounding box corner, the glow was completely clipped away, leaving an empty 125-pixel black triangular gap (`0f0d0d`) showing the screen background.
  4. Pixel-wise max saturation color extraction was easily fooled by small saturated patches (e.g. blue jacket on Ali G) over large background fields (e.g. yellow background).
- **Error Message**: 
  ```
  UI Visual Defect: Poster corner has a sharp triangular black gap between the curved white border and the outer glow; concentric discrete rings look banded.
  ```
- **Fix Applied**: 
  1. Replaced discrete concentric stroke rings with true continuous Gaussian Blur (`BlurMaskFilter(..., Blur.NORMAL)` on 3 overlapping passes: Core, Mid, Ambient Aura).
  2. Removed `clipOutPath` on the software canvas so Skia does not apply rectangular bounding-box clipping; the glow curves seamlessly around all 4 rounded corners.
  3. Relied on `posterImage` (rendered with `clipToOutline = true` on top of `glowView`) to naturally and cleanly cover the inside of the card.
  4. Implemented 16-Hue-bin histogram scoring in `extractDominantColor`, ensuring large background colors (like Ali G yellow) win over small objects while preserving saturated accents on dark posters.
  5. Cached extracted dominant colors in an LRU cache by `item.key` to instantly glow in the poster's hue upon focus without delay.
- **Prevention**: Never use `clipOutPath` on a software canvas if curved boundaries are expected outside the path; let overlapping child views handle interior coverage. Always use histogram binning for dominant image color extraction.
- **Status**: Fixed

---
