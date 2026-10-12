# Deploying free: Render (backend) + Neon (Postgres)

The Dockerfile has not been built or run on this machine (Docker was not installed), so treat the first Render build as its test.
Local development is unchanged: MySQL, no profile, no keys.

## What the app does in production
- Profile `prod` (set by the Dockerfile and `render.yaml`) reads `application-prod.properties`: Postgres via env vars, Hikari pool of 3, `ddl-auto=update`, quiet logs, `server.port=${PORT}`.
- `GET /api/health` returns `{"status":"UP"}` without touching the database.
- If `APP_ACCESS_KEY` is set, every `/api/**` call except `/api/health` needs header `X-Access-Key`. If `APP_ADMIN_KEY` is set, `/api/admin/**` also needs `X-Admin-Key`. The frontend must send `X-Access-Key` on every call.
- The Alpha Vantage job skips itself when `ALPHAVANTAGE_API_KEY` is empty. The bhavcopy import (19:30 IST, retries 20:00 and 20:30) stays on.

## Steps
1. **Neon**: create a project at neon.tech, region Asia Pacific (Singapore) `ap-southeast-1`. Keep the default database (`neondb`) and role.
2. From the Neon dashboard, Connect, choose the pooled or direct string and split it:
   - `DATABASE_URL` = `jdbc:postgresql://<host>/<dbname>?sslmode=require` (the host looks like `ep-xxxx.ap-southeast-1.aws.neon.tech`; use the URL form with `jdbc:postgresql://`, not `postgresql://user:pass@...`)
   - `DATABASE_USERNAME` = the role name
   - `DATABASE_PASSWORD` = the role password
3. **Render**: New, Blueprint, connect the private GitHub repo, pick the `investmitra-backend` repo. Render reads `render.yaml` and asks for the secret values (`sync: false`).
4. Set the env vars: the three database ones above, plus
   - `CORS_ORIGINS` = the frontend origin(s), comma separated, no trailing slash (e.g. `https://investmitra.vercel.app`)
   - `FRONTEND_URL` = the frontend URL (where `/` redirects)
   - `APP_ACCESS_KEY` = a long random string (e.g. `openssl rand -hex 24`); put the same value in the frontend
   - `APP_ADMIN_KEY` = a different long random string
   - `ALPHAVANTAGE_API_KEY` = optional; leave empty to disable that job
5. Deploy. The first build takes several minutes. When the service shows Live, open `https://<service>.onrender.com/api/health`.
6. **Keep-alive**: Render free sleeps after 15 minutes idle (cold start ~30-60 s). At cron-job.org create a job: URL `https://<service>.onrender.com/api/health`, every 14 minutes, GET. Note the scheduled bhavcopy job only runs while the service is awake, so the 14-minute ping is also what makes the 19:30 IST import fire. Render free also allows about 750 instance hours a month, which one always-on service just about fits.
7. **First bhavcopy backfill** (one day per call; weekends and holidays answer "no bhavcopy"):
   ```
   curl -X POST "https://<service>.onrender.com/api/admin/bhavcopy/run?date=2026-10-09" \
     -H "X-Access-Key: <APP_ACCESS_KEY>" -H "X-Admin-Key: <APP_ADMIN_KEY>"
   ```
   Omit `?date=` for the last completed trading day. Loop over recent weekdays for a longer backfill. Re-running a date updates, never duplicates. Each day adds roughly 5k price rows.
8. Check the log in Render if something fails; wrong `DATABASE_URL` shows up as a Hikari connection error on start.

## Storage
Neon free gives about 0.5 GB. With ~5k price rows a day (about 1M rows a year, roughly 150-200 bytes per row with the unique index), the database fills in a few months of daily imports. Plan: prune `stock_price` rows older than N days (e.g. 90) in a scheduled job. Not implemented; see open-issues.md.
