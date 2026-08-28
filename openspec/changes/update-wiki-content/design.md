## Context

The repository keeps `WIKI_CONTENT.md` as a manually maintained publication source for the GitHub Wiki. The file predates the merged FIFO coverage preflight feature and contains older release, folder, workflow, and troubleshooting details. See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**

- Make the wiki source accurately describe the current CLI, API, GUI, encrypted database, FIFO calculation, and coverage preflight workflows.
- Preserve a clear distinction between repository source content and the user's manual GitHub Wiki publishing step.
- Keep the update readable as standalone project documentation for a new user.

**Non-Goals:**

- Do not publish to GitHub Wiki automatically.
- Do not change runtime code, interfaces, database schema, dependencies, or generated reports.
- Do not claim legal, financial, or tax guarantees beyond the existing project disclaimer.

## Decisions

- Use the current repository files, README, merged FIFO coverage implementation, and OpenSpec archive as the factual sources.
- Update the existing document in place rather than creating a second wiki draft, so the manual transfer workflow has one canonical source.
- Keep command examples executable from the project root and include both normal tax calculation and coverage preflight examples.
- Describe security and data handling conservatively, matching the actual SQLCipher configuration and local-only workflow.
- Organize content around setup, workflows, coverage preflight, calculation logic, outputs, security, best practices, and troubleshooting; retain useful existing PIT-38 context where accurate.

## Risks / Trade-offs

- Documentation can become stale after future code changes → include a task to verify commands and compare documented paths/options with the current repository.
- Manual wiki copying can omit updates → state `WIKI_CONTENT.md` as the canonical source and include a final review checklist.
- Financial documentation may be overinterpreted as advice → retain an explicit educational-purpose disclaimer and avoid promising filing correctness.

## Migration Plan

1. Update and review `WIKI_CONTENT.md` on the implementation branch.
2. Run documented command smoke checks and review links/paths.
3. Merge the change through a pull request.
4. Manually transfer the reviewed content into the GitHub Wiki.
5. Roll back by reverting the documentation commit if the wiki source is inaccurate.
