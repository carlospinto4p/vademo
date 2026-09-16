# Periodic UX Review

Proactively suggest UX improvements — animations,
transitions, widgets, feedback, accessibility — in the
following situations:

## When to Suggest

1. **Every 6-7 versions released**: Same cadence as
   `/refactor`, `/optimize`, and `/improvements` — a UX
   pass fits alongside these reviews.

2. **During long sessions**: When wrapping up a session
   with multiple UI changes, suggest a UX pass before
   finishing.

3. **When noticing UX issues**: If you spot UX problems
   while working, flag them. Check
   `.claude/rules/ux-areas-android.md` (if it exists) for
   project-specific areas to watch for.

## How to Suggest

- Present findings as a prioritized list with file, line,
  and rationale
- Classify impact as HIGH / MEDIUM / LOW and effort as
  Small / Medium / Large
- Don't auto-apply — always propose and let the user decide
- Group related findings (e.g., all animation issues
  together, all accessibility issues together)
- After each UX proposal, add the items to `backlog.md`
  under a new section with the current date as the title
  (see `.claude/rules/backlog.md` for format)

## What to Review

- **Animations & transitions**: missing, janky, too
  fast/slow, inconsistent easing, missing shared-element
  transitions, abrupt content swaps
- **Widgets**: wrong component for the job (e.g., plain
  `Text` where a `Chip` fits, `Button` where an
  `IconButton` fits, `Dialog` where a `BottomSheet` fits)
- **Feedback**: missing ripples, missing haptics on key
  actions, missing loading indicators, missing success
  confirmations
- **States**: missing or poor empty/loading/error states,
  skeleton loaders vs spinners, retry affordances
- **Motion consistency**: enter/exit animations across
  screens, nav transitions, list item animations
  (`animateItemPlacement`)
- **Accessibility**: content descriptions, touch target
  sizes (min 48dp), color contrast, focus order, TalkBack
  labels
- **Density & layout**: cramped touch targets, inconsistent
  spacing, poor use of scrolling vs fixed regions
- **Gestures**: missing swipe-to-dismiss, pull-to-refresh,
  long-press affordances where they'd help
- **Typography & color**: inconsistent use of Material 3
  type scale and color roles
