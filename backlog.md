# vademo Backlog

### 2026.09.16 (first version)

- [ ] **Try the first version on the real phone against GitLab.** Install
  the debug APK, add a GitLab personal access token with the
  `write_repository` scope, sync all four knowledge bases, edit a note and
  confirm the commit arrives on the computer. Only local repositories have
  been tested so far. This also checks the unverified assumption in
  `data/KbRepository.kt` that GitLab accepts the token with the user name
  `oauth2`. Roadmap step 1. Effort: Small.
- [ ] **Size table columns by their content.** The markdown renderer gives
  every column the same width, so a long reasons column (the scorecard in
  `kb-business/ideas/tyfold.md`) becomes tall and narrow and needs a
  sideways swipe. Needs a custom table layout in `ui/note/NoteScreen.kt`.
  Effort: Medium.
- [ ] **Search note titles and text** across all knowledge bases. Left out
  of the first version on purpose. Effort: Medium.
- [ ] **Create a new note from a knowledge base's `templates/`** with the
  frontmatter filled in. Effort: Medium.
- [ ] **Show the health counts the sapiary dashboard shows**: `(unverified)`
  markers, notes nothing links to, broken links. Effort: Medium.
- [ ] **Add JGit keep rules before switching on release minification.**
  `isMinifyEnabled` is false; JGit uses reflection. Effort: Small.
