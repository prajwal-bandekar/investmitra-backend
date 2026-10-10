# Backend data model

MySQL 8 schema `investmitra`, created by Hibernate (`ddl-auto=update`, no migrations). All ids are `Long` IDENTITY. Money is `BigDecimal` (amounts scale 2, prices scale 4). Entities live in `com.investmitra.entity`.

| Entity (table) | Columns | Constraints / relations |
|---|---|---|
| **AppUser** (`app_user`) | id, name, email, passwordHash (BCrypt), role (`USER`/`ADVISOR`, string), cashBalance (19,2) | email unique; all not null |
| **Stock** (`stock`) | id, symbol (NSE, e.g. TCS), name, sector, isin, avSymbol (e.g. TCS.BSE) | symbol unique; `avSymbol` null means untracked (`isTracked()`) |
| **StockPrice** (`stock_price`) | id, stock_id, trade_date, open_price, high_price, low_price, close_price (not null), previousClose, volume, fetchedAt | unique (stock_id, trade_date); ManyToOne Stock |
| **Portfolio** (`portfolio`) | id, name, owner_id, advisor_id (nullable), sector (nullable), createdOn | owner ManyToOne AppUser (required); advisor ManyToOne AppUser; `canBeManagedBy(userId)` |
| **Holding** (`holding`) | id, portfolio_id, stock_id, quantity (long), avgBuyPrice (19,4) | unique (portfolio_id, stock_id); row deleted at quantity 0 |
| **Trade** (`trade`) | id, portfolio_id, stock_id, placedBy_id, side (`BUY`/`SELL`), quantity, price (19,4), amount (19,2), realizedPnl (nullable, sells only), executedAt (Instant) | immutable log |
| **WatchlistItem** (`watchlist_item`) | id, user_id, stock_id, targetPrice (19,4, nullable), addedOn | unique (user_id, stock_id) |
| **JobRun** (`job_run`) | id, run_trigger (`SCHEDULED`/`MANUAL`/`ON_DEMAND`), startedAt, finishedAt, status (`RUNNING`/`SUCCESS`/`PARTIAL`/`FAILED`), callsMade, pricesSaved, failures, notes (2000) | audit; sum of callsMade since IST midnight is the quota used |

## Relationships
```
AppUser 1--* Portfolio (owner)      AppUser 1--* Portfolio (advisor)
Portfolio 1--* Holding *--1 Stock   Portfolio 1--* Trade *--1 Stock
Trade *--1 AppUser (placedBy)       AppUser 1--* WatchlistItem *--1 Stock
Stock 1--* StockPrice
```
Notes: cash is per user, not per portfolio. Table names are Spring's default snake_case of the entity names (not independently checked in MySQL). The column is `run_trigger` because `trigger` is a reserved word. Enums: `Role`, `TradeSide`, `FetchTrigger`, `JobStatus`.
Seed data: `Stock` rows come from `nse-500-stocks.json` on first start. Users are not seeded by code.
