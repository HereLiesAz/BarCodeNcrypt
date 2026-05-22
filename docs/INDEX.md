# docs/

Active documentation for v1.0:

- [`crypto/wire-format.md`](crypto/wire-format.md) — canonical on-wire reference for
  the `~BCEv5~` envelope, including the test vector unit tests assert against.
- [`superpowers/plans/2026-05-21-plan-{1..5}-*.md`](superpowers/plans/) — original
  per-plan design documents. The implementation is largely complete; treat these as
  the rationale behind the current code, not as a roadmap.
- [`superpowers/specs/2026-05-21-remediation-design.md`](superpowers/specs/2026-05-21-remediation-design.md)
  — the parent design spec that the five plans were derived from.

Out-of-tree references (kept in conversation memory but not committed):
- The path-to-production roadmap (Plans 0–6) that drove the green-build push.
- A reference for the AzNavRail library (DSL, design constraints, version 9.x bump).

If you're new to the codebase, start with [`../README.md`](../README.md) and then read
`crypto/wire-format.md` before touching anything in `app/src/main/.../crypto/`.
