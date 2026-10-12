package com.investmitra.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.investmitra.entity.Role;
import com.investmitra.entity.TradeSide;
import com.investmitra.web.dto.Dtos.CreatePortfolioRequest;
import com.investmitra.web.dto.Dtos.CreateUserRequest;
import com.investmitra.web.dto.Dtos.TradeRequest;
import com.investmitra.web.dto.Dtos.WatchlistRequest;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class DtoValidationTest {

	private final Validator v = Validation.buildDefaultValidatorFactory().getValidator();

	@Test
	void userFieldLimits() {
		assertThat(v.validate(new CreateUserRequest("a".repeat(81), "a@b.com", "secret1", Role.USER))).hasSize(1);
		assertThat(v.validate(new CreateUserRequest("a", "a@b.com", "12345", Role.USER))).hasSize(1);
		assertThat(v.validate(new CreateUserRequest("a", "a@b.com", "x".repeat(73), Role.USER))).hasSize(1);
		assertThat(v.validate(new CreateUserRequest("a".repeat(80), "a@b.com", "x".repeat(72), Role.USER))).isEmpty();
	}

	@Test
	void portfolioNameLimit() {
		assertThat(v.validate(new CreatePortfolioRequest("n".repeat(61), null, null, null))).hasSize(1);
		assertThat(v.validate(new CreatePortfolioRequest("n".repeat(60), null, null, null))).isEmpty();
	}

	@Test
	void tradeQuantityAndRequestIdLimits() {
		assertThat(v.validate(new TradeRequest("TCS", TradeSide.BUY, 10_000_001L, null))).hasSize(1);
		assertThat(v.validate(new TradeRequest("TCS", TradeSide.BUY, 10_000_000L, "r".repeat(64)))).isEmpty();
		assertThat(v.validate(new TradeRequest("TCS", TradeSide.BUY, 1, "r".repeat(65)))).hasSize(1);
	}

	@Test
	void budgetItemsAreCappedAt50() {
		var items = new java.util.ArrayList<com.investmitra.expense.ExpenseDtos.BudgetItem>();
		for (int i = 0; i < 51; i++) {
			items.add(new com.investmitra.expense.ExpenseDtos.BudgetItem((long) i, BigDecimal.TEN));
		}
		assertThat(v.validate(new com.investmitra.expense.ExpenseDtos.BudgetRequest("2026-10", items))).hasSize(1);
		assertThat(v.validate(new com.investmitra.expense.ExpenseDtos.BudgetRequest("2026-10", items.subList(0, 50))))
				.isEmpty();
	}

	@Test
	void watchlistTargetPrice() {
		assertThat(v.validate(new WatchlistRequest("TCS", BigDecimal.ZERO))).hasSize(1);
		assertThat(v.validate(new WatchlistRequest("TCS", new BigDecimal("-1")))).hasSize(1);
		assertThat(v.validate(new WatchlistRequest("TCS", new BigDecimal("10.123")))).hasSize(1);
		assertThat(v.validate(new WatchlistRequest("TCS", new BigDecimal("1234.50")))).isEmpty();
		assertThat(v.validate(new WatchlistRequest("TCS", null))).isEmpty();
	}
}
