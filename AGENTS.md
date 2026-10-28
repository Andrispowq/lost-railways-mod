# Project collaboration

- The owner manages commits, pushes, branches, and merges. Do not perform these operations unless explicitly instructed.
- Agent responsibilities are implementation, testing, investigation, and code quality. Leave fixes local for the owner to commit and push.
- The owner checks GitHub Actions status and supplies failures for local investigation. Do not poll GitHub unless explicitly asked. Phase B waits for the owner's confirmation that Phase A CI is green.
- Use Java conventions and IntelliJ IDEA-compatible tooling. Keep code clear, simple, and focused; introduce abstractions when a concrete need warrants them.
- Maintain appropriate unit, integration, and end-to-end coverage as features develop, and keep CI aligned with local checks.
- Follow the specification in docs/plan/lost_railways_spec.md and record technical decisions in docs/DECISIONS.md.
