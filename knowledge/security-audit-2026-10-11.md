# Backend security audit (2026-10-11, read-only, not yet fixed)

Most severe first. Line numbers approximate.

1. CRITICAL: impersonation. `X-User-Id` header is trusted as-is (PortfolioController, WatchlistController, StockController). Anyone can send another id. Fix: signed token (JWT/session) issued at login.
2. CRITICAL: self-assigned ADVISOR role at signup (Dtos CreateUserRequest.role) + advisor can create a portfolio for ANY ownerId (PortfolioService.create) and trade with that user's cash. Fix: role not self-assignable; client-accepted advisor link.
3. HIGH: `GET /api/users/{id}` returns email, role, cash for any id. Fix: own id only.
4. HIGH: Price Desk / admin endpoints (`/api/admin/price-job/*`, bhavcopy endpoints) have no auth; anyone can burn the Alpha Vantage quota. Run is synchronous. Fix: admin role, async, rate-limit.
5. HIGH: `GET /api/stocks/{symbol}/quote` unauthenticated and spends an AV call for stale stocks. Fix: login + per-user/IP rate limit + separate on-demand cap.
6. MEDIUM: `setScale(2)` without RoundingMode in PortfolioService (~74, ~93) can throw ArithmeticException -> 500. Fix: `setScale(2, RoundingMode.HALF_UP)`.
7. MEDIUM: advisor can trade owner's cash with no limits; sector lock only checked on BUY. Document or add limits.
8. MEDIUM: no `@Max` on trade quantity; long overflow possible in quantity sums. Fix: `@Max`, `Math.addExact`.
9. MEDIUM: signup email race gives 500 instead of 409 (catch DataIntegrityViolationException); no idempotency key so a double-submitted BUY executes twice.
10. MEDIUM: missing `@Size`/`@Positive`/`@Digits` on names, password, portfolio name/sector, watchlist targetPrice, search query.
11. LOW: no login rate limiting.
12. LOW: no catch-all exception handler; set `server.error.include-message=never`; CORS must be per-environment.

Not covered: bhavcopy controllers, WatchlistRequest, remaining entities.
