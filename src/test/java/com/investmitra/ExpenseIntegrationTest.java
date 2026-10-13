package com.investmitra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.investmitra.entity.Role;
import com.investmitra.expense.CommitmentRepository;
import com.investmitra.expense.ExtraIncomeRepository;
import com.investmitra.expense.SalarySettingRepository;
import com.investmitra.entity.StockPrice;
import com.investmitra.expense.BudgetRepository;
import com.investmitra.expense.ExpenseCategoryRepository;
import com.investmitra.expense.ExpenseDtos.BudgetItem;
import com.investmitra.expense.ExpenseDtos.BudgetRequest;
import com.investmitra.expense.ExpenseDtos.CategoryRequest;
import com.investmitra.expense.ExpenseDtos.CategorySpend;
import com.investmitra.expense.ExpenseDtos.CategoryView;
import com.investmitra.expense.ExpenseDtos.ExpenseRequest;
import com.investmitra.expense.ExpenseDtos.ExpenseView;
import com.investmitra.expense.ExpenseDtos.MonthSummary;
import com.investmitra.expense.ExpenseDtos.WishCard;
import com.investmitra.expense.ExpenseRepository;
import com.investmitra.expense.ExpenseService;
import com.investmitra.expense.PaymentMode;
import com.investmitra.marketdata.AlphaVantageClient;
import com.investmitra.marketdata.MarketCalendar;
import com.investmitra.repository.AppUserRepository;
import com.investmitra.repository.HoldingRepository;
import com.investmitra.repository.PortfolioRepository;
import com.investmitra.repository.StockPriceRepository;
import com.investmitra.repository.TradeRepository;
import com.investmitra.repository.WatchlistItemRepository;
import com.investmitra.service.ApiException;
import com.investmitra.service.StockService;
import com.investmitra.service.UserService;
import com.investmitra.web.dto.Dtos.CreateUserRequest;

/** ExpenseMitra against in-memory H2. Same context setup as InvestMitraIntegrationTest so Spring reuses it. */
@SpringBootTest
class ExpenseIntegrationTest {

	@MockitoBean
	AlphaVantageClient alphaVantage;

	@Autowired ExpenseService expenses;
	@Autowired UserService users;
	@Autowired StockService stockService;
	@Autowired MarketCalendar calendar;
	@Autowired ExpenseRepository expenseRepo;
	@Autowired BudgetRepository budgetRepo;
	@Autowired ExpenseCategoryRepository categoryRepo;
	@Autowired AppUserRepository userRepo;
	@Autowired CommitmentRepository commitmentRepo;
	@Autowired ExtraIncomeRepository extraIncomeRepo;
	@Autowired SalarySettingRepository salaryRepo;
	@Autowired StockPriceRepository priceRepo;
	@Autowired TradeRepository tradeRepo;
	@Autowired HoldingRepository holdingRepo;
	@Autowired WatchlistItemRepository watchlistRepo;
	@Autowired PortfolioRepository portfolioRepo;

	Long me;
	Long other;

	@BeforeEach
	void setUp() {
		cleanUp();
		me = users.create(new CreateUserRequest("Me", "me@x.dev", "secret123", Role.USER)).id();
		other = users.create(new CreateUserRequest("Other", "other@x.dev", "secret123", Role.USER)).id();
	}

	/** Runs after each test too, so the shared H2 database is clean for the trading tests. */
	@AfterEach
	void cleanUp() {
		expenseRepo.deleteAll();
		budgetRepo.deleteAll();
		categoryRepo.deleteAll(categoryRepo.findAll().stream().filter(c -> c.getUser() != null).toList());
		tradeRepo.deleteAll();
		holdingRepo.deleteAll();
		watchlistRepo.deleteAll();
		portfolioRepo.deleteAll();
		priceRepo.deleteAll();
		commitmentRepo.deleteAll();
		extraIncomeRepo.deleteAll();
		salaryRepo.deleteAll();
		userRepo.deleteAll();
	}

	@Test
	void defaultCategoriesAreSeeded() {
		List<CategoryView> list = expenses.categories(me);
		assertThat(list).extracting(CategoryView::name).contains("Food delivery", "Rent", "Other");
		assertThat(list).filteredOn(CategoryView::want).extracting(CategoryView::name)
				.containsExactlyInAnyOrder("Food delivery", "Eating out", "Shopping", "Entertainment");
	}

	@Test
	void logListAndDefaults() {
		ExpenseView e = expenses.create(me, new ExpenseRequest(new BigDecimal("250"), cat("Food delivery"), null, null,
				"  Swiggy ", null));
		assertThat(e.amount()).isEqualByComparingTo("250.00");
		assertThat(e.paymentMode()).isEqualTo(PaymentMode.UPI);
		assertThat(e.note()).isEqualTo("Swiggy");
		assertThat(e.spentOn()).isEqualTo(today());

		assertThat(expenses.list(me, null, null, null)).hasSize(1);
		assertThat(expenses.list(other, null, null, null)).isEmpty();
		assertThat(expenses.list(me, null, cat("Rent"), null)).isEmpty();
	}

	@Test
	void cannotTouchSomeoneElsesExpenseOrCategory() {
		ExpenseView e = expenses.create(me, new ExpenseRequest(BigDecimal.TEN, cat("Other"), null, null, null, null));
		assertStatus(() -> expenses.delete(other, e.id()), HttpStatus.NOT_FOUND);

		CategoryView mine = expenses.createCategory(me, new CategoryRequest("Gym", "💪", false));
		assertStatus(() -> expenses.create(other, new ExpenseRequest(BigDecimal.TEN, mine.id(), null, null, null, null)),
				HttpStatus.NOT_FOUND);
		assertStatus(() -> expenses.updateCategory(me, cat("Rent"), new CategoryRequest("Hostel", null, null)),
				HttpStatus.FORBIDDEN);
		assertStatus(() -> expenses.updateCategory(me, mine.id(), new CategoryRequest("rent", null, null)),
				HttpStatus.CONFLICT);
	}

	@Test
	void summaryTotalsBudgetsAndLastMonth() {
		LocalDate today = today();
		String month = today.toString().substring(0, 7);
		expenses.create(me, new ExpenseRequest(new BigDecimal("300"), cat("Food delivery"), today, null, null, null));
		expenses.create(me, new ExpenseRequest(new BigDecimal("200"), cat("Food delivery"), today, null, null, null));
		expenses.create(me, new ExpenseRequest(new BigDecimal("1000"), cat("Rent"), today.minusMonths(1), null, null,
				null));
		expenses.saveBudgets(me, new BudgetRequest(month, List.of(new BudgetItem(cat("Food delivery"),
				new BigDecimal("400")), new BudgetItem(null, new BigDecimal("5000")))));

		MonthSummary s = expenses.summary(me, month);
		assertThat(s.total()).isEqualByComparingTo("500");
		assertThat(s.lastMonthTotal()).isEqualByComparingTo("1000");
		assertThat(s.overallBudget()).isEqualByComparingTo("5000");
		CategorySpend food = s.byCategory().get(0);
		assertThat(food.name()).isEqualTo("Food delivery");
		assertThat(food.count()).isEqualTo(2);
		assertThat(food.budget()).isEqualByComparingTo("400");

		// zero limit removes the budget
		expenses.saveBudgets(me, new BudgetRequest(month, List.of(new BudgetItem(null, BigDecimal.ZERO))));
		assertThat(expenses.budgets(me, month)).hasSize(1);
	}

	@Test
	void couldHaveBoughtPicksPriciestAffordableProduct() {
		savePrice("RELIANCE", "1500");
		LocalDate today = today();
		expenses.create(me, new ExpenseRequest(new BigDecimal("60000"), cat("Food delivery"), today, null, null, null));
		expenses.create(me, new ExpenseRequest(new BigDecimal("500"), cat("Shopping"), today, null, null, null));
		expenses.create(me, new ExpenseRequest(new BigDecimal("90000"), cat("Rent"), today, null, null, null));

		List<WishCard> cards = expenses.summary(me, null).couldHaveBought();
		assertThat(cards).extracting(WishCard::category).containsExactly("Food delivery", "Shopping");

		WishCard food = cards.get(0);
		assertThat(food.productName()).isEqualTo("PS5");
		assertThat(food.count()).isEqualTo(1);
		assertThat(food.progressPercent()).isNull();
		assertThat(food.investSymbol()).isEqualTo("RELIANCE");
		assertThat(food.investShares()).isEqualTo(40);

		WishCard shopping = cards.get(1);
		assertThat(shopping.count()).isZero();
		assertThat(shopping.productName()).isEqualTo("movie night for two");
		assertThat(shopping.progressPercent()).isEqualTo(50);
	}

	@Test
	void expenseKeepsItsArchivedCategoryOnEditAndStillShowsInSummary() {
		LocalDate today = today();
		CategoryView gym = expenses.createCategory(me, new CategoryRequest("Gym", "x", true));
		ExpenseView e = expenses.create(me, new ExpenseRequest(new BigDecimal("800"), gym.id(), today, null, null, null));
		expenses.create(me, new ExpenseRequest(new BigDecimal("300"), cat("Rent"), today, null, null, null));
		expenses.archiveCategory(me, gym.id());

		ExpenseView edited = expenses.update(me, e.id(),
				new ExpenseRequest(new BigDecimal("900"), gym.id(), today, null, "edited", null));
		assertThat(edited.amount()).isEqualByComparingTo("900");
		assertStatus(() -> expenses.create(me, new ExpenseRequest(BigDecimal.TEN, gym.id(), today, null, null, null)),
				HttpStatus.BAD_REQUEST);

		MonthSummary s = expenses.summary(me, null);
		assertThat(s.byCategory().stream().map(CategorySpend::spent).reduce(BigDecimal.ZERO, BigDecimal::add))
				.isEqualByComparingTo(s.total());
		CategorySpend archived = s.byCategory().stream().filter(c -> c.name().equals("Gym")).findFirst().orElseThrow();
		assertThat(archived.archived()).isTrue();
		assertThat(archived.spent()).isEqualByComparingTo("900");
		assertThat(s.couldHaveBought()).extracting(WishCard::category).contains("Gym");

		// Changing to another archived category is still refused.
		CategoryView club = expenses.createCategory(me, new CategoryRequest("Club", "x", true));
		expenses.archiveCategory(me, club.id());
		assertStatus(() -> expenses.update(me, e.id(),
				new ExpenseRequest(new BigDecimal("900"), club.id(), today, null, null, null)), HttpStatus.BAD_REQUEST);
	}

	@Test
	void spentOnMustBeWithinTwoYearsAndTomorrow() {
		LocalDate today = today();
		Long food = cat("Food delivery");
		expenses.create(me, new ExpenseRequest(BigDecimal.TEN, food, today.plusDays(1), null, null, null));
		expenses.create(me, new ExpenseRequest(BigDecimal.TEN, food, today.minusYears(2), null, null, null));
		assertStatus(() -> expenses.create(me, new ExpenseRequest(BigDecimal.TEN, food, today.plusDays(2), null, null,
				null)), HttpStatus.BAD_REQUEST);
		assertStatus(() -> expenses.create(me, new ExpenseRequest(BigDecimal.TEN, food, today.minusYears(2).minusDays(1),
				null, null, null)), HttpStatus.BAD_REQUEST);
	}

	@Test
	void savingTheOverallBudgetTwiceUpdatesTheSameRow() {
		String month = today().toString().substring(0, 7);
		expenses.saveBudgets(me, new BudgetRequest(month, List.of(new BudgetItem(null, new BigDecimal("100")))));
		expenses.saveBudgets(me, new BudgetRequest(month, List.of(new BudgetItem(null, new BigDecimal("200")),
				new BudgetItem(null, new BigDecimal("300")))));
		assertThat(expenses.budgets(me, month)).hasSize(1);
		assertThat(expenses.budgets(me, month).get(0).limit()).isEqualByComparingTo("300");
	}

	@Test
	void staleInvestPriceGivesNoShareCount() {
		StockPrice p = savePrice("RELIANCE", "1500");
		expenses.create(me, new ExpenseRequest(new BigDecimal("500"), cat("Shopping"), today(), null, null, null));
		assertThat(expenses.summary(me, null).couldHaveBought().get(0).investShares()).isNotNull();

		p.setTradeDate(calendar.lastCompletedTradingDay().minusDays(30));
		priceRepo.save(p);
		WishCard card = expenses.summary(me, null).couldHaveBought().get(0);
		assertThat(card.investShares()).isNull();
		assertThat(card.investSymbol()).isNull();
	}

	@Test
	void badMonthIsRejected() {
		assertStatus(() -> expenses.summary(me, "Oct-2026"), HttpStatus.BAD_REQUEST);
	}

	private Long cat(String name) {
		return expenses.categories(me).stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow().id();
	}

	private LocalDate today() {
		return LocalDate.now(MarketCalendar.IST);
	}

	private void assertStatus(Runnable call, HttpStatus status) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class,
				e -> assertThat(e.getStatus()).isEqualTo(status));
	}

	private StockPrice savePrice(String symbol, String close) {
		StockPrice p = new StockPrice();
		p.setStock(stockService.require(symbol));
		p.setTradeDate(calendar.lastCompletedTradingDay());
		p.setClose(new BigDecimal(close));
		p.setPreviousClose(new BigDecimal(close));
		p.setFetchedAt(Instant.now());
		return priceRepo.save(p);
	}
}
