package com.investmitra.marketdata.bhavcopy;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One instrument's end-of-day line from an exchange bhavcopy. */
public record BhavcopyRow(
		LocalDate tradeDate,
		String exchange,
		String instrumentType,
		String symbol,
		String series,
		String isin,
		String name,
		BigDecimal open,
		BigDecimal high,
		BigDecimal low,
		BigDecimal close,
		BigDecimal previousClose,
		Long volume) {

	/** NSE series that are ordinary shares; the rest (GB, GS, N0...) are bonds. BSE groups are shares except F, IF, G, GS (see below). */
	private static final java.util.Set<String> NSE_EQUITY_SERIES = java.util.Set.of("EQ", "BE", "BZ", "SM", "ST");

	/** BSE groups verified on a real file: F = bonds/NCDs, IF = InvITs; G and GS are government securities. */
	private static final java.util.Set<String> BSE_NON_EQUITY_GROUPS = java.util.Set.of("F", "IF", "G", "GS");

	/**
	 * Company shares only. ISINs starting INE are company securities; INF are funds/ETFs and IN0/IN9 are
	 * government bonds. NSE also lists company bonds under INE, so its series must be an equity series.
	 */
	public boolean isEquity() {
		if (!"STK".equals(instrumentType) || isin == null || !isin.startsWith("INE")) {
			return false;
		}
		if ("NSE".equals(exchange)) {
			return NSE_EQUITY_SERIES.contains(series);
		}
		return series == null || !BSE_NON_EQUITY_GROUPS.contains(series.trim().toUpperCase(java.util.Locale.ROOT));
	}
}
