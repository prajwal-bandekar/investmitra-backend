package com.investmitra.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Alpha Vantage settings. The free key allows 25 requests per day and about 1 per second,
 * so the job keeps a few calls spare and waits between requests.
 *
 * @param baseUrl      API endpoint
 * @param apiKey       read from the ALPHAVANTAGE_API_KEY environment variable
 * @param requestDelay pause between two requests
 * @param dailyBudget    maximum calls the app may spend per day (IST), across the job and on-demand quotes
 * @param exchangeSuffix appended to an NSE symbol when a stock has no explicit Alpha Vantage symbol, e.g. ".BSE"
 */
@ConfigurationProperties(prefix = "alphavantage")
public record AlphaVantageProperties(String baseUrl, String apiKey, Duration requestDelay, int dailyBudget,
		String exchangeSuffix) {
}
