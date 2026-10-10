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
| POST `/portfolios` (201) | `{name, sector?, ownerId?}` (ownerId only for advisors) | `PortfolioSummary` |
| GET `/portfolios` | | `PortfolioSummary[]` owned or advised, with `currentValue`, `totalPnl` |
| GET `/portfolios/{id}` | | `PortfolioDetail {portfolio, holdings: HoldingView[], invested, currentValue, unrealizedPnl, unrealizedPnlPercent, realizedPnl, ownerCash}`; 403 if not managed |
| POST `/portfolios/{id}/trades` (201) | `{symbol, side: BUY or SELL, quantity >= 1}` | `TradeResult {trade: TradeView, holding: HoldingView or null, ownerCash}` |
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

## Admin price job (no auth yet)
| Method, path | Response |
|---|---|
| POST `/admin/price-job/run` | `JobRunView {id, trigger, status, startedAt, finishedAt, callsMade, pricesSaved, failures, notes}`; 409 if a run is in progress |
| GET `/admin/price-job/runs` | last 20 `JobRunView` |
| GET `/admin/price-job/quota` | `{dailyBudget, remainingToday}` |

## Other
GET `/` (outside /api): 302 redirect to `app.frontend-url`. CORS allows `app.cors.allowed-origins` for `/api/**`.
