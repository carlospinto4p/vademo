# vademo Backlog

### 2026.09.16 (first version)

- [ ] **Try the first version on the real phone against GitHub.** Install
  the debug APK, add a GitHub personal access token with the `repo` scope
  (classic) or `Contents: Read and write` (fine-grained), sync all four
  knowledge bases, edit a note and confirm the commit arrives on the
  computer. Only local repositories have been tested so far. This also
  checks the unverified assumption in `data/KbRepository.kt` that GitHub
  accepts the token with the user name `oauth2`. Roadmap step 1. Effort:
  Small.
  - **Context (2026-09-20):** the four `kb-*` repos, `sapiary`, and vademo
    itself moved from GitLab to GitHub as private repos this session (see
    programme's backlog). `DEFAULT_REPOS`, the settings/UI copy, and the
    stored-token key/keystore alias were updated to GitHub accordingly —
    this item's remaining job is the on-device verification, not the code
    change.
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
