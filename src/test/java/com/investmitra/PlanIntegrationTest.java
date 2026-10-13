package com.investmitra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.investmitra.entity.Role;
import com.investmitra.expense.CommitmentKind;
import com.investmitra.expense.CommitmentRepository;
import com.investmitra.expense.ExpenseCategoryRepository;
import com.investmitra.expense.ExpenseDtos.ExpenseRequest;
import com.investmitra.expense.ExpenseRepository;
import com.investmitra.expense.ExpenseService;
import com.investmitra.expense.ExtraIncomeRepository;
import com.investmitra.expense.PlanDtos.CommitmentRequest;
import com.investmitra.expense.PlanDtos.CommitmentView;
import com.investmitra.expense.PlanDtos.ExtraIncomeRequest;
import com.investmitra.expense.PlanDtos.ExtraIncomeView;
import com.investmitra.expense.PlanDtos.PlanView;
import com.investmitra.expense.PlanDtos.SalaryRequest;
import com.investmitra.expense.PlanService;
import com.investmitra.expense.SalarySettingRepository;
import com.investmitra.marketdata.AlphaVantageClient;
import com.investmitra.marketdata.MarketCalendar;
import com.investmitra.repository.AppUserRepository;
import com.investmitra.repository.HoldingRepository;
import com.investmitra.repository.PortfolioRepository;
import com.investmitra.repository.StockPriceRepository;
import com.investmitra.repository.TradeRepository;
import com.investmitra.repository.WatchlistItemRepository;
import com.investmitra.service.ApiException;
import com.investmitra.service.UserService;
import com.investmitra.web.dto.Dtos.CreateUserRequest;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

/** Free to spend against in-memory H2. Same context setup as ExpenseIntegrationTest so Spring reuses it. */
@SpringBootTest
class PlanIntegrationTest {

	@MockitoBean
	AlphaVantageClient alphaVantage;

	@Autowired PlanService plan;
	@Autowired ExpenseService expenses;
	@Autowired UserService users;
	@Autowired SalarySettingRepository salaryRepo;
	@Autowired ExtraIncomeRepository extraRepo;
	@Autowired CommitmentRepository commitmentRepo;
	@Autowired ExpenseRepository expenseRepo;
	@Autowired ExpenseCategoryRepository categoryRepo;
	@Autowired AppUserRepository userRepo;
	@Autowired PortfolioRepository portfolioRepo;
	@Autowired TradeRepository tradeRepo;
	@Autowired HoldingRepository holdingRepo;
	@Autowired WatchlistItemRepository watchlistRepo;
	@Autowired StockPriceRepository priceRepo;

	final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
	final YearMonth now = YearMonth.from(LocalDate.now(MarketCalendar.IST));

	Long me;
	Long other;

	@BeforeEach
	void setUp() {
		cleanUp();
		me = users.create(new CreateUserRequest("Me", "me@x.dev", "secret123", Role.USER)).id();
		other = users.create(new CreateUserRequest("Other", "other@x.dev", "secret123", Role.USER)).id();
	}

	@AfterEach
	void cleanUp() {
		expenseRepo.deleteAll();
		salaryRepo.deleteAll();
		extraRepo.deleteAll();
		commitmentRepo.deleteAll();
		categoryRepo.deleteAll(categoryRepo.findAll().stream().filter(c -> c.getUser() != null).toList());
		tradeRepo.deleteAll();
		holdingRepo.deleteAll();
		watchlistRepo.deleteAll();
		portfolioRepo.deleteAll();
		priceRepo.deleteAll();
		userRepo.deleteAll();
	}

	@Test
	void noSalaryMeansNullSalaryAndZeroIncome() {
		PlanView p = plan.plan(me, null);
		assertThat(p.month()).isEqualTo(now.toString());
		assertThat(p.salary()).isNull();
		assertThat(p.salaryFromMonth()).isNull();
		assertThat(p.totalIncome()).isEqualByComparingTo("0");
		assertThat(p.committedPercent()).isNull();
		assertThat(p.dailyAllowance()).isNull();
	}

	@Test
	void salaryAppliesFromItsMonthOnward() {
		plan.setSalary(me, new SalaryRequest("2026-01", new BigDecimal("50000")));
		PlanView changed = plan.setSalary(me, new SalaryRequest("2026-07", new BigDecimal("65000.555")));
		assertThat(changed.salary()).isEqualByComparingTo("65000.56");
		assertThat(changed.salaryFromMonth()).isEqualTo("2026-07");

		assertThat(plan.plan(me, "2025-12").salary()).isNull();
		assertThat(plan.plan(me, "2026-06").salary()).isEqualByComparingTo("50000");
		assertThat(plan.plan(me, "2026-06").salaryFromMonth()).isEqualTo("2026-01");
		assertThat(plan.plan(me, "2027-03").salary()).isEqualByComparingTo("65000.56");

		// Same month again replaces rather than adds a row.
		plan.setSalary(me, new SalaryRequest("2026-07", new BigDecimal("70000")));
		assertThat(salaryRepo.count()).isEqualTo(2);
		assertThat(plan.plan(me, "2026-08").salary()).isEqualByComparingTo("70000");
		assertThat(plan.plan(other, "2026-08").salary()).isNull();
	}

	@Test
	void commitmentIsActiveOnlyInsideItsWindow() {
		CommitmentView c = plan.createCommitment(me, commitment("Bajaj EMI", CommitmentKind.EMI, "4500", "2026-03", "2026-08"));
		assertThat(c.amount()).isEqualByComparingTo("4500.00");
		assertThat(plan.plan(me, "2026-02").commitments()).isEmpty();
		assertThat(plan.plan(me, "2026-03").commitments()).hasSize(1);
		assertThat(plan.plan(me, "2026-08").commitments()).hasSize(1);
		assertThat(plan.plan(me, "2026-09").commitments()).isEmpty();

		CommitmentView inMarch = plan.plan(me, "2026-06").commitments().get(0);
		assertThat(inMarch.monthsLeft()).isEqualTo(3);
		assertThat(inMarch.active()).isTrue();

		plan.createCommitment(me, commitment("Netflix", CommitmentKind.SUBSCRIPTION, "199", "2026-01", null));
		assertThat(plan.plan(me, "2026-12").commitments()).extracting(CommitmentView::name).containsExactly("Netflix");
		assertThat(plan.plan(me, "2026-12").commitments().get(0).monthsLeft()).isNull();
	}

	@Test
	void startMonthDefaultsToCurrentAndListIsNewestFirst() {
		CommitmentView c = plan.createCommitment(me, commitment("Gym", CommitmentKind.OTHER, "1000", null, null));
		assertThat(c.startMonth()).isEqualTo(now.toString());
		assertThat(c.active()).isTrue();
		plan.createCommitment(me, commitment("Old loan", CommitmentKind.EMI, "2000", "2025-01", "2025-06"));
		List<CommitmentView> all = plan.commitments(me);
		assertThat(all).extracting(CommitmentView::name).containsExactly("Gym", "Old loan");
		assertThat(all.get(1).active()).isFalse();
		assertThat(all.get(1).monthsLeft()).isZero();
		assertThat(plan.commitments(other)).isEmpty();
	}

	@Test
	void savingsAreSetAsideNotCommitted() {
		plan.setSalary(me, new SalaryRequest("2026-01", new BigDecimal("50000")));
		plan.createCommitment(me, commitment("Rent", CommitmentKind.RENT, "15000", "2026-01", null));
		plan.createCommitment(me, commitment("SIP", CommitmentKind.SAVINGS, "5000", "2026-01", null));
		PlanView p = plan.plan(me, "2026-02");
		assertThat(p.committedTotal()).isEqualByComparingTo("15000");
		assertThat(p.savingsTotal()).isEqualByComparingTo("5000");
		assertThat(p.freeToSpend()).isEqualByComparingTo("30000");
		assertThat(p.committedPercent()).isEqualTo(30);
	}

	@Test
	void freeToSpendRemainingAndDailyAllowance() {
		String month = now.toString();
		plan.setSalary(me, new SalaryRequest(month, new BigDecimal("40000")));
		plan.addExtraIncome(me, new ExtraIncomeRequest(month, new BigDecimal("10000"), "Bonus"));
		plan.createCommitment(me, commitment("Rent", CommitmentKind.RENT, "12000", month, null));
		plan.createCommitment(me, commitment("SIP", CommitmentKind.SAVINGS, "3000", month, null));
		Long food = expenses.categories(me).stream().filter(c -> c.name().equals("Food delivery")).findFirst()
				.orElseThrow().id();
		expenses.create(me, new ExpenseRequest(new BigDecimal("1234.50"), food, LocalDate.now(MarketCalendar.IST),
				null, null, null));

		PlanView p = plan.plan(me, month);
		assertThat(p.totalIncome()).isEqualByComparingTo("50000");
		assertThat(p.freeToSpend()).isEqualByComparingTo("35000");
		assertThat(p.spent()).isEqualByComparingTo("1234.50");
		assertThat(p.remaining()).isEqualByComparingTo("33765.50");
		int daysLeft = now.lengthOfMonth() - LocalDate.now(MarketCalendar.IST).getDayOfMonth() + 1;
		assertThat(p.daysLeft()).isEqualTo(daysLeft);
		assertThat(p.dailyAllowance()).isEqualByComparingTo(
				new BigDecimal("33765.50").divide(BigDecimal.valueOf(daysLeft), 0, java.math.RoundingMode.DOWN));
		assertThat(p.dailyAllowance().scale()).isZero();
		assertThat(p.committedPercent()).isEqualTo(24);
	}

	@Test
	void negativeRemainingHasNoDailyAllowanceAndPastFutureDays() {
		String month = now.toString();
		plan.setSalary(me, new SalaryRequest(month, new BigDecimal("1000")));
		plan.createCommitment(me, commitment("Rent", CommitmentKind.RENT, "1500", month, null));
		PlanView p = plan.plan(me, month);
		assertThat(p.freeToSpend()).isEqualByComparingTo("-500");
		assertThat(p.remaining()).isEqualByComparingTo("-500");
		assertThat(p.dailyAllowance()).isNull();

		assertThat(plan.plan(me, now.minusMonths(1).toString()).daysLeft()).isZero();
		assertThat(plan.plan(me, now.minusMonths(1).toString()).dailyAllowance()).isNull();
		YearMonth next = now.plusMonths(1);
		assertThat(plan.plan(me, next.toString()).daysLeft()).isEqualTo(next.lengthOfMonth());
	}

	@Test
	void endingSoonCoversTheNextThreeMonths() {
		plan.createCommitment(me, commitment("Soon", CommitmentKind.EMI, "4500", "2026-01", "2026-05"));
		plan.createCommitment(me, commitment("Edge", CommitmentKind.BNPL, "700", "2026-01", "2026-06"));
		plan.createCommitment(me, commitment("Later", CommitmentKind.INSURANCE, "900", "2026-01", "2026-07"));
		plan.createCommitment(me, commitment("Forever", CommitmentKind.RENT, "9000", "2026-01", null));
		PlanView p = plan.plan(me, "2026-04");
		assertThat(p.endingSoon()).extracting(e -> e.name()).containsExactlyInAnyOrder("Soon", "Edge");
		assertThat(p.endingSoon()).filteredOn(e -> e.name().equals("Soon")).singleElement()
				.satisfies(e -> {
					assertThat(e.endMonth()).isEqualTo("2026-05");
					assertThat(e.amount()).isEqualByComparingTo("4500");
				});
	}

	@Test
	void extraIncomeIsPerMonthAndAddsToIncome() {
		ExtraIncomeView a = plan.addExtraIncome(me, new ExtraIncomeRequest("2026-03", new BigDecimal("2500"), " Freelance "));
		plan.addExtraIncome(me, new ExtraIncomeRequest(null, new BigDecimal("100"), "Gift"));
		assertThat(a.label()).isEqualTo("Freelance");
		assertThat(plan.extraIncome(me, "2026-03")).hasSize(1);
		assertThat(plan.extraIncome(me, null)).extracting(ExtraIncomeView::label).containsExactly("Gift");
		assertThat(plan.plan(me, "2026-03").extraIncome()).isEqualByComparingTo("2500");
		assertThat(plan.plan(me, "2026-03").totalIncome()).isEqualByComparingTo("2500");
		assertThat(plan.plan(me, "2026-04").extraIncome()).isEqualByComparingTo("0");
		assertThat(plan.extraIncome(other, "2026-03")).isEmpty();

		plan.deleteExtraIncome(me, a.id());
		assertThat(plan.extraIncome(me, "2026-03")).isEmpty();
	}

	@Test
	void cannotTouchSomeoneElsesRows() {
		CommitmentView c = plan.createCommitment(me, commitment("Rent", CommitmentKind.RENT, "100", null, null));
		ExtraIncomeView e = plan.addExtraIncome(me, new ExtraIncomeRequest(null, BigDecimal.TEN, "Gift"));
		assertStatus(() -> plan.updateCommitment(other, c.id(), commitment("X", CommitmentKind.OTHER, "1", null, null)),
				HttpStatus.NOT_FOUND);
		assertStatus(() -> plan.deleteCommitment(other, c.id()), HttpStatus.NOT_FOUND);
		assertStatus(() -> plan.deleteExtraIncome(other, e.id()), HttpStatus.NOT_FOUND);
		assertStatus(() -> plan.deleteCommitment(me, 999_999L), HttpStatus.NOT_FOUND);

		CommitmentView updated = plan.updateCommitment(me, c.id(), commitment("Hostel", CommitmentKind.RENT, "250", null, null));
		assertThat(updated.name()).isEqualTo("Hostel");
		plan.deleteCommitment(me, c.id());
		assertThat(plan.commitments(me)).isEmpty();
	}

	@Test
	void businessValidationAnswers400() {
		assertStatus(() -> plan.createCommitment(me, commitment("Bad", CommitmentKind.EMI, "10", "2026-05", "2026-04")),
				HttpStatus.BAD_REQUEST);
		assertStatus(() -> plan.plan(me, "2026-13x"), HttpStatus.BAD_REQUEST);
		assertStatus(() -> plan.plan(me, "nonsense"), HttpStatus.BAD_REQUEST);
	}

	@Test
	void requestValidationRejectsBadFields() {
		assertThat(validator.validate(commitment("Ok", CommitmentKind.EMI, "10", "2026-05", null))).isEmpty();
		assertThat(validator.validate(commitmentDay(0))).hasSize(1);
		assertThat(validator.validate(commitmentDay(32))).hasSize(1);
		assertThat(validator.validate(commitment("Zero", CommitmentKind.EMI, "0", null, null))).hasSize(1);
		assertThat(validator.validate(commitment(" ", CommitmentKind.EMI, "10", null, null))).hasSize(1);
		assertThat(validator.validate(commitment("x".repeat(61), CommitmentKind.EMI, "10", null, null))).hasSize(1);
		assertThat(validator.validate(commitment("Bad month", CommitmentKind.EMI, "10", "2026-13", null))).hasSize(1);
		assertThat(validator.validate(new SalaryRequest("2026-01", BigDecimal.ZERO))).hasSize(1);
		assertThat(validator.validate(new SalaryRequest("2026-01", new BigDecimal("1")))).isEmpty();
		assertThat(validator.validate(new ExtraIncomeRequest(null, BigDecimal.ZERO, "Gift"))).hasSize(1);
		assertThat(validator.validate(new ExtraIncomeRequest(null, BigDecimal.ONE, " "))).hasSize(1);
	}

	private CommitmentRequest commitment(String name, CommitmentKind kind, String amount, String start, String end) {
		return new CommitmentRequest(name, kind, new BigDecimal(amount), 5, start, end);
	}

	private CommitmentRequest commitmentDay(int day) {
		return new CommitmentRequest("Day", CommitmentKind.OTHER, BigDecimal.TEN, day, null, null);
	}

	private void assertStatus(Runnable call, HttpStatus status) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class,
				e -> assertThat(e.getStatus()).isEqualTo(status));
	}
}
