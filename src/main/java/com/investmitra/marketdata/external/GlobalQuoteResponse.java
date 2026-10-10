package com.investmitra.marketdata.external;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * External model for Alpha Vantage's GLOBAL_QUOTE response.
 * <p>
 * Alpha Vantage returns HTTP 200 even for limits and errors, so exactly one of these is set:
 * <ul>
 * <li>{@code quote}: the price data</li>
 * <li>{@code information} / {@code note}: rate limit reached</li>
 * <li>{@code errorMessage}: invalid call or symbol</li>
 * </ul>
 * An unknown symbol comes back as an empty {@code "Global Quote": {}}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GlobalQuoteResponse(
		@JsonProperty("Global Quote") GlobalQuote quote,
		@JsonProperty("Information") String information,
		@JsonProperty("Note") String note,
		@JsonProperty("Error Message") String errorMessage) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record GlobalQuote(
			@JsonProperty("01. symbol") String symbol,
			@JsonProperty("02. open") BigDecimal open,
			@JsonProperty("03. high") BigDecimal high,
			@JsonProperty("04. low") BigDecimal low,
			@JsonProperty("05. price") BigDecimal price,
			@JsonProperty("06. volume") Long volume,
			@JsonProperty("07. latest trading day") LocalDate latestTradingDay,
			@JsonProperty("08. previous close") BigDecimal previousClose,
			@JsonProperty("09. change") BigDecimal change,
			@JsonProperty("10. change percent") String changePercent) {
	}

	public boolean isRateLimited() {
		return information != null || note != null;
	}

	public boolean hasQuote() {
		return quote != null && quote.price() != null && quote.latestTradingDay() != null;
	}
}
