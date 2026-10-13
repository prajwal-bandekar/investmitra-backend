package com.investmitra.expense;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request and response bodies for the Free to spend plan. */
public final class PlanDtos {

	private PlanDtos() {
	}

	/** @param month effective month, defaults to the current IST month */
	public record SalaryRequest(@Pattern(regexp = ExpenseDtos.MONTH_PATTERN) String month,
			@NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount) {
	}

	/** @param startMonth defaults to the current IST month; endMonth null means no end and must not precede startMonth */
	public record CommitmentRequest(@NotBlank @Size(max = 60) String name, @NotNull CommitmentKind kind,
			@NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
			@NotNull @Min(1) @Max(31) Integer dueDay,
			@Pattern(regexp = ExpenseDtos.MONTH_PATTERN) String startMonth,
			@Pattern(regexp = ExpenseDtos.MONTH_PATTERN) String endMonth) {
	}

	/** @param monthsLeft months from the requested (or current) month to endMonth inclusive, null with no end, 0 once ended */
	public record CommitmentView(Long id, String name, CommitmentKind kind, BigDecimal amount, int dueDay,
			String startMonth, String endMonth, Integer monthsLeft, boolean active) {

		static CommitmentView of(Commitment c, java.time.YearMonth month) {
			Integer left = null;
			if (c.getEndMonth() != null) {
				long n = java.time.temporal.ChronoUnit.MONTHS.between(month, java.time.YearMonth.parse(c.getEndMonth())) + 1;
				left = (int) Math.max(n, 0);
			}
			return new CommitmentView(c.getId(), c.getName(), c.getKind(), c.getAmount(), c.getDueDay(),
					c.getStartMonth(), c.getEndMonth(), left, c.activeIn(month.toString()));
		}
	}

	/** @param month defaults to the current IST month */
	public record ExtraIncomeRequest(@Pattern(regexp = ExpenseDtos.MONTH_PATTERN) String month,
			@NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
			@NotBlank @Size(max = 60) String label) {
	}

	public record ExtraIncomeView(Long id, String month, BigDecimal amount, String label) {

		static ExtraIncomeView of(ExtraIncome e) {
			return new ExtraIncomeView(e.getId(), e.getIncomeMonth(), e.getAmount(), e.getLabel());
		}
	}

	public record EndingSoon(Long id, String name, BigDecimal amount, String endMonth) {
	}

	/**
	 * The month at a glance. freeToSpend = totalIncome - committedTotal - savingsTotal and may be negative.
	 *
	 * @param salaryFromMonth effectiveMonth of the salary row used, null when no salary is set
	 * @param dailyAllowance  remaining / daysLeft rounded down to whole rupees, null when daysLeft is 0 or remaining <= 0
	 * @param committedPercent committedTotal as a share of totalIncome, null when totalIncome is 0
	 */
	public record PlanView(String month, BigDecimal salary, String salaryFromMonth, BigDecimal extraIncome,
			BigDecimal totalIncome, List<CommitmentView> commitments, BigDecimal committedTotal,
			BigDecimal savingsTotal, BigDecimal freeToSpend, BigDecimal spent, BigDecimal remaining, int daysLeft,
			BigDecimal dailyAllowance, Integer committedPercent, List<EndingSoon> endingSoon) {
	}
}
