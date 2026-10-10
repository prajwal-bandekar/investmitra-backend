# InvestMitra backend

Spring Boot 4 (Java 25) REST API for InvestMitra, a paper-trading simulator for the NIFTY 500.
Stores daily closing prices from Alpha Vantage in MySQL 8, executes paper trades with broker-style average pricing, and serves the React frontend.

```bash
# needs MySQL 8 on localhost:3306 and these environment variables:
#   DB_PASSWORD            MySQL root password
#   ALPHAVANTAGE_API_KEY   free key from alphavantage.co
./gradlew bootRun   # http://localhost:8080/api
./gradlew test      # uses in-memory H2, Alpha Vantage mocked
```

Full documentation lives in [`knowledge/`](knowledge/).
