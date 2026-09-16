# UX Areas

Project-specific UX areas to watch for when running
`/review-ux-android` or noticing issues during normal work.

## What to Look For

- **Compose animations**: missing `animateContentSize`,
  `AnimatedVisibility`, `Crossfade`, or `animate*AsState`
  on values that change visibly
- **List behavior**: missing `animateItemPlacement` on
  `LazyColumn`/`LazyRow` items that can reorder, appear,
  or disappear
- **Navigation**: abrupt screen swaps without enter/exit
  transitions; missing back-gesture handling
- **Charts (Vico)**: missing entry animations, abrupt
  data updates, missing loading state while data loads
- **Filter chips / toggles**: non-animated state changes,
  missing selection feedback
- **Bottom navigation / tabs**: missing indicator
  animations, abrupt content swaps between tabs
- **Forms & inputs**: missing keyboard IME actions,
  missing input validation feedback, missing
  `bringIntoViewRequester` for focused fields
- **Dialogs vs bottom sheets**: use bottom sheets for
  mobile-friendly interactions where appropriate
- **Empty/loading/error states**: every screen that
  fetches data should have all three; prefer skeletons
  over spinners for content-heavy screens
- **Re-entry loading flash**: screens that call `refresh()` (or equivalent) on `ON_RESUME` / `onStart` must NOT show a full-screen loading spinner when data is already present. The spinner should only appear on the very first load (no data yet). Subsequent resume refreshes should be silent — update state in the background without setting `isLoading = true`. Look for `LifecycleEventObserver` blocks that call a load function unconditionally on `ON_RESUME`, and check whether the loading helper (`launchWithLoading` or similar) has a `showLoading = false` / silent path for that call site.
- **Haptics**: key actions (complete set, save workout,
  delete) should provide haptic feedback
- **Accessibility**: `contentDescription` on every
  `Icon` / `Image`; min 48dp touch targets; semantic
  grouping with `Modifier.semantics`
- **Heatmaps & custom charts**: cell tap feedback,
  tooltips on long-press, smooth color interpolation
