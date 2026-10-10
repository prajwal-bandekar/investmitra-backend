# InvestMitra backend: instructions for AI sessions

1. Read `knowledge/PROJECT.md` (whole-project overview, how to run, ingest protocol), then the docs in `knowledge/` for this repo.
2. The sibling repo is `investmitra-frontend`; its own `knowledge/` folder documents it.
3. When the user says "ingest", follow the INGEST PROTOCOL in `knowledge/PROJECT.md` for this repo: diff the code against `knowledge/.ingest-state.json` (git history once commits exist), update only affected docs, append to `changelog.md`, record decisions (including rejected options) in `decisions.md`, update `open-issues.md`, then rewrite `.ingest-state.json`.
4. After any code change, update the affected knowledge files in the same session.
5. Never put secrets in code or docs. `ALPHAVANTAGE_API_KEY` and `DB_PASSWORD` come from environment variables.
6. Do not commit unless the user asks. Commits are authored by the user only: no AI attribution or Co-Authored-By lines.
