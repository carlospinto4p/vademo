## Changelog - vademo


### v0.1.1 - 17th September 2026

- Rewrote `.claude/rules/ux-areas-android.md`: it was a byte-for-byte
  copy of SocratesAndroid's UX-review areas (Vico charts, workout
  actions) with nothing about vademo's own screens. Replaced with
  areas grounded in `HomeScreen`, `BrowseScreen`, `NoteScreen`,
  `EditScreen`, `ConflictScreen` and `SettingsScreen`: missing
  transitions on sync-status changes, no in-progress indicator on the
  sync button, decorative no-op chips, text-only broken-link feedback,
  no empty-folder state, no save confirmation, and no settings-form
  validation. Left `.claude/rules/review-ux-android.md` and
  `.claude/skills/review-ux-android/SKILL.md` untouched — both are
  already generic procedure, not app-specific content.


### v0.1.0 - 16th September 2026

- First version: read, edit and sync the `kb-*` knowledge bases on Android.
- Added `kb/`:
  - `FrontmatterParser`: reads the note header and refreshes `updated:` on save.
  - `WikiLinks`: rewrites `[[note]]` links into tappable links, skipping code.
  - `KnowledgeBase`: folders, notes (hub first) and link resolution.
- Added `git/GitSync.kt`: clone into a temporary folder, commit phone edits,
  merge GitLab changes, push, and stop on a conflict without overwriting.
- Added `data/`: settings with the GitLab token encrypted by the Android
  Keystore, and `KbRepository` mapping settings to local clones.
- Added screens: knowledge-base list with Sync, folders and notes, note
  reading with wrapping tables, editing, conflict choice, settings.
- Added tests:
  - `FrontmatterTest`, `WikiLinksTest`, `KnowledgeBaseTest`: 22 JVM tests.
  - `GitSyncTest`: 14 tests against real local repositories.
  - `GitOnDeviceTest`, `ReadAndEditTest`: on-device tests.
