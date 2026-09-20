# vademo Roadmap

The standing direction for development sessions. `backlog.md` holds the
*items*; this file holds the *ordering, gates, and invariants* that decide
which items to pick and which to refuse. Consult it **before selecting work**.

**Maintenance:** update when a direction-level decision lands, when a gate
clears, or when a phase completes. Keep it short — ordering and gates only;
rationale lives in the docs or ADRs it cites.

## Current phase: first version in daily use

1. **Use it on the real phone against GitHub** (backlog 2026.09.16).
   **Gate: no new features before a phone edit has round-tripped to the
   computer through GitHub.**
2. **Fix what daily use turns up**, starting with table readability
   (backlog 2026.09.16).
3. **Search, new notes from templates, health counts** — in the order use
   asks for them (backlog 2026.09.16).

## Standing invariants (apply to every session)

- Sync never overwrites either side. A conflict stops and asks.
- Notes stay plain markdown files in the knowledge-base layout; the app
  keeps no second copy or database of their content.
- `GitOnDeviceTest` must pass on a device after any JGit change.

## What NOT to do (recent refuted directions)

- **JGit 6.x or 7.x.** They need Java 11 / 17 APIs Android does not fully
  provide; 5.13.3 is verified on a Pixel 8 emulator running Android 16
  (2026-09-16).
