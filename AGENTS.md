# vademo instructions for Codex and OpenCode

This file adapts the repository's Claude Code guidance for Codex and
OpenCode. `CLAUDE.md` and the files under `.claude/` remain the source of
truth: read them before acting, and do not keep a parallel copy of their
procedures here.

Read, in this order:

1. `CLAUDE.md` — what the app does, build commands, architecture, and the
   decisions that are easy to undo by accident.
2. `.claude/rules/committing.md` and `.claude/rules/versioning.md` — test,
   version and commit workflow.
3. `roadmap.md`, then `backlog.md` — what to work on next.

## Skills

The skills live in `.claude/skills/` and are linked for discovery from
`.agents/skills/` (Codex, invoked as `$name`) and `.opencode/skills/`
(OpenCode, with `/name` shims in `.opencode/command/`).

| Skill | Use it to |
|-------|-----------|
| `review-ux-android` | Review the app's screens for usability issues. |
| `rotate-changelog` | Move old changelog entries into yearly archives. |
