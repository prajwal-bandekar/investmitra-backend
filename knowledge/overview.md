# Backend overview

**Purpose:** REST API for InvestMitra paper trading: users, folios (portfolios), trades at stored closing prices, watchlists, and a budgeted daily price fetch from Alpha Vantage.

**Repo:** `investmitra-backend` | **Package:** `com.investmitra` | **Port:** 8080 (all routes under `/api`, plus `/` redirect)

## Stack
- Spring Boot 4.1.1, Java 25 (Amazon Corretto, Gradle toolchain), Gradle Kotlin DSL (wrapper 9.7.1)
- Starters: data-jpa, validation, webmvc, restclient; `spring-security-crypto` (BCrypt only, no Spring Security filter chain)
- Lombok (`@Getter/@Setter/@NoArgsConstructor` on entities)
- MySQL 8 (runtime), H2 in MySQL mode (tests). Jackson 3: imports are `tools.jackson.*` (annotations still `com.fasterxml.jackson.annotation`)
- Config: `src/main/resources/application.properties`; Gradle JDK path pinned in `gradle.properties`

## Structure (src/main/java/com/investmitra)
| Package | Contents |
|---|---|
| `entity` | JPA entities and enums (see data-model.md) |
| `repository` | Spring Data repositories with custom JPQL |
| `service` | Business logic (trading, portfolios, users, stocks, watchlist, catalog seeding) |
| `marketdata` | Alpha Vantage client, budget/freshness logic, scheduler, calendar |
| `web` / `web/dto` | Controllers, exception handler, DTO records (`Dtos`), `ResponseStructure`, `PriceView` |
| `config` | `AlphaVantageProperties`, `AppConfig` (Clock, Pacer, CORS) |
Resources: `data/nse-500-stocks.json` (catalog, do not open, large). Tests in `src/test`.

## Build / run / test
```
$env:JAVA_HOME="$env:LOCALAPPDATA\Programs\Corretto\jdk25.0.4_10"
$env:GRADLE_USER_HOME="$env:LOCALAPPDATA\Programs\GradleHome"
.\gradlew bootRun     # needs MySQL80 running; ALPHAVANTAGE_API_KEY optional for boot
.\gradlew test        # 30 tests, H2, no network, key faked
.\gradlew build
```
Tests: `InvestMitraIntegrationTest` (15, MockMvc end to end), `AlphaVantageClientTest` (5, uses fixtures in `src/test/resources/alphavantage/*.json`), `MarketCalendarTest` (5), `PositionMathTest` (5). Test profile: daily-budget 3, request-delay 0ms, tracked TCS/INFY/HDFCBANK/RELIANCE, `ddl-auto=create-drop`.

## Key config properties
`alphavantage.api-key=${ALPHAVANTAGE_API_KEY:}`, `alphavantage.request-delay=1500ms`, `alphavantage.daily-budget=24`, `alphavantage.exchange-suffix=.BSE`, `pricejob.cron=0 0 7 * * MON-FRI` (IST), `catalog.tracked=` 20 symbols, `trading.starting-cash=1000000`, `app.cors.allowed-origins=http://localhost:3000`, `app.frontend-url=http://localhost:3000`, `spring.jpa.hibernate.ddl-auto=update`, `spring.jpa.open-in-view=false`.
