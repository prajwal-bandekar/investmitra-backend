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
- Only role ADVISOR may create a portfolio with `ownerId` other than self, and only for a user who already has a portfolio advised by them (a client). The first link is made by the client: they create their own portfolio with `advisorId` set to the advisor (must have role ADVISOR). The advisor is stored as `Portfolio.advisor`. `listManagedBy` returns portfolios where the user is owner or advisor.
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

## Free to spend (`PlanService`, 2026-10-13)
- Months are calendar months (YYYY-MM, IST). No rollover: each month starts fresh from its own income and commitments.
- Salary for month M = the `SalarySetting` with the greatest `effectiveMonth <= M`; none means `salary` null and income 0. `PUT /plan/salary` upserts the row for the given month, so earlier months keep their old salary.
- `totalIncome` = salary (or 0) + sum of that month's `ExtraIncome`.
- A `Commitment` is active in M when `startMonth <= M` and (`endMonth` null or `endMonth >= M`). `endMonth < startMonth` gives 400. `SAVINGS` counts in `savingsTotal` (set aside), everything else in `committedTotal`.
- `freeToSpend` = totalIncome - committedTotal - savingsTotal (may be negative); `spent` = month's expenses; `remaining` = freeToSpend - spent (may be negative).
- `daysLeft`: current month = days from today to month end inclusive; future month = days in month; past month = 0. `dailyAllowance` = remaining / daysLeft rounded down to whole rupees, null when daysLeft is 0 or remaining <= 0.
- `committedPercent` = round(committedTotal * 100 / totalIncome), HALF_UP, null when totalIncome is 0 (savings not included).
- `endingSoon`: active commitments with an `endMonth <= month + 2`. `monthsLeft` = months from the requested/current month to `endMonth` inclusive (0 once ended).

## Bhavcopy daily import
- `BhavcopyImporter.importDay(date, trigger)`: fetches NSE (primary) and BSE; BSE only contributes companies whose ISIN is not in NSE's file. Equity rows only (`isEquity()`). Rows whose trade date differs from the requested date, or whose close is <= 0, are skipped and counted in the notes; a file whose rows are all for another date counts as "not published" (note "file is for <date>"). Malformed CSV lines are skipped and counted by the parser (`ParsedRows.skipped()`); the parse fails only if more than half the data rows are bad.
- Stock matching: by ISIN first; else by symbol (case-insensitive) if that stock has no ISIN yet (ISIN is then set); else a new Stock is created (symbol = ticker, name = FinInstrmNm, sector `Other`, avSymbol null). A BSE-only ticker already used by a different ISIN is skipped and counted in the notes.
- Prices upserted per (stock, tradeDate) in batches of 500, one transaction per batch; re-running a day updates, never duplicates. Prices are read from `StockPrice` regardless of avSymbol.
- Outcome: both exchanges "not published" = holiday: status SUCCESS, `published=false`, note "no bhavcopy (holiday?)". One exchange missing or failing = PARTIAL (the other is still imported). Both failing with a real error = FAILED. A `JobRun` (source BHAVCOPY) is written for every run. One import at a time (409 otherwise).
- `BhavcopyJob`: cron `bhavcopy.cron` (19:30 IST Mon-Fri) plus retries `bhavcopy.retry-cron` (20:00 and 20:30); retries skip if a SUCCESS import with prices saved already ran today. Skips non-weekdays. `bhavcopy.enabled=false` removes the job bean.
- Alpha Vantage: `getQuote` returns the stored price (or 503 "No price yet") for stocks with null avSymbol and never calls the API. The scheduled AV job only fetches tracked stocks whose price is not fresh, so once bhavcopy has stored the last trading day it makes 0 calls.
- Search order: stocks with any stored price first, then by symbol.

## Bhavcopy (preview)
- `marketdata/bhavcopy`: `BhavcopyClient` downloads the exchange's daily end-of-day file (BSE plain CSV, NSE ZIP with one CSV) with browser-like headers; `BhavcopyParser` unzips if needed and reads columns by header name (required: TradDt, Src, FinInstrmTp, TckrSymb, ClsPric). Rows with a blank close are skipped; `equities()` keeps `FinInstrmTp == STK`.
- `BhavcopyController` preview stores nothing; the import above does the saving.

### Bhavcopy: what counts as a share
`BhavcopyRow.isEquity()`: instrument type `STK`, ISIN starting `INE`, and on NSE a series of EQ, BE, BZ, SM or ST. On BSE the groups F (bonds/NCDs), IF (InvITs), G and GS (government securities) are excluded; verified on the real 2026-10-09 file (observed INE groups B,X,A,F,XT,M,T,MT,Z,IF,P,MS,ZP,TS,R). `BhavcopyClient` rejects downloads over 20 MB. A download that is neither a ZIP nor a CSV starting with `TradDt` is treated as "no file for that date" (BSE serves an HTML page with HTTP 200 on holidays).

## Trade safeguards (2026-10-11 review)
- Quantity 1..10,000,000 per order; position sums use `Math.addExact` (overflow -> 400).
- Optional `clientRequestId` makes a trade idempotent per portfolio (checked after the owner row lock, so replays serialise). Replays return the original trade, current holding and cash.
- BUY is blocked (409) when the latest stored price is more than 7 calendar days (about 5 trading days) older than the last completed trading day (delisted or dead feed). SELL is still allowed at the last price so users are not trapped. Weekend/holiday trades use the last close (accepted).
- All `setScale(2)` use HALF_UP.
- Concurrent owner + advisor trades cannot overdraw: both lock the owner's AppUser row.

## Review fixes 2026-10-12 (expense and trading)
- Trading rejects a latest price that is null or <= 0 with 409 (before idempotent replay, both sides).
- Expense update keeps the expense's existing category even if archived; a changed category must be usable. `spentOn` must be in [today-2y, today+1d] IST (an unchanged date on update is not rechecked).
- Month summary lists archived categories that have spend (`archived=true`), and could-have-bought cards count their year-to-date spend.
- `saveBudgets` locks the user row (`findByIdForUpdate`) and finds the overall budget with an explicit null-category lookup, so concurrent saves cannot create duplicates (MySQL unique keys do not constrain NULL).
- The could-have-bought share count is omitted (null investSymbol/investShares) when the invest-symbol price is older than `TradingService.MAX_PRICE_AGE_DAYS`.
