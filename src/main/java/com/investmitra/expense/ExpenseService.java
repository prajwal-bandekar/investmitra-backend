package com.investmitra.expense;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.investmitra.entity.AppUser;
import com.investmitra.entity.StockPrice;
import com.investmitra.expense.ExpenseDtos.BudgetItem;
import com.investmitra.expense.ExpenseDtos.BudgetRequest;
import com.investmitra.expense.ExpenseDtos.BudgetView;
import com.investmitra.expense.ExpenseDtos.CategoryRequest;
import com.investmitra.expense.ExpenseDtos.CategorySpend;
import com.investmitra.expense.ExpenseDtos.CategoryView;
import com.investmitra.expense.ExpenseDtos.ExpenseRequest;
import com.investmitra.expense.ExpenseDtos.ExpenseView;
import com.investmitra.expense.ExpenseDtos.MonthSummary;
import com.investmitra.expense.ExpenseDtos.WishCard;
import com.investmitra.marketdata.MarketCalendar;
import com.investmitra.repository.StockRepository;
import com.investmitra.service.ApiException;
import com.investmitra.service.StockService;
import com.investmitra.service.UserService;

/** ExpenseMitra: real-life spending, budgets and could-have-bought cards. Every row belongs to one user. */
@Service
public class ExpenseService {

	private final ExpenseRepository expenses;
	private final ExpenseCategoryRepository categories;
	private final BudgetRepository budgets;
	private final WishProductRepository products;
	private final StockRepository stocks;
	private final StockService stockService;
	private final UserService users;
	private final Clock clock;
	private final String investSymbol;

	public ExpenseService(ExpenseRepository expenses, ExpenseCategoryRepository categories, BudgetRepository budgets,
			WishProductRepository products, StockRepository stocks, StockService stockService, UserService users,
			Clock clock, @Value("${expense.invest-symbol:RELIANCE}") String investSymbol) {
		this.expenses = expenses;
		this.categories = categories;
		this.budgets = budgets;
		this.products = products;
		this.stocks = stocks;
		this.stockService = stockService;
		this.users = users;
		this.clock = clock;
		this.investSymbol = investSymbol;
	}

	// ---- Categories ----

	@Transactional(readOnly = true)
	public List<CategoryView> categories(Long userId) {
		return categories.visibleTo(users.require(userId)).stream().map(CategoryView::of).toList();
	}

	@Transactional
	public CategoryView createCategory(Long userId, CategoryRequest request) {
		AppUser user = users.require(userId);
		String name = request.name().trim();
		boolean taken = categories.visibleTo(user).stream().anyMatch(c -> c.getName().equalsIgnoreCase(name));
		if (taken) {
			throw ApiException.conflict("You already have a category called " + name);
		}
		ExpenseCategory c = new ExpenseCategory();
		c.setUser(user);
		c.setName(name);
		c.setIcon(request.icon());
		c.setWant(Boolean.TRUE.equals(request.want()));
		return CategoryView.of(categories.save(c));
	}

	/** Renames or re-flags a custom category. Shared defaults cannot be changed. */
	@Transactional
	public CategoryView updateCategory(Long userId, Long id, CategoryRequest request) {
		ExpenseCategory c = requireOwnCategory(userId, id);
		c.setName(request.name().trim());
		if (request.icon() != null) {
			c.setIcon(request.icon());
		}
		if (request.want() != null) {
			c.setWant(request.want());
		}
		return CategoryView.of(c);
	}

	/** Hides a custom category; its past expenses keep it. */
	@Transactional
	public void archiveCategory(Long userId, Long id) {
		requireOwnCategory(userId, id).setArchived(true);
	}

	// ---- Expenses ----

	@Transactional(readOnly = true)
	public List<ExpenseView> list(Long userId, String month, Long categoryId, PaymentMode mode) {
		YearMonth ym = parseMonth(month);
		return expenses.find(users.require(userId), ym.atDay(1), ym.atEndOfMonth(), categoryId, mode).stream()
				.map(ExpenseView::of).toList();
	}

	@Transactional
	public ExpenseView create(Long userId, ExpenseRequest request) {
		AppUser user = users.require(userId);
		Expense e = new Expense();
		e.setUser(user);
		e.setCreatedAt(Instant.now(clock));
		e.setSource(request.source() == null ? ExpenseSource.MANUAL : request.source());
		apply(e, user, request);
		return ExpenseView.of(expenses.save(e));
	}

	@Transactional
	public ExpenseView update(Long userId, Long id, ExpenseRequest request) {
		Expense e = requireOwnExpense(userId, id);
		apply(e, e.getUser(), request);
		return ExpenseView.of(e);
	}

	@Transactional
	public void delete(Long userId, Long id) {
		expenses.delete(requireOwnExpense(userId, id));
	}

	private void apply(Expense e, AppUser user, ExpenseRequest request) {
		e.setAmount(request.amount().setScale(2, RoundingMode.HALF_UP));
		e.setCategory(requireUsableCategory(user, request.categoryId()));
		e.setSpentOn(request.spentOn() == null ? today() : request.spentOn());
		e.setPaymentMode(request.paymentMode() == null ? PaymentMode.UPI : request.paymentMode());
		e.setNote(request.note() == null || request.note().isBlank() ? null : request.note().trim());
	}

	// ---- Budgets ----

	@Transactional(readOnly = true)
	public List<BudgetView> budgets(Long userId, String month) {
		String key = parseMonth(month).toString();
		return budgets.findByUserAndMonth(users.require(userId), key).stream()
				.map(b -> new BudgetView(b.getCategory() == null ? null : b.getCategory().getId(),
						b.getCategory() == null ? null : b.getCategory().getName(), b.getLimitAmount()))
				.toList();
	}

	/** Upserts the listed budgets; a null or zero limit removes one. Budgets not listed are left alone. */
	@Transactional
	public List<BudgetView> saveBudgets(Long userId, BudgetRequest request) {
		AppUser user = users.require(userId);
		String month = parseMonth(request.month()).toString();
		Map<Long, Budget> existing = new HashMap<>();
		for (Budget b : budgets.findByUserAndMonth(user, month)) {
			existing.put(b.getCategory() == null ? null : b.getCategory().getId(), b);
		}
		for (BudgetItem item : request.items()) {
			Budget b = existing.get(item.categoryId());
			boolean remove = item.limit() == null || item.limit().signum() == 0;
			if (remove) {
				if (b != null) {
					budgets.delete(b);
					existing.remove(item.categoryId());
				}
				continue;
			}
			if (b == null) {
				b = new Budget();
				b.setUser(user);
				b.setMonth(month);
				b.setCategory(item.categoryId() == null ? null : requireUsableCategory(user, item.categoryId()));
				existing.put(item.categoryId(), b);
			}
			b.setLimitAmount(item.limit().setScale(2, RoundingMode.HALF_UP));
			budgets.save(b);
		}
		return budgets(userId, month);
	}

	// ---- Summary ----

	/** Month totals by category against budgets, plus could-have-bought cards from year-to-date "want" spend. */
	@Transactional(readOnly = true)
	public MonthSummary summary(Long userId, String month) {
		AppUser user = users.require(userId);
		YearMonth ym = parseMonth(month);
		List<ExpenseCategory> visible = categories.visibleTo(user);

		Map<Long, Object[]> monthTotals = totals(user, ym.atDay(1), ym.atEndOfMonth());
		Map<Long, BigDecimal> limits = new HashMap<>();
		for (Budget b : budgets.findByUserAndMonth(user, ym.toString())) {
			limits.put(b.getCategory() == null ? null : b.getCategory().getId(), b.getLimitAmount());
		}

		List<CategorySpend> byCategory = new ArrayList<>();
		for (ExpenseCategory c : visible) {
			Object[] row = monthTotals.get(c.getId());
			BigDecimal spent = row == null ? BigDecimal.ZERO.setScale(2) : (BigDecimal) row[1];
			long count = row == null ? 0 : ((Number) row[2]).longValue();
			byCategory.add(new CategorySpend(c.getId(), c.getName(), c.getIcon(), c.isWant(), spent, count,
					limits.get(c.getId())));
		}
		byCategory.sort(Comparator.comparing(CategorySpend::spent).reversed());

		BigDecimal total = total(user, ym);
		BigDecimal lastMonth = total(user, ym.minusMonths(1));

		return new MonthSummary(ym.toString(), total, lastMonth, limits.get(null), byCategory,
				wishCards(user, ym, visible));
	}

	private List<WishCard> wishCards(AppUser user, YearMonth ym, List<ExpenseCategory> visible) {
		List<WishProduct> catalog = products.findByActiveTrueOrderByPriceAsc();
		if (catalog.isEmpty()) {
			return List.of();
		}
		Map<Long, Object[]> ytd = totals(user, ym.withMonth(1).atDay(1), ym.atEndOfMonth());
		BigDecimal investPrice = investPrice();

		List<WishCard> cards = new ArrayList<>();
		for (ExpenseCategory c : visible) {
			Object[] row = ytd.get(c.getId());
			if (!c.isWant() || row == null) {
				continue;
			}
			BigDecimal spent = (BigDecimal) row[1];
			cards.add(card(c, spent, catalog, investPrice));
		}
		cards.sort(Comparator.comparing(WishCard::spent).reversed());
		return cards;
	}

	/**
	 * Picks the priciest product the spend covers. When it covers none, shows progress towards the cheapest.
	 * Package-private for unit tests.
	 */
	WishCard card(ExpenseCategory c, BigDecimal spent, List<WishProduct> cheapestFirst, BigDecimal investPrice) {
		WishProduct pick = null;
		for (WishProduct p : cheapestFirst) {
			if (p.getPrice().compareTo(spent) <= 0) {
				pick = p;
			}
		}
		int count;
		Integer progress = null;
		if (pick == null) {
			pick = cheapestFirst.get(0);
			count = 0;
			progress = spent.multiply(BigDecimal.valueOf(100)).divide(pick.getPrice(), 0, RoundingMode.DOWN).intValue();
		}
		else {
			count = spent.divide(pick.getPrice(), 0, RoundingMode.DOWN).intValue();
		}
		Long shares = investPrice == null ? null
				: spent.divide(investPrice, 0, RoundingMode.DOWN).longValue();
		return new WishCard(c.getId(), c.getName(), c.getIcon(), spent, pick.getName(), pick.getImage(),
				pick.getPrice(), count, progress, investPrice == null ? null : investSymbol, shares);
	}

	private BigDecimal investPrice() {
		return stocks.findBySymbolIgnoreCase(investSymbol)
				.map(s -> stockService.latestPrices(List.of(s.getId())).get(s.getId()))
				.map(StockPrice::getClose)
				.filter(p -> p.signum() > 0)
				.orElse(null);
	}

	private BigDecimal total(AppUser user, YearMonth ym) {
		BigDecimal sum = expenses.total(user, ym.atDay(1), ym.atEndOfMonth());
		return sum == null ? BigDecimal.ZERO.setScale(2) : sum;
	}

	private Map<Long, Object[]> totals(AppUser user, LocalDate from, LocalDate to) {
		Map<Long, Object[]> map = new HashMap<>();
		for (Object[] row : expenses.totalsByCategory(user, from, to)) {
			map.put((Long) row[0], row);
		}
		return map;
	}

	// ---- Helpers ----

	private ExpenseCategory requireUsableCategory(AppUser user, Long id) {
		ExpenseCategory c = categories.findById(id)
				.orElseThrow(() -> ApiException.notFound("Unknown category: " + id));
		if (c.getUser() != null && !Objects.equals(c.getUser().getId(), user.getId())) {
			throw ApiException.notFound("Unknown category: " + id);
		}
		if (c.isArchived()) {
			throw ApiException.badRequest("Category " + c.getName() + " is archived");
		}
		return c;
	}

	private ExpenseCategory requireOwnCategory(Long userId, Long id) {
		ExpenseCategory c = categories.findById(id)
				.orElseThrow(() -> ApiException.notFound("Unknown category: " + id));
		if (c.getUser() == null) {
			throw ApiException.forbidden("Default categories can't be changed");
		}
		if (!c.getUser().getId().equals(userId)) {
			throw ApiException.notFound("Unknown category: " + id);
		}
		return c;
	}

	/** Someone else's expense answers 404, so ids can't be probed. */
	private Expense requireOwnExpense(Long userId, Long id) {
		users.require(userId);
		Expense e = expenses.findById(id).orElseThrow(() -> ApiException.notFound("Unknown expense: " + id));
		if (!e.getUser().getId().equals(userId)) {
			throw ApiException.notFound("Unknown expense: " + id);
		}
		return e;
	}

	private LocalDate today() {
		return LocalDate.now(clock.withZone(MarketCalendar.IST));
	}

	/** Null or blank means the current month (IST). */
	private YearMonth parseMonth(String month) {
		if (month == null || month.isBlank()) {
			return YearMonth.from(today());
		}
		try {
			return YearMonth.parse(month.trim());
		}
		catch (DateTimeParseException ex) {
			throw ApiException.badRequest("Month must look like 2026-10");
		}
	}
}
