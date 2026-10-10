package com.investmitra.web.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;

import com.investmitra.entity.StockPrice;

/**
 * A stored price as shown to the client.
 *
 * @param source {@code DATABASE} when served from storage, {@code ALPHA_VANTAGE} when fetched just now
 * @param fresh  false when the price is older than the last completed trading day
 */
public record PriceView(
		LocalDate asOf,
		BigDecimal open,
		BigDecimal high,
		BigDecimal low,
		BigDecimal close,
		BigDecimal previousClose,
		BigDecimal change,
		BigDecimal changePercent,
		Long volume,
		Instant fetchedAt,
		String source,
		boolean fresh) {

	public static PriceView of(StockPrice p, String source, boolean fresh) {
		BigDecimal change = null;
		BigDecimal changePct = null;
		if (p.getPreviousClose() != null && p.getPreviousClose().signum() != 0) {
			change = p.getClose().subtract(p.getPreviousClose()).setScale(2, RoundingMode.HALF_UP);
			changePct = p.getClose().subtract(p.getPreviousClose())
					.multiply(BigDecimal.valueOf(100))
					.divide(p.getPreviousClose(), 2, RoundingMode.HALF_UP);
		}
		return new PriceView(p.getTradeDate(), p.getOpen(), p.getHigh(), p.getLow(), p.getClose(),
				p.getPreviousClose(), change, changePct, p.getVolume(), p.getFetchedAt(), source, fresh);
	}
}
