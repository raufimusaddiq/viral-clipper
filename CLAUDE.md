@AGENTS.md

## Claude Code specifics
- Subagents live in `.claude/agents/` (see `test-runner`).
- Slash commands live in `.claude/commands/`: `/research`, `/plan`, `/execute`, `/review`, `/test`, `/verify`.
- Test gate: `/test` delegates to the `test-runner` subagent (Haiku). Binary verdict: OPEN or BLOCKED.
- Persistent user/project memory lives under `~/.claude/projects/D--VibeCoding-ViralVideo/memory/` — not in this file.
