---
name: rotate-changelog
version: 1.3.0
description: >-
  Rotate changelog entries into yearly archives. Keeps
  last 30 versions in changelog.md, moves older entries
  to changelog/YYYY.md. Accepts a project name or no
  argument (defaults to all registered projects).
argument-hint: "[project-name]"
---

<!-- opencode counterpart: .opencode/command/rotate-changelog.md — the
     `/rotate-changelog` slash shim (skills aren't `/` commands in the
     opencode TUI). Keep in sync (global claude-opencode-sync rule). -->

Rotate changelog entries into yearly archives:
**$ARGUMENTS**

Rotation is fully automated by `headless_rotate_changelog.py`. For
each project over 30 entries it archives the overflow to
`changelog/YYYY.md`, bumps the patch version, prepends a rotation
changelog entry, commits, and pushes. The entry goes through the
`rotate_changelog._assemble` normalizer, so blank-line spacing is
always correct — **do not hand-edit the changelog or bump the
version yourself**. Hand-inserting that entry was the source of the
2026-06-13 corruption (a single blank line before the next header
and a preamble that grew every run).

## Resolve the argument

- **No argument** — rotate all registered projects (omit `--project`).
- **A project name** — pass it as `--project "$ARGUMENTS"`.

## Steps

1. **Preview (dry-run)** — the default writes nothing:
   ```
   uv run python scripts/headless_rotate_changelog.py [--project <name>]
   ```

2. **Apply** — rotate, bump, prepend the entry, commit, and push:
   ```
   uv run python scripts/headless_rotate_changelog.py --apply [--project <name>]
   ```

3. **Report** what was rotated — the script prints a per-project
   `archiving N -> vX.Y.Z` line and a final `Rotated / Skipped /
   Errors` tally.

## Notes

- Projects with no version file (see `NO_VERSION_FILE` in
  `project_meta.py`) are rotated without a bump or entry — the
  file-move still happens.
- Each rotation adds one entry, so a project settles at 31 entries
  and archives one per run.

## Skip conditions

The script skips automatically and reports them at the end:

- Projects with no `changelog.md` (or in `NO_CHANGELOG`).
- Projects with 30 or fewer versions (nothing to rotate).
- Projects whose path is absent on this machine.

## Safety

Each project only has entries moved between its own files plus a
patch bump — no cross-project writes. Dry-run is the default;
`--apply` is required to write, commit, and push. Everything is
reversible via git.
