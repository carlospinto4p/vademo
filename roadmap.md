# vademo Roadmap

The standing direction for development sessions. `backlog.md` holds the
*items*; this file holds the *ordering, gates, and invariants* that decide
which items to pick and which to refuse. Consult it **before selecting work**.

**Maintenance:** update when a direction-level decision lands, when a gate
clears, or when a phase completes. Keep it short — ordering and gates only;
rationale lives in the docs or ADRs it cites.

## Current phase: first version in daily use

1. **Fix what daily use turns up**, starting with table readability
   (backlog 2026.09.16). The phone-to-GitHub round trip works
   (checked 2026-11-15), so the gate on new features is cleared.
2. **Search, views and health counts** — designed in
   `docs/kb-views-and-queries.md`. Done: all six steps of its order of
   work shipped by v0.18.0 (Health tab v0.17.0, saved searches v0.18.0).
   New notes from templates (backlog 2026.09.16) is still open.
3. **Diagrams**: shown since v0.6.0, edited on the phone since v0.7.0, in
   tyfold's format so agents (with tyfold's graph tool) and the phone
   write the same files. By v0.10.0 the phone does what tyfold's diagram
   pane does, arrows kept off and bent round other boxes included, except
   links from a box to a terminal session, which need tyfold's desktop.
   Since v0.11.0 it also routes an arrow running backwards round boxes,
   which tyfold does not yet (filed in tyfold's own backlog). The phone
   checks are in backlog 2026.11.15 (diagrams, and editing diagrams on
   the phone).

## Standing invariants (apply to every session)

- Sync never overwrites either side. A conflict stops and asks.
- Notes stay plain markdown files in the knowledge-base layout; the app
  keeps no second copy or database of their content.
- `GitOnDeviceTest` must pass on a device after any JGit change.

## What NOT to do (recent refuted directions)

- **JGit 6.x or 7.x.** They need Java 11 / 17 APIs Android does not fully
  provide; 5.13.3 is verified on a Pixel 8 emulator running Android 16
  (2026-09-16).
