# InvestMitra knowledge base (read this first)

## What it is
InvestMitra is a paper-trading app for Indian students and freshers. Every user starts with ₹10,00,000 of pretend cash and trades NIFTY 500 stocks at the latest stored closing price. Two roles: USER (learner) and ADVISOR. Advisors can create and manage portfolios ("folios") for clients, identified by client ID `IM-0001` style. No real money, no real orders.

## Architecture

```
Browser
  React 18 CRA, :3000   (react-router v6, axios, plain CSS)
     |  HTTP JSON, header X-User-Id (placeholder auth)
     v
  Spring Boot 4.1.1 API, :8080, base path /api   (Java 25, Gradle)
     |  JPA / Hibernate (ddl-auto=update)         |  RestClient
     v                                            v
  MySQL 8, :3306, schema "investmitra"       Alpha Vantage GLOBAL_QUOTE
                                              (free key: 25/day, ~1 req/s)
  Scheduled job 07:00 IST Mon-Fri fills StockPrice once per day.
  Backend "/" redirects (302) to the frontend (app.frontend-url).
```

All endpoints answer with the envelope `{ data, message, statusCode }`.

## Run locally (Windows)
Prereqs: Corretto 25, MySQL 8 service `MySQL80` running (root / password in application.properties), Node.

Backend (PowerShell):
```
$env:JAVA_HOME = "$env:LOCALAPPDATA\Programs\Corretto\jdk25.0.4_10"
$env:GRADLE_USER_HOME = "$env:LOCALAPPDATA\Programs\GradleHome"
$env:ALPHAVANTAGE_API_KEY = "<your key>"      # never commit it
cd investmitra-backend
.\gradlew bootRun        # API on :8080; tests: .\gradlew test
```
Frontend:
```
cd investmitra-frontend
npm install
npm start                # :3000; API URL override: REACT_APP_API_URL
```
Without the API key the app still runs; the Price Desk job reports FAILED "ALPHAVANTAGE_API_KEY is not set" and trades need stored prices.

Ports 3000 and 8080 are reserved on this PC via `netsh ... excludedportrange` (Windows dynamic range is 1024-15000), see backend/decisions.md.

## Demo logins
`learner@investmitra.dev` (USER) and `advisor@investmitra.dev` (ADVISOR), password `demo1234`. These are rows in the local DB, not seeded by code. If missing, create them via the Sign up page or `POST /api/users`.

## Knowledge folder map
```
knowledge/
  README.md                 this file
  backend/   overview, components, business-logic, api, data-model, decisions, changelog, open-issues, .ingest-state.json
  frontend/  overview, components, business-logic, api, ui-design,  decisions, changelog, open-issues, .ingest-state.json
```
Other folders: `design-export/` (Claude Design export, visual source of truth for the frontend), `investmitra_ERD_old.jpg` (obsolete ERD from the pre-rewrite project, do not trust).
Start with `backend/business-logic.md` for rules, `backend/api.md` for contracts, `frontend/ui-design.md` for look and feel.

## INGEST PROTOCOL
Triggered when the user says "ingest", "ingest backend" or "ingest frontend" (no qualifier means both).

State file: `knowledge/<repo>/.ingest-state.json`.
- If the repo has git commits: `{ "mode": "git", "lastCommit": "<hash>" }`. Diff with `git diff <lastCommit>..HEAD` plus uncommitted changes (`git status`).
- If the repo has NO commits (currently the case): `{ "mode": "hashes", "generatedAt": "<ISO timestamp>", "files": { "<relative path>": "<sha256>" } }`. Recompute sha256 of the same file set (src/**, build.gradle.kts or package.json, application.properties; exclude node_modules, build, data JSON) and compare. Added, removed and changed paths are the diff.

Steps:
1. Compute the diff against the state file. Read only the changed files.
2. Update only the affected sections in components.md, api.md, data-model.md (backend) or ui-design.md (frontend), and business-logic.md. Keep names and paths verified against code.
3. Append a dated entry to changelog.md (newest at the bottom, format `## YYYY-MM-DD`).
4. Add or update decisions.md entries for decisions made in the conversation, including rejected alternatives (fields: date, decision, why, alternatives rejected, status).
5. Update open-issues.md: add new gaps, strike or remove resolved ones.
6. Rewrite `.ingest-state.json` (new commit hash, or new timestamp and fresh hashes). File sets, from each repo root, hashed with `sha256sum` (Git Bash):
   - backend: `find src build.gradle.kts -type f -not -path "src/main/resources/data/*" | sort` (includes application.properties and test fixtures)
   - frontend: `find src package.json -type f | sort`
   Keep the JSON shape: `repo`, `mode`, `lastCommit`, `generatedAt`, `files{path: sha256}`.
7. Report a short list of what changed in the knowledge base.

Conventions: concise, scannable, no em-dashes, money is BigDecimal (backend) and formatted with `format.js` (frontend). Never write secrets into the docs.
