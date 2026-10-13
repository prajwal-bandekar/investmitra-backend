# Backend data model

MySQL 8 schema `investmitra`, created by Hibernate (`ddl-auto=update`, no migrations). All ids are `Long` IDENTITY. Money is `BigDecimal` (amounts scale 2, prices scale 4). Entities live in `com.investmitra.entity`.

| Entity (table) | Columns | Constraints / relations |
|---|---|---|
| **AppUser** (`app_user`) | id, name, email, passwordHash (BCrypt), role (`USER`/`ADVISOR`, string), cashBalance (19,2) | email unique; all not null |
| **Stock** (`stock`) | id, symbol (NSE, e.g. TCS), name, sector, isin, avSymbol (e.g. TCS.BSE) | symbol unique; `avSymbol` null means not fetched from Alpha Vantage (`isTracked()`); such stocks still get prices from the bhavcopy import. Stocks created by the import have sector `Other` |
| **StockPrice** (`stock_price`) | id, stock_id, trade_date, open_price, high_price, low_price, close_price (not null), previousClose, volume, fetchedAt | unique (stock_id, trade_date); ManyToOne Stock |
| **Portfolio** (`portfolio`) | id, name, owner_id, advisor_id (nullable), sector (nullable), createdOn | owner ManyToOne AppUser (required); advisor ManyToOne AppUser; `canBeManagedBy(userId)` |
| **Holding** (`holding`) | id, portfolio_id, stock_id, quantity (long), avgBuyPrice (19,4) | unique (portfolio_id, stock_id); row deleted at quantity 0 |
| **Trade** (`trade`) | id, portfolio_id, stock_id, placedBy_id, side (`BUY`/`SELL`), quantity, price (19,4), amount (19,2), realizedPnl (nullable, sells only), executedAt (Instant), clientRequestId (nullable, max 64) | immutable log; unique (portfolio_id, client_request_id) |
| **WatchlistItem** (`watchlist_item`) | id, user_id, stock_id, targetPrice (19,4, nullable), addedOn | unique (user_id, stock_id) |
| **JobRun** (`job_run`) | id, run_trigger (`SCHEDULED`/`MANUAL`/`ON_DEMAND`), startedAt, finishedAt, status (`RUNNING`/`SUCCESS`/`PARTIAL`/`FAILED`), callsMade, source (`ALPHA_VANTAGE`/`BHAVCOPY`, null on old rows = Alpha Vantage), pricesSaved, failures, notes (2000) | audit; sum of callsMade since IST midnight is the quota used |
| **ExpenseCategory** (`expense_category`) | id, user_id (null = shared default), name, icon, want, archived | 10 defaults seeded by `ExpenseSeeder` |
| **Expense** (`expense`) | id, user_id, category_id, amount (19,2), spentOn, paymentMode (`UPI`/`CASH`/`CARD`/`NETBANKING`/`OTHER`), source (`MANUAL`/`SMS`), note, createdAt | index (user_id, spentOn) |
| **Budget** (`budget`) | id, user_id, category_id (null = overall cap), month (column `budget_month`, `YYYY-MM`; `month` is reserved in H2), limitAmount (19,2) | unique (user_id, category_id, budget_month); MySQL allows duplicate NULLs, so the service upserts the overall row itself |
| **SalarySetting** (`salary_setting`) | id, user_id, effectiveMonth (column `effective_month`, `YYYY-MM`), amount (19,2) | unique (user_id, effective_month); salary for month M = row with the greatest effective_month <= M |
| **ExtraIncome** (`extra_income`) | id, user_id, incomeMonth (column `income_month`), amount (19,2), label (60), createdAt | one-off income in one month |
| **Commitment** (`commitment`) | id, user_id, name (60), kind (column `commitment_kind`: `EMI`/`BNPL`/`INSURANCE`/`SUBSCRIPTION`/`RENT`/`SAVINGS`/`OTHER`), amount (19,2), dueDay (1-31), startMonth (`start_month`), endMonth (`end_month`, null = no end), createdAt | active in M when start_month <= M and (end_month null or >= M); column names avoid reserved words |
| **WishProduct** (`wish_product`) | id, name (unique), price (19,2), image (illustration key), active | 9 seeded, approximate Indian prices; edit rows to update |

## Relationships
```
AppUser 1--* Portfolio (owner)      AppUser 1--* Portfolio (advisor)
Portfolio 1--* Holding *--1 Stock   Portfolio 1--* Trade *--1 Stock
Trade *--1 AppUser (placedBy)       AppUser 1--* WatchlistItem *--1 Stock
Stock 1--* StockPrice
AppUser 1--* Expense *--1 ExpenseCategory   AppUser 1--* Budget *--0..1 ExpenseCategory
AppUser 1--* SalarySetting / ExtraIncome / Commitment
```
Notes: cash is per user, not per portfolio. Table names are Spring's default snake_case of the entity names (not independently checked in MySQL). The column is `run_trigger` because `trigger` is a reserved word. Enums: `Role`, `TradeSide`, `FetchTrigger`, `JobStatus`.
Seed data: `Stock` rows come from `nse-500-stocks.json` on first start. Users are not seeded by code.
