# Viewing and querying a knowledge base

vademo shows a knowledge base the way the file system holds it: folders,
then notes, then one note's text. That is one projection of the notes, and
the least informative one on a phone. The notes already carry more
structure than the folder tree shows — `type`, `status`, `tags`, `updated`
and 437 `[[links]]` in `kb-business` alone — and none of it is reachable
today except by reading.

This is the design for the rest: what a knowledge base looks like when it
is not being read note by note, and what can be asked of it.

## What the corpus actually is

Measured on `kb-business`, 2026-09-20: 86 notes, 516 KB of markdown, 437
wiki links. Of the notes, 47 are `type: source`, 12 `question`, 7 `hub`, 7
`concept`, 6 `case-study`, 1 `idea`; 80 are `status: draft`; 8 carry an
`(unverified)` marker. The four knowledge bases together are a couple of
megabytes.

Two consequences, and they decide most of the design:

- **No database, no index file, no background indexer.** Reading every
  note and parsing its frontmatter is a few hundred milliseconds of work
  on a phone. An index is built in memory when a knowledge base is opened
  and thrown away when it is not needed, which keeps the roadmap's
  invariant that the app holds no second copy of the notes.
- **Sources dominate.** More than half of `kb-business` is `sources/`.
  Any view that mixes them with the thinking notes is mostly a list of
  other people's articles, so type is not a nice-to-have facet — it is
  what makes the list readable at all.

## The index

`kb/NoteIndex.kt`, pure Kotlin beside the existing `KnowledgeBase`, one
pass over the repository producing per note: path, name, title, type,
status, tags, created, updated, outgoing link targets, heading list,
`(unverified)` count. Derived from those: backlinks (target -> notes
linking to it), unresolved links, orphans (no inbound link, excluding
`index.md` and hubs).

It is rebuilt after a sync and on first open, not cached to disk. It is
unit-testable on the JVM exactly like `KnowledgeBase`, which is why it
belongs in `kb/` and not in a view model.

## Views

The knowledge base screen becomes a small set of projections over that
index rather than a single folder list:

- **Folders** — today's view, unchanged. It is how the knowledge-base
  rule defines the layout and how a person thinks about where a note
  lives.
- **Recent** — notes by `updated`, newest first. This is the view that
  answers "what changed", which is the question after every sync, and it
  is the one most worth opening on a phone.
- **Types** — grouped by `type`, sources collapsed into one row. Turns
  86 notes into 6 groups, of which 5 are the ones you think with.
- **Tags** — every tag with its count; tapping one lists its notes.
- **Health** — the numbers the `sapiary` dashboard shows, per knowledge
  base: orphans, links that resolve to nothing, `(unverified)` figures,
  open questions, items waiting in `inbox/`. Each is a row that opens the
  list behind it, so a number is never a dead end.

## Queries

One search field, reachable from anywhere, searching one knowledge base or
all four. Free text matches title, headings and body, ranked in that
order. Filters are the frontmatter, not an invented vocabulary:

```
type:question status:draft tag:monetization
updated:>2026-09-01 kb:business
links:cpm-by-niche      # notes linking to that note
is:orphan               # nothing links to it
is:unverified           # has an (unverified) marker
```

Tokens combine with an implicit AND; anything that is not a filter is free
text. No boolean grammar, no parentheses: the filters above answer the
questions the conventions actually raise, and a query language is a poor
thing to type on a phone.

**Saved queries** are the same mechanism with a name. "Open questions",
"Drafts to review", "Unverified figures", "Changed this week" are stored
query strings, and so is every row of the Health view. One engine, several
doors.

## On the note itself

- **Backlinks**: a "Linked from (N)" section under the body. Links are the
  spine of these knowledge bases — every note links to its hub and the
  hub links back — and today only half of that spine is walkable.
- **Outline**: the note's headings, for jumping inside a long note.
- **Chips that do something**: the `type`, `status` and tag chips run the
  matching query instead of being decorative, which also settles the
  "decorative chips that look tappable" item in
  `.claude/rules/ux-areas-android.md`.
- **Unresolved links marked as they are rendered**, rather than only
  failing with a snackbar when tapped.

## Deliberately not built

- **A graph view.** 437 edges on a phone screen is a picture of a hairball.
  The neighbours of one note — what it links to, what links to it — is the
  part of the graph that answers questions, and it is a list.
- **A boolean query language**, for the reason above.
- **A disk cache or a second copy of note content.** The corpus is small
  enough to re-read, and a cache would be a second source of truth for
  notes that git already owns.
- **Editing from a query result.** Conflicts are already the hardest part
  of this app; a list that writes is a list that can write into a merging
  state.

## Order of work

Each step is useful on its own, and each one needs only the step before
it:

1. `kb/NoteIndex.kt` and its tests. Nothing visible yet.
2. Search over the index, with the filters above. Closes the standing
   backlog item.
3. Backlinks on the note screen — the cheapest large gain once the index
   exists.
4. Recent, Types and Tags views; chips become queries.
5. Health view.
6. Saved queries, including the Health rows.
