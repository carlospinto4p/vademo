# vademo Backlog

### 2026.09.20 (viewing and querying)

From `docs/kb-views-and-queries.md`, which has the reasoning and the
measurements behind these. In order — each needs the one before it.

- [ ] **Index a knowledge base in memory.** `kb/NoteIndex.kt`: one pass
  producing per note its path, title, type, status, tags, dates,
  headings, outgoing links and `(unverified)` count, plus the backlinks,
  unresolved links and orphans derived from them. Pure Kotlin beside
  `KnowledgeBase`, rebuilt after a sync, never written to disk. Effort:
  Medium.
- [ ] **Search across the notes** on that index: free text over title,
  headings and body, ranked in that order, with the `type:`, `status:`,
  `tag:`, `updated:`, `kb:`, `links:` and `is:` filters. Replaces the
  2026.09.16 search item, which said only "titles and text". Effort:
  Medium.
- [ ] **Show backlinks on a note** — "Linked from (N)" under the body.
  Half the link spine is unwalkable without it. Effort: Small.
- [ ] **Recent, Types and Tags views** of a knowledge base, beside the
  folder list. Sources are 47 of `kb-business`'s 86 notes, so grouping
  by type is what makes a list readable. Effort: Medium.
- [ ] **Make the note's chips run a query** instead of `onClick = {}`,
  which also closes the decorative-chips item in
  `.claude/rules/ux-areas-android.md`. Effort: Small.
- [ ] **Health view per knowledge base**: orphans, broken links,
  `(unverified)` figures, open questions, waiting inbox items — each row
  opening the list behind the number. Effort: Medium.
- [ ] **Saved queries**, with the Health rows as the first ones. Effort:
  Small.

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
