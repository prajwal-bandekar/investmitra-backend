# Backend decisions (ADR log)

Format: date, decision, why, alternatives rejected, status. Dates are when the decision was made in conversation (2026-10-06 to 2026-10-10 unless noted).

## D01 Project rename and package (2026-10-06)
- **Decision:** Copy `Desktop/Final Project` into `Desktop/InvestMitra`; remove the old Wells Fargo naming; package `com.investmitra`; folders `investmitra-backend` and `investmitra-frontend`.
- **Why:** Clean identity for the product; the old name belonged to a different project.
- **Rejected:** Editing in place under the old name.
- **Status:** Done.

## D02 Domain model rewrite (2026-10-06)
- **Decision:** Replace Advisor, Client, Security entities with Stock, StockPrice, AppUser (role USER/ADVISOR), Portfolio, Holding, Trade, WatchlistItem, JobRun (trigger SCHEDULED/MANUAL/ON_DEMAND). Money as BigDecimal.
- **Why:** Old model did not fit paper trading with advisors managing client folios; floating point is wrong for money.
- **Rejected:** Keeping separate Advisor and Client tables (one AppUser with a role is simpler).
- **Status:** Done. Old ERD image `investmitra_ERD_old.jpg` is obsolete.

## D03 Maven to Gradle (2026-10-06)
- **Decision:** Gradle Kotlin DSL, wrapper 9.7.1, at the user's request.
- **Why:** User preference.
- **Rejected:** Staying on Maven.
- **Status:** Done.

## D04 Java and Spring Boot versions (2026-10-06)
- **Decision:** Java 17 then 21 then 25 (Amazon Corretto 25.0.4_10); Spring Boot 3.1.3 then 4.1.1. Boot 4 uses Jackson 3, so imports are `tools.jackson.*`.
- **Why:** Latest supported stack on the user's machine.
- **Rejected:** Staying on Boot 3.1.3 / Java 17.
- **Status:** Done. JDK path pinned in `gradle.properties`, auto-download disabled.

## D05 Alpha Vantage usage model (2026-10-07)
- **Decision:** Free key (25 calls/day, about 1 request/second). Fetch each stock once per day into the DB and serve search from the DB. Daily budget 24, shared by job and on-demand. 20 tracked stocks (4 sectors x 5) as `SYMBOL.BSE`. Prices stored date-wise and reused to save calls (explicit user requirement).
- **Why:** The quota cannot support live quotes for 500 stocks; one close per day is enough for paper trading at last close.
- **Rejected:** `REALTIME_BULK_QUOTES` (premium only); live per-request quotes; `.NSE` suffix (not confirmed to be supported, so BSE is used until verified).
- **Status:** Implemented and unit tested. First real run against the live API not yet done.

## D06 API key handling (2026-10-07)
- **Decision:** Key comes from env var `ALPHAVANTAGE_API_KEY`, never in code or docs.
- **Why:** Secrets must stay out of source control.
- **Note / risk:** The user pasted the key in chat and chose to keep using it. Consider rotating it.
- **Status:** Done; risk accepted by user.

## D07 External model class instead of manual parsing (2026-10-08)
- **Decision:** `GlobalQuoteResponse` with `@JsonProperty` mapping, one client method `getGlobalQuote`.
- **Why:** Typed, testable, no stringly-typed map digging.
- **Rejected:** Parsing the JSON tree by hand; multiple client methods.
- **Status:** Done.

## D08 Passwords and auth (2026-10-08)
- **Decision:** BCrypt passwords, login by email plus password, identical error for unknown email and wrong password. Auth remains a placeholder: client sends `X-User-Id`.
- **Why:** Get a working app first; real tokens are a larger piece of work.
- **Rejected for now:** JWT or sessions, Spring Security filter chain.
- **Status:** Placeholder. See open-issues.md (real tokens and admin protection before going public).

## D09 Client IDs as IM-0001 (2026-10-08)
- **Decision:** Display user id as `IM-%04d` (`Dtos.clientId`); the frontend parses it back.
- **Why:** Advisors need a friendly ID to link a client.
- **Rejected:** Showing raw numeric ids.
- **Status:** Done.

## D10 Database engine (2026-10-09)
- **Decision:** Use installed MySQL 8.0 (service `MySQL80` set Automatic, `MySQL55` set Manual). Hibernate `ddl-auto=update` creates tables.
- **Why:** MySQL 5.5 lacks `datetime(6)` and is unsupported by Boot 4's driver.
- **Rejected:** MySQL 5.5; Flyway now (deferred, see open-issues).
- **Status:** Done. DB password sits in plain `application.properties` (open issue).

## D11 Reserve dev ports on Windows (2026-10-09)
- **Decision:** The Windows dynamic port range on this PC is 1024-15000, so outgoing connections could steal 3000 and 8080. Ports 3000 and 8080 were reserved with `netsh int ipv4 add excludedportrange`.
- **Why:** Stop random "port in use" failures at startup.
- **Rejected:** Changing app ports; shrinking the dynamic range globally.
- **Status:** Done (machine specific, not in the repo).

## D12 Backend root redirects to frontend (2026-10-09)
- **Decision:** `GET /` returns 302 to `app.frontend-url`.
- **Why:** Opening :8080 in a browser should land on the app, since the backend serves only /api.
- **Rejected:** Whitelabel 404 page.
- **Status:** Done.

## D13 LAN access not enabled (2026-10-10)
- **Decision:** Do not expose the app to another laptop on the shared PG Wi-Fi (Public network profile).
- **Why:** Placeholder auth and unprotected `/api/admin` make exposure a security risk.
- **Rejected:** Opening firewall and binding to 0.0.0.0 now.
- **Status:** Pending user choice (revisit after real auth).

## D14 DBeaver "papertrading" connection (2026-10-10)
- **Decision:** Treat it as unrelated: it points to an AWS RDS PostgreSQL, not this app's local MySQL.
- **Why:** Different engine and host.
- **Status:** Open question: is that RDS instance still billing? (see open-issues.md)
