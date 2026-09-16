---
name: committing
description: Git commit conventions, pull-first step, and post-change workflow for this Gradle project
version: 1.5
---

# Committing Guidelines

## Pull First — Before Any Work

**The first step of every session's workflow is `git pull`.** Run it
before reading code, planning, or editing — not just before pushing.

- Use `git -C <repo> pull` as a standalone Bash call (never chained).
- Do this even when the working tree looks clean: cleanliness says
  nothing about whether the remote has moved ahead from another
  machine.
- If the pull brings in commits, re-read anything you had already
  looked at before continuing — your view of the code is stale.

## Concurrent Sessions — Verify Before Every Commit

A second live Claude Code session working the same repo can commit
independently while this session is mid-task, and a broad `git add`
there can sweep up this session's uncommitted edits.

- **Run `git status --porcelain` immediately before every `git
  commit`.** If files you expected to still be uncommitted are
  missing, check `git log` for a commit you didn't make.
- **Stage by name, never `-A`/`.`.**
- If your edits were already committed elsewhere, diff to confirm the
  content is identical and do not re-commit or double-bump.

## Post-Change Workflow

After completing any task that involves code changes:

1. **Run the JVM tests**: `./gradlew testDebugUnitTest`. They cover
   note parsing, links, and git sync against real local repositories.
2. **Run the device tests when the change touches the app on a device**
   (screens, navigation, storage, anything JGit does at runtime):
   `./gradlew connectedDebugAndroidTest` with an emulator or phone
   connected. Start the emulator headless
   (`emulator -avd Pixel_8_API_36 -no-window`) so it takes no focus.
3. **Build**: `./gradlew assembleDebug` must pass.
4. **Update version and changelog**: follow `versioning.md`. Changes
   under `.claude/` and `CLAUDE.md` count too.
5. **Update README.md / CLAUDE.md** when user-facing behaviour or rules
   change.
6. **Commit**, then **push**.

**Config/tooling changes** (`.claude/`, `CLAUDE.md`): patch bump and
changelog entry; skip tests if no code changed.

**Pure tracking changes** (`backlog.md` checkboxes only): commit and
push, no tests and no version bump.

## When to Skip Tests

Skip tests when the staged diff consists only of Markdown
(`*.md`, `changelog.md`, `README.md`, `CLAUDE.md`), `.claude/` files,
or a version bump in `app/build.gradle.kts`. If any `.kt`, `.kts`,
`.xml` or `libs.versions.toml` file is in the diff, run them.

## Commit Message Format

Use conventional commit style:

```
<type>: <description>

[optional body]
```

## Types

- `feat` - New feature or functionality
- `fix` - Bug fix
- `refactor` - Code refactoring without changing behavior
- `docs` - Documentation changes
- `test` - Adding or updating tests
- `chore` - Maintenance tasks, dependency updates

## Best Practices

- Keep the subject line under 72 characters
- Use imperative mood ("Add feature" not "Added feature")
- **Default to subject-only.** Skip the body unless it adds
  something a reader wouldn't get from the subject, the diff,
  and the changelog entry.
- **One command per Bash call** — never chain git commands with `&&`,
  `;`, heredocs, or subshells.
- **Never put a git command in a parallel tool batch.** A hook that
  rejects one call cancels every other call in the same batch.
- **Use repeated `-m` flags for commit bodies**, not heredocs.
