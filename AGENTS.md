# AGENTS.md — Rules and Limitations for Working in This Project

Project: **IBKR Tax Assistant** — Python tool that turns Interactive Brokers data into Polish PIT-38 tax reports (FIFO, NBP FX rates, encrypted local DB).

## 1. Git workflow
- **Never work directly on `main`/`master`.** Check `git branch --show-current` before editing or committing; if on `main`, create a feature branch first (`feat/...`, `fix/...`, `docs/...`).
- Never force-push or rewrite shared history. Do not push or open PRs unless asked.
- CI (`.github/workflows/python-app.yml`) runs on PRs to `main`; Markdown-only PRs skip the Python checks. Do not turn that job into a job-level `if:` (it breaks required checks).
- Commit messages end with the trailer: `Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>`.

### Branches and pull requests
- **One task, one branch, one PR.** Before starting a task, run `git fetch` and branch from the current `origin/main`. Do not keep adding unrelated tasks to a long-lived branch; once a task is done, propose a PR and start the next task from `main`.
- Keep each PR reviewable: aim for roughly 1,500 changed lines or fewer. Split larger work into separate tasks up front rather than splitting a finished branch afterwards.
- Every PR must pass CI on its own. CI runs only on PRs targeting `main`, so a PR based on another feature branch gets no checks until it is retargeted.
- **Avoid stacked PRs** (a PR whose base is another unmerged PR). If one is unavoidable:
  - merge bottom-up, and only after the PR below it is in `main`;
  - merge every PR in the stack with **"Create a merge commit"**, not "Squash and merge": squashing a base PR makes the next PR conflict (add/add) with `main`.
- If a base PR was already squash-merged, fix the next branch without rebasing or force-pushing: confirm `git diff <old-base-branch> origin/main` is empty, run `git merge origin/main` on the next branch, keep that branch's version of each conflicted file, and check that the merged tree equals the previously tested tree (`git diff --cached <tested-commit>` is empty) before committing and pushing.
- Delete a branch after its PR is merged.

### Agents: pushing and PRs
- Before promising to push or open a PR, check that it is possible (`gh auth status`, or `git push --dry-run`). If it is not, say so plainly and give the user the exact commands to run.
- A PR exists only when the push and `gh pr create` succeeded. Confirm it (`gh pr view` or the PR URL) before reporting it; never describe a PR as ready, opened, or "prepared" when it has not been created.

## 2. Security and privacy (hard limits)
- **Never read, print, log, or commit secrets or personal data**: `.env` (`SQLCIPHER_KEY`, `DATABASE_PATH`, `IB_*`), `data/`, `data.bkp/`, `db/`, `output/`, `*.csv`, `*.db`, `snapshot_*.json`, `manual_history.csv`, `manual_fixes.csv`. They are git-ignored; keep them that way.
- Do not share code or data with third-party services.
- The DB is encrypted with **SQLCipher** (`sqlcipher3`); plaintext SQLite fallback is forbidden. Key rotation only via `python tools/change_key.py`; the key is never printed. See [DOCS_SECURITY.md](DOCS_SECURITY.md).
- IBKR connectors (`src/ib_connector.py`, `src/ib_web_connector.py`) are **read-only**: never place, modify, or cancel orders. Keep `IB_WEB_VERIFY_SSL` and timeouts configurable; the local CPGW listens on `127.0.0.1` only.
- Do not commit secrets, real trade data, or generated reports.

## 2a. Tax-logic invariants (do not change without an OpenSpec change)
- **FIFO** matching (queue-based), per instrument identity.
- **NBP FX rates**: strict **T-1** (previous business day) rule; monthly batch caching (cache in `cache/`, git-ignored).
- Withholding tax is linked to its dividends; split/corporate-action handling must stay covered by tests.
- Parser normalizes dates to ISO, strips metadata/total rows; `transactions` table is the single source of truth.
- Results must be reproducible and covered by tests — never "fix" numbers by editing fixtures to match output.

## 3. Architecture
```
[IBKR CSV / Flex / Web API] -> parser / ib_normalizer -> SQLCipher DB -> FIFO core -> reporters (PDF / Excel / GUI)
```
- `main.py` CLI entry; `src/` logic modules; `gui/` dashboard/API; `tools/` maintenance scripts; `tests/` pytest suite; `openspec/` specs and changes.
- Keep module boundaries; put new logic in `src/`, not `main.py`.
- Windows is the primary dev OS (PowerShell); keep code cross-platform and use `pathlib`/`os.path`.

## 4. Code style and quality
- Python **3.12**. Dependencies in `requirements.txt`; add new ones only when necessary.
- Formatting: **black 26.5.1** (`black --check .` must pass). Pre-commit also enforces trailing whitespace, EOF newline, YAML validity, no large files.
- Lint: **flake8**, blocking set `E9,F63,F7,F82`, max line length 127.
- Make surgical changes; no unrelated refactors. Comment only non-obvious code.
- The repo root holds only `main.py` as a Python entry point; put helper scripts in `tools/`.

## 5. Testing — before declaring done
```powershell
black --check .
flake8 . --count --select=E9,F63,F7,F82 --show-source --statistics
pytest -v
```
- Add or update tests for any behavior change (`tests/`). Tests must not use real credentials, the real DB, or live network (mock NBP/IBKR).
- Docs-only changes need no test run.

## 6. Spec-driven workflow (OpenSpec)
- Non-trivial features/behavior changes go through OpenSpec (`openspec/`, schema `spec-driven`) using the skills in `.github/skills/` (`openspec-propose`, `-apply-change`, `-update-change`, `-sync-specs`, `-archive-change`, `-explore`).
- Specs live in `openspec/specs/` (calculation-reliability, database-security, fifo-coverage-check, gui-api, ib-web-api, pit-38-filling, web-portal, desktop-dashboard). Keep code, specs, and docs consistent; update [README.md](README.md) / [SPECIFICATION.md](SPECIFICATION.md) when behavior changes.
- Explore/propose/update modes never edit code.

## 7. Agent behavior
- Be concise; ask the user when a requirement is ambiguous or affects tax correctness.
- Prefer built-in search/view tools; avoid destructive commands.
- Do not create planning/notes markdown files in the repo unless requested.
- Clean up temporary files you create.
