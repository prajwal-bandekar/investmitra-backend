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

## 2026-10-12
- ExpenseMitra module (`com.investmitra.expense`): categories, expenses, budgets, month summary and could-have-bought cards; default categories and wish products seeded on start.
- New config `expense.invest-symbol` (default RELIANCE).
- `ExpenseIntegrationTest` (6 tests).
- Review fix: renaming a custom expense category to a name the user already has now answers 409.
