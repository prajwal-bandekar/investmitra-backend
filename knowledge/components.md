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
- **BhavcopyImporter** (`marketdata/bhavcopy`): the import described in business-logic.md; `importDay`, `importedSince`. **BhavcopyJob**: scheduled run + retry. **BhavcopyImportController** (`web`): `POST /api/admin/bhavcopy/run`.
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

## expense (ExpenseMitra, self-contained package)
- Entities `Expense`, `ExpenseCategory`, `Budget`, `WishProduct`; enums `PaymentMode`, `ExpenseSource`; repositories for each.
- **ExpenseService**: categories CRUD (own only), expense CRUD (404 for others' rows), `saveBudgets` upsert, `summary(month)` with `card(...)` for could-have-bought.
- **ExpenseController** `/api/expense-categories`, `/api/expenses`, `/api/expenses/summary`, `/api/budgets`.
- **ExpenseSeeder**: seeds default categories and wish products when their tables are empty.
- **ExpenseDtos**: request/response records for the module.
- **Free to spend (2026-10-13)**: entities `SalarySetting`, `ExtraIncome`, `Commitment` (enum `CommitmentKind`) with repositories; **PlanService** (plan maths, salary upsert under the user-row lock, commitment and extra-income CRUD, 404 for others' rows); **PlanController** `/api/plan`, `/api/plan/salary`, `/api/commitments`, `/api/extra-income`; **PlanDtos** records. Reuses `ExpenseRepository.total` for spent.

## repository (Spring Data)
`AppUserRepository` (`findByIdForUpdate` with PESSIMISTIC_WRITE, `findByEmailIgnoreCase`, `existsByEmailIgnoreCase`), `StockRepository` (`findBySymbolIgnoreCase`, `findByAvSymbolIsNotNull`, `search`, `sectorSummary`), `StockPriceRepository` (`findTopByStockOrderByTradeDateDesc`, `findByStockAndTradeDate`, `findLatestFor(ids)`), `HoldingRepository` (`findByPortfolioAndStock`, `findByPortfolio`, `findByPortfolioIn`, `findManagedByUserIn`), `PortfolioRepository` (`findManagedBy(userId)`), `TradeRepository` (`totalRealizedPnl`, `findByPortfolioNewestFirst`), `WatchlistItemRepository`, `JobRunRepository` (`findTop20ByOrderByStartedAtDesc`, `callsMadeSince(instant)`).

## web
- **UserController** `/api/users`, **StockController** `/api` (stocks, sectors), **PortfolioController** `/api/portfolios`, **WatchlistController** `/api/watchlist`, **PriceJobController** `/api/admin/price-job`, **HomeController** `/` (302 to frontend). Details in api.md.
- **ApiExceptionHandler** (`@RestControllerAdvice`): ApiException -> its status; validation -> 400 "field message"; missing header -> 401 "Missing header X"; unreadable body -> 400.
- **dto/Dtos**: all request/response records plus `clientId(id)` -> `IM-%04d`. **dto/PriceView**: price + change/changePercent computed from previousClose, `source`, `fresh`. **dto/ResponseStructure**: `{data, message, statusCode}`.

- 2026-10-12: `BhavcopyController` preview is now `/api/admin/bhavcopy/preview/...`. `BhavcopyParser.ParsedRows` (List with `skipped()`); `BhavcopyClient.readCapped`. `ExpenseService` now also takes `MarketCalendar` and `AppUserRepository`; `BudgetRepository.findFirstByUserAndMonthAndCategoryIsNull`.
