# Backend open issues

## Security
- Auth is the `X-User-Id` header only; anyone can impersonate any user. Needs real tokens (JWT or sessions) before going public.
- `/api/admin/**` (run job, quota, run log) is unprotected; also `GET /users/{id}` is open.
- DB root password is in plain `application.properties`; move to env var or secrets.
- The Alpha Vantage key was pasted in chat; consider rotating it.
- LAN access from another laptop deliberately not enabled (see D13).

## Data and infra
- Schema managed by `ddl-auto=update`; move to Flyway migrations.
- Demo users (`learner@`, `advisor@investmitra.dev`) exist only in the local DB; no seed code. A fresh DB needs them created again.
- Quota window mismatch: the backend counts calls since IST midnight, while Alpha Vantage resets daily on its own clock (the frontend toast claims 05:30 IST, i.e. 00:00 UTC). Around the boundary the local count can be wrong.
- Exchange holidays not modelled in `MarketCalendar` (costs at most one extra call per stock).
- NSE suffix unconfirmed with Alpha Vantage; `.BSE` used. Check whether `.NSE` works.
- First real price run against live Alpha Vantage not done yet.
- Possible unresolved question: DBeaver "papertrading" connection is an AWS RDS PostgreSQL unrelated to this app; is it still billing?

## ExpenseMitra
- Expense data is personal; real auth must replace `X-User-Id` before ExpenseMitra goes public.
- `WishProduct` prices are approximate seeds (Oct 2026); no admin UI to edit them yet.
- Not built yet from the PRD: recurring expenses, income entries, CSV export, SMS auto-capture (phase 2 Android app).

## Process
- Repo now has commits; next ingest should switch `.ingest-state.json` to git mode.
- Tests: 36 passing. No unit tests for `UserService` edge cases beyond login, nor for the cron wiring of `PriceFetchJob` (`pricejob.cron=-` disables it in tests).

## Review 2026-10-11 proposals (not implemented)
- Signup still lets anyone self-select ADVISOR (left so the demo advisor account keeps working); real fix needs admin approval.
- No portfolio close/delete endpoint exists, so "delete with holdings" cannot occur; if added, require zero holdings (or auto-sell) and keep trades.
- SELL on a very stale price is allowed; consider a warning flag in TradeView.
- Advisors have no trade limits on client cash and the client cannot revoke an advisor (no unlink endpoint).
- Sector `Other` (bhavcopy-created stocks) shows as one sector in `/api/sectors` with large counts; a real sector source is still missing.
- Holidays are not modelled, so a holiday counts toward the 7-day stale window only marginally (acceptable).
- Auth/JWT, X-User-Id trust, admin protection of endpoints, login rate limiting remain open by owner decision (see security-audit-2026-10-11.md).

## Process
- No git commits yet (ingest uses file hashes until the first commit).
- Tests: 62 passing. No unit tests for `UserService` edge cases beyond login, nor for the cron wiring of `PriceFetchJob` (`pricejob.cron=-` disables it in tests).

## Bhavcopy trial
- NSE may block non-browser clients (works today with browser-like User-Agent/Referer; may break).
- Terms of use for commercial use of exchange data unchecked.
- Not yet saved to the database or scheduled; Alpha Vantage job unchanged.

## Bhavcopy import
- Stocks created by the import have sector `Other`; real sectors missing, so sector-locked portfolios and sector filters treat them as one group.
- Price history grows ~1M rows/year (about 4,000 stocks x 250 days); consider an index review or archiving later.
- Retry guard counts any SUCCESS bhavcopy import with prices since IST midnight, even a manual backfill of an older date.
- Exchange holidays are not modelled; holiday days show as "no bhavcopy (holiday?)".

## Review round 2 follow-ups (proposals, not implemented)
- Advisor revoke endpoint (an owner cannot remove an advisor once appointed).
- Flag on a replayed trade response so clients can tell it was a replay.
- Stock search ordering uses a price-exists subquery; revisit cost at ~4,000 stocks and 1M+ prices.
- Consider `rewriteBatchedStatements=true` on the MySQL URL for faster bhavcopy batch inserts.
- Bond/InvIT rows from earlier BSE imports stay until `knowledge/scripts/cleanup-bse-bonds.sql` is reviewed and run by the user.

## Deployment (Render + Neon)
- Dockerfile and render.yaml are unverified (no Docker locally); first Render build is the test. Image base `eclipse-temurin:25-jre` and `amazoncorretto:25` tags assumed to exist.
- Neon free is about 0.5 GB and ~5k price rows/day will fill it: add a scheduled prune of `stock_price` older than N days (not implemented).
- `testPostgresMode` uses H2 PostgreSQL mode, not a real Postgres; verify on Neon (DDL via `ddl-auto=update`, queries) after the first deploy.
- Access key is a single shared secret sent from the browser; anyone with the frontend bundle can read it. Replace with JWT login later.
- Scheduled jobs only run while the free instance is awake; if the keep-alive ping fails at 19:30 IST the import is missed (run the backfill curl).

## Found in review 2026-10-13 (not fixed yet)
- **Security, admin-key bypass:** `AccessKeyFilter.isAdminPath` checks the raw `getRequestURI()`. `/api/admin;x/...` (matrix parameter) and `/api/%61dmin/...` (percent-encoded) skip the admin-key check but Spring still routes them to the admin controllers. Fix: check the normalised path Spring matches on, or reject `;` and `%` in `/api` paths.
- **Postgres risk:** `ExpenseRepository.find` casts `:categoryId` but not `:mode` (`:mode is null`). Verify History with no filters on Neon.
- **Import race:** bhavcopy import and Alpha Vantage writes don't share a lock; a duplicate (stock, date) row fails a whole 500-row batch and marks the import FAILED.
- **Retries after PARTIAL:** `BhavcopyImporter.importedSince` counts only SUCCESS, so the 20:00 and 20:30 retries re-download after a PARTIAL import (likely intended, to pick up NSE).
