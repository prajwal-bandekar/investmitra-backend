# Backend changelog

Newest at the bottom. Append one `## YYYY-MM-DD` section per ingest.

## 2026-10-06
- Project copied into `InvestMitra`; Wells Fargo naming removed; package `com.investmitra`; folders renamed.
- Entities replaced: Stock, StockPrice, AppUser, Portfolio, Holding, Trade, WatchlistItem, JobRun.
- Maven replaced by Gradle Kotlin DSL (wrapper 9.7.1); Java 25 Corretto; Spring Boot 4.1.1 (Jackson 3).

## 2026-10-07 to 2026-10-08
- Alpha Vantage integration: `AlphaVantageClient`, `GlobalQuoteResponse`, `MarketDataService`, `MarketCalendar`, `PriceFetchJob` (07:00 IST Mon-Fri), budget 24, 1.5s pacing, stalest-first refetch.
- `CatalogSeeder` loads NIFTY 500 and marks 20 tracked `.BSE` symbols.
- Trading engine: weighted average buy, realised P&L, pessimistic lock on owner, sector-locked portfolios, advisor flow.
- BCrypt passwords, login endpoint, `IM-0001` client IDs, admin price-job endpoints.
- 30 tests (integration, client, calendar, math) passing on H2.

## 2026-10-09
- Switched to MySQL 8.0; tables created by Hibernate `ddl-auto=update`.
- `GET /` redirects to the frontend (`app.frontend-url`).
- Ports 3000 and 8080 reserved on Windows.

## 2026-10-10
- Knowledge base created (this folder); initial `.ingest-state.json` written with sha256 hashes (repo has no commits).

## 2026-10-11
- Bhavcopy trial: `marketdata/bhavcopy` (client, parser, row, exceptions), preview endpoint `GET /api/bhavcopy/{exchange}/{date}`, `BhavcopyParserTest` (8 tests). No DB writes.

- 2026-10-11: Bhavcopy equity filter tightened: ISIN must start `INE` (drops ETFs/funds `INF`, government bonds `IN0`/`IN9`), and NSE rows must be series EQ/BE/BZ/SM/ST (drops corporate bonds). Live 2026-10-09: BSE 4,758 of 5,092 rows, NSE 3,036 of 3,687. BSE returns HTTP 200 with an HTML page on holidays; the client now treats any body that is not a ZIP or a `TradDt` CSV as "not published" (404).

- 2026-10-11: Daily bhavcopy import built. `BhavcopyImporter`, `BhavcopyJob` (19:30 IST + retries 20:00/20:30, `bhavcopy.*` properties), `POST /api/admin/bhavcopy/run`. `JobRun.source` column added. Hibernate jdbc batching enabled. Search now lists stocks with prices first. On-demand quote no longer calls Alpha Vantage for stocks with null avSymbol. 6 new tests (44 total).

- 2026-10-11: Security/business review fixes. HALF_UP on all setScale; trade quantity `@Max` 10M + addExact; signup duplicate-email race -> 409; size/positive/digits validation on names, password (6..72), portfolio name (60), search query (50), watchlist target price; catch-all 500 handler plus 400 handlers for type mismatch/unreadable body/param validation; `server.error.include-message/stacktrace=never`; trade idempotency via `clientRequestId` (new nullable `trade.client_request_id`, unique with portfolio_id); advisors can create portfolios only for existing clients, owners appoint advisors via `advisorId`; BUY blocked on prices older than 7 days. 18 new tests (62 total).

## 2026-10-12
- ExpenseMitra module (`com.investmitra.expense`): categories, expenses, budgets, month summary and could-have-bought cards; default categories and wish products seeded on start.
- New config `expense.invest-symbol` (default RELIANCE).
- `ExpenseIntegrationTest` (6 tests).
- Review fix: renaming a custom expense category to a name the user already has now answers 409.
- Merge of the stash with ExpenseMitra: bhavcopy decisions renumbered D15-D17 to D17-D19. All 68 tests pass (62 + 6 expense).

## 2026-10-12 (review fixes round 2)
- Bhavcopy: skip close <= 0 and rows for other dates (all-mismatch = not published); per-row parse tolerance with skipped count in notes (fail if >50% bad); BSE groups F, IF, G, GS excluded; preview moved to `/api/admin/bhavcopy/preview/{exchange}/{date}`; 20 MB download cap.
- Trading: 409 on null or <= 0 price.
- ApiExceptionHandler: only unique violations are 409 (else 400 "Invalid data"); Spring ErrorResponse keeps detail and headers.
- Expenses: archived category kept on edit; `spentOn` range; budget items <= 50; archived categories with spend in summary (`CategorySpend.archived`); budget save serialised per user; stale invest price gives no share count.
- New script `knowledge/scripts/cleanup-bse-bonds.sql` (not run). 84 tests pass.

## 2026-10-12 (deploy prep: Render + Neon)
- Added `org.postgresql:postgresql` (runtime), `application-prod.properties`, `Dockerfile`, `.dockerignore`, `render.yaml`, `knowledge/deploy.md`.
- `GET /api/health` (`HealthController`, no DB hit).
- `AccessKeyFilter` (`app.access-key` / `app.admin-key`, headers `X-Access-Key` / `X-Admin-Key`); CORS now allows these headers, `X-User-Id`, `Content-Type`, `Accept` and OPTIONS. `AccessKeyFilterTest` (5 tests).
- Postgres audit: wrapped nullable query params in `cast(... as string/long)` in `StockRepository.search` and `ExpenseRepository.find` (Postgres cannot infer the type of `:p is null` and fails on `lower(bytea)`). No other MySQL-specific SQL, `@Lob` or reserved words found.
- Gradle task `testPostgresMode` runs the suite on H2 in PostgreSQL mode. 89 tests (1 skipped) pass on both `test` and `testPostgresMode`.
- `PriceFetchJob` skips quietly when the Alpha Vantage key is empty (previously wrote a FAILED job run daily).

## 2026-10-13
- Security: Alpha Vantage error text can contain the API key ("We have detected your API key as ..."). New `marketdata/SecretMasker` replaces the configured key (when 8+ characters), `API key as <token>` and `apikey=<token>` with `****`. Applied in `AlphaVantageException` constructors (so notes, API errors and logs are clean), in `MarketDataService.finishRun` (stored notes) and in `JobRunView.of` (rows stored earlier are masked on read). The generic refresh error log prints the masked message only, not the stack trace.
- `StockRepository.search` ranks an exact symbol match first, then symbols starting with the query, then the rest, then has-price and symbol. JPQL `case` with `lower()`, keeps the `cast(:q as string)` null-safety; works on H2 (MySQL and PostgreSQL modes).
- Tests: `SecretMaskerTest` (4), search ranking test in `BhavcopyImporterTest`. 94 tests (1 skipped) pass on `test` and `testPostgresMode`.

## 2026-10-13 (Free to spend)
- New in `com.investmitra.expense`: entities `SalarySetting`, `ExtraIncome`, `Commitment` (+ `CommitmentKind`), their repositories, `PlanService`, `PlanController`, `PlanDtos`.
- New endpoints: `GET /api/plan`, `PUT /api/plan/salary`, `GET/POST /api/commitments`, `PUT/DELETE /api/commitments/{id}`, `GET/POST /api/extra-income`, `DELETE /api/extra-income/{id}`.
- Tables `salary_setting`, `extra_income`, `commitment` are created by `ddl-auto=update`; column names avoid reserved words.
- `PlanIntegrationTest` (12 tests). 106 tests (1 skipped) pass on `test` and `testPostgresMode`.

## 2026-10-13 (Free to spend: review fixes)
- `month` query params of `GET /api/plan` and `GET /api/extra-income` are checked strictly against the month pattern (400 "Month must look like 2026-10"). `ExpenseService` unchanged.
- Test cleanup in `ExpenseIntegrationTest` and `InvestMitraIntegrationTest` also clears commitments, extra income and salary rows before users.
- `PlanIntegrationTest`: MockMvc HTTP test, endMonth-equals-month, other-user isolation, malformed month tests. Unused imports removed from `SalarySetting`/`ExtraIncome`.
- `POST /api/commitments` and `POST /api/extra-income` now answer HTTP 201 (`@ResponseStatus`), not just the envelope statusCode.
