package com.investmitra.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Broker-style position arithmetic. Prices keep 4 decimals, rupee amounts 2. */
public final class PositionMath {

	private PositionMath() {
	}

	/** Weighted average after buying {@code qty} more at {@code price}. */
	public static BigDecimal averageAfterBuy(long oldQty, BigDecimal oldAvg, long qty, BigDecimal price) {
		BigDecimal oldCost = oldAvg.multiply(BigDecimal.valueOf(oldQty));
		BigDecimal newCost = price.multiply(BigDecimal.valueOf(qty));
		return oldCost.add(newCost).divide(BigDecimal.valueOf(oldQty + qty), 4, RoundingMode.HALF_UP);
	}

	/** Profit booked by selling {@code qty} at {@code price}. Selling never changes the average. */
	public static BigDecimal realizedPnl(BigDecimal avg, long qty, BigDecimal price) {
		return price.subtract(avg).multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
	}

	public static BigDecimal amount(BigDecimal price, long qty) {
		return price.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
	}

	/** Percentage of {@code part} over {@code whole}, or null when whole is zero. */
	public static BigDecimal percent(BigDecimal part, BigDecimal whole) {
		if (whole == null || whole.signum() == 0) {
			return null;
		}
		return part.multiply(BigDecimal.valueOf(100)).divide(whole, 2, RoundingMode.HALF_UP);
	}
}
