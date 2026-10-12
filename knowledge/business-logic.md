# Backend business logic

## Trading (`TradingService.trade`, one `@Transactional`)
1. Actor must own or advise the portfolio (`Portfolio.canBeManagedBy`), else 403.
2. Price = latest stored `StockPrice.close` for the stock (any age; stale prices still trade). No price gives 409 "No price available yet".
3. The **portfolio owner's** `AppUser` row is loaded with `findByIdForUpdate` (`PESSIMISTIC_WRITE`) so concurrent trades cannot overspend cash. Cash lives on the user, not the portfolio; an advisor's trade spends the client's cash.
4. **Buy:** sector lock check, cash check (`cash >= cost`, else 400 "Insufficient cash"), cash decreases, holding created or updated.
5. **Sell:** needs an existing holding with enough quantity (400 otherwise), books realised P&L on the trade, cash increases, holding deleted when quantity hits 0 (response `holding` is then null).
6. A `Trade` row records price, amount, `placedBy` (actor), `realizedPnl` (sells only), `executedAt`.

## Formulas (`PositionMath`)
- Prices keep 4 decimals, rupee amounts 2 (HALF_UP).
- **Average buy price** (weighted): `(oldQty*oldAvg + qty*price) / (oldQty + qty)`.
- **Realised P&L** on sell: `(sellPrice - avgBuyPrice) * qty`.
- **Selling never changes the average buy price**; only quantity drops.
- Unrealised P&L = `qty*lastClose - qty*avg`; percent = `pnl*100/invested`, null when invested is 0.
- Portfolio `totalPnl` (list) = unrealised + sum of realised P&L across its trades. Holdings with no stored price are valued at cost.
- `StockService.combinedPosition` merges the caller's holdings across all portfolios they manage with a weighted average (`invested/qty`).

## Sector-locked portfolios
`Portfolio.sector` is optional. Creation validates it against the names from `GET /api/sectors` (400 "Unknown sector"). Buys of stocks in another sector fail with 400. Sells are not restricted.

## Permissions
- Owner or assigned advisor can view, trade and read history (`requireManaged`).
- Only role ADVISOR may create a portfolio with `ownerId` other than self; the advisor is then stored as `Portfolio.advisor`. `listManagedBy` returns portfolios where the user is owner or advisor.
- Auth is a placeholder: client sends `X-User-Id`; nothing verifies it. `/api/admin/**` is unprotected. See open-issues.md.
- Client IDs are shown as `IM-0001` (`Dtos.clientId`); the frontend parses them back to the numeric user id.

## Market data and Alpha Vantage budget
- **Fetch once per day, serve from DB.** Search, sectors, tracked list and portfolios use stored prices only and never spend calls.
- **Date-wise storage:** one `StockPrice` per (stock, tradeDate), upserted by `StockPriceWriter`.
- **Freshness (`MarketCalendar.isFresh`):** fresh if `tradeDate >= lastCompletedTradingDay` OR `fetchedAt >= startOfToday (IST)`. `lastCompletedTradingDay` is yesterday if before 16:00 IST else today, then stepped back over Sat/Sun. Holidays are not modelled: on a holiday there is at most one extra call per stock, since a price fetched today counts as fresh.
- **Daily budget 24** (`alphavantage.daily-budget`; free tier is 25), **shared by the scheduled job, manual runs and on-demand quotes**. Used = `sum(JobRun.callsMade)` since IST midnight (`JobRunRepository.callsMadeSince`). Remaining = max(0, budget - used).
- **Pacing 1.5s** (`request-delay`) between calls, enforced with `lastCallAt` and `Pacer`, under a single `ReentrantLock` so the job and on-demand calls cannot overlap.
- **Rate-limit detection:** Alpha Vantage answers HTTP 200 for limits and errors. Body key `Information` or `Note` gives `RateLimited` (job stops); `Error Message` gives `SymbolNotFound`; empty `Global Quote {}` gives `SymbolNotFound`; unparseable body gives a generic `AlphaVantageException`.
- **Job (`refreshTrackedStocks`):** `tryLock` (409 if already running); fails fast with no API key; takes tracked stocks whose latest price is not fresh, **stalest first** (never fetched first, then oldest `fetchedAt`); stops at budget with note "N stocks left for next run"; status SUCCESS, PARTIAL (failures or stopped early) or FAILED. Scheduled 07:00 IST Mon-Fri.
- **On-demand (`getQuote`):** fresh stored price is served from DB; otherwise lock, re-check, then fetch if budget and key allow (JobRun trigger ON_DEMAND, 1 call). On failure or no budget it returns the last stored price with `fresh=false`, or 503 if there is none.
- **Symbol mapping:** `avSymbol` (e.g. `TCS.BSE`) is set only for tracked stocks; fallback `symbol + exchange-suffix`. NSE suffix is unconfirmed with Alpha Vantage, so BSE is used.
- **Catalog seeding:** `CatalogSeeder` loads the NIFTY 500 json once, then marks the 20 tracked symbols on every start: IT (TCS, INFY, WIPRO, HCLTECH, TECHM), banks (HDFCBANK, ICICIBANK, SBIN, KOTAKBANK, AXISBANK), energy (RELIANCE, ONGC, BPCL, IOC, GAIL), consumer (HINDUNILVR, ITC, NESTLEIND, BRITANNIA, DABUR). Sector values come from the catalog `Industry` field, so check real names via `/api/sectors`.

## Users
BCrypt hashes (`BCryptPasswordEncoder`), email lowercased and unique, login error identical for unknown email and wrong password. Starting cash from `trading.starting-cash` (1000000).

## Watchlist
One row per (user, stock). POST on an existing symbol updates the target price. `targetReached` is true when `close <= targetPrice` (the target is a buy-below price).

## ExpenseMitra
- Tracks real spending only; never moves money. Amounts are BigDecimal, 2 decimals HALF_UP, must be > 0.
- Ownership: every expense, budget and custom category belongs to one user. Other users' ids answer 404 (not 403) so ids can't be probed. Shared defaults are read-only (403 on edit).
- Defaults: want categories Food delivery, Eating out, Shopping, Entertainment; need categories Groceries, Travel, Rent, Bills, Education, Other.
- Budgets: per (category, month) plus an overall cap (category null). PUT upserts listed items only; null or 0 removes.
- Summary: month total, last month total, per-category spend (sorted by spend) with count and budget.
- **Could-have-bought cards**: for each `want` category with spend from 1 Jan to the end of the selected month (year to date), pick the priciest active `WishProduct` whose price <= spend; `count` = floor(spend / price). If spend is below the cheapest product, show that product with `progressPercent` = floor(spend * 100 / price). `investShares` = floor(spend / last close of `expense.invest-symbol`, default RELIANCE), null when no stored price. Uses stored prices only, so no Alpha Vantage calls.
