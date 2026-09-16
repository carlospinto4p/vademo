---
name: review-ux-android
version: 1.0.0
description: >-
  Review the app's UX — animations, transitions, widgets,
  feedback, accessibility — to propose improvements, sorted
  by impact and effort, then add them to the backlog.
---

<!-- opencode counterpart: .opencode/command/review-ux-android.md — the
     `/review-ux-android` slash shim (skills aren't `/` commands in the
     opencode TUI). Keep in sync (global claude-opencode-sync rule). -->

Check your procedural guidelines in
`.claude/rules/review-ux-android.md`, then check
`.claude/rules/ux-areas-android.md` for project-specific
UX areas to focus on (if the file exists).

Review the whole app UI (screens, composables, navigation,
theming) and propose UX improvements: missing or janky
animations, inappropriate widgets, missing visual feedback,
inconsistent motion, poor empty/loading/error states,
accessibility gaps, and anything else that degrades the
feel of the app.

Once you scan everything and sort suggestions by impact
and effort, add them to the backlog (use
`.claude/rules/backlog.md`) and show me the list to start
addressing them one by one.
