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
