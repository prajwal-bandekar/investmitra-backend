# Backend components

Base path: `src/main/java/com/investmitra/`. Constructor injection everywhere.

## Entry / config
- **InvestMitraApplication** (`InvestMitraApplication.java`): Spring Boot main; enables scheduling and `@ConfigurationProperties`.
- **AppConfig** (`config/AppConfig.java`): beans `Clock` (UTC), `Pacer` (Thread.sleep), CORS for `/api/**` (GET/POST/PUT/DELETE from `app.cors.allowed-origins`).
- **AlphaVantageProperties** (`config/`): record bound to prefix `alphavantage` (baseUrl, apiKey, requestDelay, dailyBudget, exchangeSuffix).

## marketdata
- **AlphaVantageClient**: `getGlobalQuote(avSymbol)` (one HTTP GET = one quota call), `isConfigured()`, package-private `toQuote(symbol, body)` for tests. Parses body into `GlobalQuoteResponse`, maps limits and errors to exceptions. Uses `RestClient`.
- **GlobalQuoteResponse** (`marketdata/external/`): external model. Fields `quote`, `information`, `note`, `errorMessage` via `@JsonProperty`; nested `GlobalQuote` record (`01. symbol` ... `10. change percent`); `isRateLimited()`, `hasQuote()`.
- **AlphaVantageException**: base plus `RateLimited` and `SymbolNotFound`.
- **MarketDataService**: only caller of Alpha Vantage. `getQuote(symbol)` (on-demand), `refreshTrackedStocks(trigger)` (job body), `remainingCallsToday()`, `alphaVantageSymbol(stock)`, `isFresh(price)`, `staleTrackedStocks()`. Holds `ReentrantLock apiLock` and `lastCallAt` for pacing. Constants `SOURCE_DATABASE`, `SOURCE_ALPHA_VANTAGE`.
- **MarketCalendar**: IST dates, `lastCompletedTradingDay()`, `isFresh(tradeDate, fetchedAt)`, `today()`, `startOfToday()`. No holiday calendar.
- **PriceFetchJob**: `@Scheduled(cron=${pricejob.cron}, zone Asia/Kolkata)` calls `refreshTrackedStocks(SCHEDULED)`; swallows `ApiException` (already running).
- **StockPriceWriter**: `@Transactional upsert(stock, quote)` keyed by (stock, tradeDate).
- **Pacer**: functional interface `pause(Duration)`; real impl sleeps, tests use no-op.

## service
- **ApiException**: carries HttpStatus; factories notFound, badRequest, forbidden, conflict, unavailable.
- **CatalogSeeder** (`ApplicationRunner`): loads `data/nse-500-stocks.json` when `stock` table is empty (fields Company Name, Industry, Symbol, ISIN Code); every start syncs `avSymbol = SYMBOL + exchangeSuffix` for `catalog.tracked`, null for others; warns if tracked symbols are missing from catalog.
- **UserService**: `create` (lowercases email, BCrypt, sets starting cash, 409 on duplicate), `login` (generic 401 "Wrong email or password"), `get`, `require`.
- **StockService**: DB-only `search(query, sector, userId, page, size)` (size capped 50), `get`, `tracked`, `sectors`, `require`, `latestPrices(ids)`, `priceView`; `combinedPosition` merges holdings across the caller's portfolios.
- **PortfolioService**: `create`, `listManagedBy`, `detail`, `tradeHistory`, `requireManaged` (403 if not owner/advisor); static view builders.
- **TradingService**: `trade(actorId, portfolioId, request)` single `@Transactional` buy/sell. See business-logic.md.
- **WatchlistService**: `list`, `add` (upsert target price), `remove` (404 if absent); `targetReached` = close <= target.
- **PositionMath**: pure helpers `averageAfterBuy`, `realizedPnl`, `amount`, `percent`.

## repository (Spring Data)
`AppUserRepository` (`findByIdForUpdate` with PESSIMISTIC_WRITE, `findByEmailIgnoreCase`, `existsByEmailIgnoreCase`), `StockRepository` (`findBySymbolIgnoreCase`, `findByAvSymbolIsNotNull`, `search`, `sectorSummary`), `StockPriceRepository` (`findTopByStockOrderByTradeDateDesc`, `findByStockAndTradeDate`, `findLatestFor(ids)`), `HoldingRepository` (`findByPortfolioAndStock`, `findByPortfolio`, `findByPortfolioIn`, `findManagedByUserIn`), `PortfolioRepository` (`findManagedBy(userId)`), `TradeRepository` (`totalRealizedPnl`, `findByPortfolioNewestFirst`), `WatchlistItemRepository`, `JobRunRepository` (`findTop20ByOrderByStartedAtDesc`, `callsMadeSince(instant)`).

## web
- **UserController** `/api/users`, **StockController** `/api` (stocks, sectors), **PortfolioController** `/api/portfolios`, **WatchlistController** `/api/watchlist`, **PriceJobController** `/api/admin/price-job`, **HomeController** `/` (302 to frontend). Details in api.md.
- **ApiExceptionHandler** (`@RestControllerAdvice`): ApiException -> its status; validation -> 400 "field message"; missing header -> 401 "Missing header X"; unreadable body -> 400.
- **dto/Dtos**: all request/response records plus `clientId(id)` -> `IM-%04d`. **dto/PriceView**: price + change/changePercent computed from previousClose, `source`, `fresh`. **dto/ResponseStructure**: `{data, message, statusCode}`.
