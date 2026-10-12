package com.investmitra.expense;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request and response bodies for ExpenseMitra. */
public final class ExpenseDtos {

	private ExpenseDtos() {
	}

	public static final String MONTH_PATTERN = "\\d{4}-(0[1-9]|1[0-2])";

	// ---- Categories ----

	public record CategoryRequest(@NotBlank @Size(max = 40) String name, @Size(max = 8) String icon, Boolean want) {
	}

	public record CategoryView(Long id, String name, String icon, boolean want, boolean custom) {

		static CategoryView of(ExpenseCategory c) {
			return new CategoryView(c.getId(), c.getName(), c.getIcon(), c.isWant(), c.getUser() != null);
		}
	}

	// ---- Expenses ----

	/** @param spentOn defaults to today (IST); paymentMode defaults to UPI */
	public record ExpenseRequest(@NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
			@NotNull Long categoryId, LocalDate spentOn, PaymentMode paymentMode, @Size(max = 200) String note,
			ExpenseSource source) {
	}

	public record ExpenseView(Long id, BigDecimal amount, Long categoryId, String category, String icon,
			LocalDate spentOn, PaymentMode paymentMode, ExpenseSource source, String note, Instant createdAt) {

		static ExpenseView of(Expense e) {
			ExpenseCategory c = e.getCategory();
			return new ExpenseView(e.getId(), e.getAmount(), c.getId(), c.getName(), c.getIcon(), e.getSpentOn(),
					e.getPaymentMode(), e.getSource(), e.getNote(), e.getCreatedAt());
		}
	}

	// ---- Budgets ----

	/** @param categoryId null for the overall monthly cap; limit null or 0 removes the budget */
	public record BudgetItem(Long categoryId, @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal limit) {
	}

	public record BudgetRequest(@NotNull @Pattern(regexp = MONTH_PATTERN) String month,
			@NotNull @Valid List<BudgetItem> items) {
	}

	public record BudgetView(Long categoryId, String category, BigDecimal limit) {
	}

	// ---- Summary ----

	public record CategorySpend(Long categoryId, String name, String icon, boolean want, BigDecimal spent, long count,
			BigDecimal budget) {
	}

	/**
	 * Year-to-date spend in a "want" category expressed as something to buy.
	 *
	 * @param count           how many of the product the spend covers (0 when it covers none yet)
	 * @param progressPercent share of the product's price covered, set only when count is 0
	 * @param investShares    whole shares of {@code investSymbol} the same money buys at the last close
	 */
	public record WishCard(Long categoryId, String category, String icon, BigDecimal spent, String productName,
			String productImage, BigDecimal productPrice, int count, Integer progressPercent, String investSymbol,
			Long investShares) {
	}

	public record MonthSummary(String month, BigDecimal total, BigDecimal lastMonthTotal, BigDecimal overallBudget,
			List<CategorySpend> byCategory, List<WishCard> couldHaveBought) {
	}
}
