package com.investmitra.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class PositionMathTest {

	@Test
	void firstBuySetsAverageToPrice() {
		assertThat(PositionMath.averageAfterBuy(0, BigDecimal.ZERO, 10, new BigDecimal("3200")))
				.isEqualByComparingTo("3200");
	}

	@Test
	void averageIsWeightedByQuantity() {
		// 10 @ 3000 + 30 @ 3400 = 132000 / 40 = 3300
		assertThat(PositionMath.averageAfterBuy(10, new BigDecimal("3000"), 30, new BigDecimal("3400")))
				.isEqualByComparingTo("3300");
	}

	@Test
	void averageKeepsFourDecimals() {
		// 3 @ 100 + 1 @ 101 = 401 / 4 = 100.25; 1 @ 100 + 2 @ 100.01 = 300.02 / 3 = 100.0067
		assertThat(PositionMath.averageAfterBuy(3, new BigDecimal("100"), 1, new BigDecimal("101")))
				.isEqualByComparingTo("100.25");
		assertThat(PositionMath.averageAfterBuy(1, new BigDecimal("100"), 2, new BigDecimal("100.01")))
				.isEqualByComparingTo("100.0067");
	}

	@Test
	void realizedPnlUsesAverageNotLastBuy() {
		assertThat(PositionMath.realizedPnl(new BigDecimal("3300"), 5, new BigDecimal("3500")))
				.isEqualByComparingTo("1000.00");
		assertThat(PositionMath.realizedPnl(new BigDecimal("3300"), 5, new BigDecimal("3100")))
				.isEqualByComparingTo("-1000.00");
	}

	@Test
	void percentOfZeroIsNull() {
		assertThat(PositionMath.percent(BigDecimal.ONE, BigDecimal.ZERO)).isNull();
		assertThat(PositionMath.percent(new BigDecimal("50"), new BigDecimal("200"))).isEqualByComparingTo("25");
	}
}
