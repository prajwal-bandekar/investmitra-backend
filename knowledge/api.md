# Backend API

Base URL `http://localhost:8080/api`. Every response: `{ "data": <T>, "message": "...", "statusCode": 200 }`. Errors use the same envelope with `data: null`.
Caller identity: header `X-User-Id: <numeric user id>` (placeholder auth). Error mapping: ApiException status; validation 400; missing header 401; malformed JSON 400; "Wrong email or password" 401.
DTO shapes are in `web/dto/Dtos.java` and `web/dto/PriceView.java`.

## Users
| Method, path | Auth | Request | Response `data` |
|---|---|---|---|
| POST `/users` (201) | none | `{name, email, password (6-100), role: USER or ADVISOR}` | `UserView {id, clientId, name, email, role, cashBalance}`; 409 if email exists |
| POST `/users/login` | none | `{email, password}` | `UserView`; 401 on failure |
| GET `/users/{id}` | none | | `UserView` (frontend uses it to refresh cash) |

## Stocks
| Method, path | Header | Params | Response |
|---|---|---|---|
| GET `/stocks` | X-User-Id optional | `query`, `sector`, `page=0`, `size=20` (max 50) | Spring `Page<StockView>`; tracked stocks sort first |
| GET `/stocks/tracked` | none | | `StockView[]` (20 tracked, by symbol) |
| GET `/stocks/{symbol}` | X-User-Id optional | | `StockView` |
| GET `/stocks/{symbol}/quote` | none | | `PriceView`; may spend 1 Alpha Vantage call; message says fetched, from DB, or stale; 404 unknown, 503 no price and no calls |
| GET `/sectors` | none | | `SectorView[] {sector, stocks, trackedStocks}` |

`StockView {symbol, name, sector, isin, tracked, price: PriceView or null, myPosition: PositionView or null}`.
`PriceView {asOf, open, high, low, close, previousClose, change, changePercent, volume, fetchedAt, source: DATABASE or ALPHA_VANTAGE, fresh}`.
`PositionView {quantity, avgBuyPrice, invested, currentValue, unrealizedPnl, unrealizedPnlPercent}`.

## Portfolios (all need X-User-Id)
| Method, path | Request | Response |
|---|---|---|
| POST `/portfolios` (201) | `{name <= 60, sector? (known sector), ownerId?, advisorId?}`; ownerId only for advisors who already advise that user (403 otherwise); advisorId lets an owner appoint an advisor on their own portfolio | `PortfolioSummary` |
| GET `/portfolios` | | `PortfolioSummary[]` owned or advised, with `currentValue`, `totalPnl` |
| GET `/portfolios/{id}` | | `PortfolioDetail {portfolio, holdings: HoldingView[], invested, currentValue, unrealizedPnl, unrealizedPnlPercent, realizedPnl, ownerCash}`; 403 if not managed |
| POST `/portfolios/{id}/trades` (201) | `{symbol, side: BUY or SELL, quantity 1..10,000,000, clientRequestId?: string <= 64}` (repeat of same clientRequestId on the same portfolio returns the first result; same id with a different order = 409; BUY on a price older than 7 days = 409) | `TradeResult {trade: TradeView, holding: HoldingView or null, ownerCash}` |
| GET `/portfolios/{id}/trades` | | `TradeView[]` newest first |

`PortfolioSummary {id, name, sector, ownerId, ownerClientId, ownerName, advisorId, createdOn, currentValue, totalPnl}` (value fields null when not valued).
`HoldingView {symbol, name, sector, quantity, avgBuyPrice, invested, lastPrice, priceAsOf, currentValue, unrealizedPnl, unrealizedPnlPercent}`.
`TradeView {id, symbol, side, quantity, price, amount, realizedPnl, priceAsOf, placedById, executedAt}`.

## Watchlist (all need X-User-Id)
| Method, path | Request | Response |
|---|---|---|
| GET `/watchlist` | | `WatchlistView[] {symbol, name, sector, targetPrice, addedOn, price, targetReached}` |
| POST `/watchlist` | `{symbol, targetPrice? (>= 0)}` | `WatchlistView` (adds or updates target) |
| DELETE `/watchlist/{symbol}` | | `data: null`; 404 if not on list |

## ExpenseMitra (real-life spending; all rows scoped to `X-User-Id`)
Months are `YYYY-MM`; omitted means the current IST month; anything else gives 400. Someone else's expense or custom category answers 404.

| Method, path | Request | Response |
|---|---|---|
| GET `/expense-categories` | | `CategoryView[] {id, name, icon, want, custom}` (shared defaults first) |
| POST `/expense-categories` | `{name, icon?, want?}` | 201 `CategoryView`; 409 if the name exists for the user |
| PUT `/expense-categories/{id}` | `{name, icon?, want?}` | `CategoryView`; 403 on a shared default |
| DELETE `/expense-categories/{id}` | | archives a custom category (past expenses keep it) |
| GET `/expenses?month&categoryId&paymentMode` | | `ExpenseView[] {id, amount, categoryId, category, icon, spentOn, paymentMode, source, note, createdAt}`, newest first |
| POST `/expenses` | `{amount (>= 0.01, 2dp), categoryId, spentOn? (within last 2 years to tomorrow IST, else 400), paymentMode?, note? (200), source?}` | 201 `ExpenseView`; defaults: today IST, `UPI`, `MANUAL` |
| PUT / DELETE `/expenses/{id}` | same body | `ExpenseView` / `data: null` |
| GET `/expenses/summary?month` | | `MonthSummary {month, total, lastMonthTotal, overallBudget, byCategory[CategorySpend {categoryId, name, icon, want, spent, count, budget, archived}] (archived categories with spend in the month are included so rows sum to total), couldHaveBought[WishCard]}` |
| GET `/budgets?month` | | `BudgetView[] {categoryId (null = overall), category, limit}` |
| PUT `/budgets` | `{month, items[{categoryId?, limit?}]}` | upserts listed items; null or 0 limit removes; returns the month's `BudgetView[]` |

`WishCard {categoryId, category, icon, spent, productName, productImage, productPrice, count, progressPercent, investSymbol, investShares}`; see business-logic.md.

## Admin price job (no auth yet)
| Method, path | Response |
|---|---|
| POST `/admin/price-job/run` | `JobRunView {id, trigger, status, startedAt, finishedAt, callsMade, pricesSaved, failures, notes}`; 409 if a run is in progress |
| GET `/admin/price-job/runs` | last 20 `JobRunView` |
| GET `/admin/price-job/quota` | `{dailyBudget, remainingToday}` |

## Other
GET `/` (outside /api): 302 redirect to `app.frontend-url`. CORS allows `app.cors.allowed-origins` for `/api/**`.

## Bhavcopy import (admin, unprotected like price-job)
| Endpoint | Notes |
|---|---|
| POST `/api/admin/bhavcopy/run?date=YYYY-MM-DD` | `date` optional (default last completed trading day; future dates 400). Returns `{date, status, published, nseRows, bseRows, stocksCreated, pricesSaved, failures, notes}`. 409 if an import is running. Safe to re-run a date. `JobRunView` now also has `source`. |

## Bhavcopy preview (trial, read-only, no auth)
| Method, path | Response |
|---|---|
| GET `/api/admin/bhavcopy/preview/{exchange}/{date}?equityOnly=true&limit=20` | `{exchange, date, totalRows, equityRows, rows: BhavcopyRow[]}`. `exchange` BSE or NSE (case-insensitive), `date` ISO (2026-10-09), `limit` capped at 200. 400 bad exchange/date; 404 no file for that day; 502 download or format failure. Nothing is saved. |

## Error mapping notes (2026-10-12)
- `DataIntegrityViolationException`: 409 only for duplicate-key violations (SQLState 23505, or 23000 with MySQL 1062/"Duplicate"); anything else (data too long, null) is 400 "Invalid data".
- Spring MVC client errors (405, 415, ...) keep their status, the ProblemDetail detail text as the message, and headers such as `Allow`.
- Budget save request: `items` limited to 50 entries.
- Trades: 409 "No usable price" when the latest close is null or <= 0 (BUY and SELL).

## Health and access gate (2026-10-12)
- GET `/api/health` returns `{"status":"UP"}` (no ResponseStructure envelope, no DB hit); never gated.
- When `app.access-key` is set (env `APP_ACCESS_KEY` in prod): all other `/api/**` need header `X-Access-Key`, else 401 `{data:null, message:"Access key required", statusCode:401}`. CORS preflight is exempt.
- When `app.admin-key` is set (env `APP_ADMIN_KEY`): `/api/admin/**` also needs `X-Admin-Key`, else 403 "Admin key required". Both blank locally and in tests.
