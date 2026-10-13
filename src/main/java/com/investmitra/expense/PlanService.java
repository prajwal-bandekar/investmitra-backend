package com.investmitra.expense;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.investmitra.entity.AppUser;
import com.investmitra.expense.PlanDtos.CommitmentRequest;
import com.investmitra.expense.PlanDtos.CommitmentView;
import com.investmitra.expense.PlanDtos.EndingSoon;
import com.investmitra.expense.PlanDtos.ExtraIncomeRequest;
import com.investmitra.expense.PlanDtos.ExtraIncomeView;
import com.investmitra.expense.PlanDtos.PlanView;
import com.investmitra.expense.PlanDtos.SalaryRequest;
import com.investmitra.marketdata.MarketCalendar;
import com.investmitra.repository.AppUserRepository;
import com.investmitra.service.ApiException;
import com.investmitra.service.UserService;

/** Free to spend: salary, extra income and fixed commitments for a calendar month (IST). No rollover. */
@Service
public class PlanService {

	private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

	private final SalarySettingRepository salaries;
	private final ExtraIncomeRepository extras;
	private final CommitmentRepository commitments;
	private final ExpenseRepository expenses;
	private final AppUserRepository userRepo;
	private final UserService users;
	private final Clock clock;

	public PlanService(SalarySettingRepository salaries, ExtraIncomeRepository extras,
			CommitmentRepository commitments, ExpenseRepository expenses, AppUserRepository userRepo,
			UserService users, Clock clock) {
		this.salaries = salaries;
		this.extras = extras;
		this.commitments = commitments;
		this.expenses = expenses;
		this.userRepo = userRepo;
		this.users = users;
		this.clock = clock;
	}

	// ---- Plan ----

	@Transactional(readOnly = true)
	public PlanView plan(Long userId, String month) {
		return buildPlan(users.require(userId), parseMonth(month));
	}

	/** Sets the salary from the given month onward (upsert on that effective month). */
	@Transactional
	public PlanView setSalary(Long userId, SalaryRequest request) {
		users.require(userId);
		// Serialise concurrent saves per user so two requests can't both insert the same month.
		AppUser user = userRepo.findByIdForUpdate(userId).orElseThrow();
		YearMonth ym = parseMonth(request.month());
		SalarySetting s = salaries.findByUserAndEffectiveMonth(user, ym.toString()).orElseGet(() -> {
			SalarySetting n = new SalarySetting();
			n.setUser(user);
			n.setEffectiveMonth(ym.toString());
			return n;
		});
		s.setAmount(money(request.amount()));
		salaries.save(s);
		return buildPlan(user, ym);
	}

	private PlanView buildPlan(AppUser user, YearMonth ym) {
		String key = ym.toString();
		SalarySetting salary = salaries
				.findFirstByUserAndEffectiveMonthLessThanEqualOrderByEffectiveMonthDesc(user, key).orElse(null);
		BigDecimal extra = extras.findByUserAndIncomeMonthOrderByIdDesc(user, key).stream().map(ExtraIncome::getAmount)
				.reduce(ZERO, BigDecimal::add);
		BigDecimal totalIncome = (salary == null ? ZERO : salary.getAmount()).add(extra);

		List<Commitment> active = commitments.findByUserOrderByStartMonthDescIdDesc(user).stream()
				.filter(c -> c.activeIn(key)).toList();
		BigDecimal committed = ZERO;
		BigDecimal savings = ZERO;
		for (Commitment c : active) {
			if (c.getKind() == CommitmentKind.SAVINGS) {
				savings = savings.add(c.getAmount());
			}
			else {
				committed = committed.add(c.getAmount());
			}
		}
		BigDecimal free = totalIncome.subtract(committed).subtract(savings);
		BigDecimal spentSum = expenses.total(user, ym.atDay(1), ym.atEndOfMonth());
		BigDecimal spent = spentSum == null ? ZERO : spentSum;
		BigDecimal remaining = free.subtract(spent);

		int daysLeft = daysLeft(ym);
		BigDecimal daily = daysLeft == 0 || remaining.signum() <= 0 ? null
				: remaining.divide(BigDecimal.valueOf(daysLeft), 0, RoundingMode.DOWN);
		Integer percent = totalIncome.signum() == 0 ? null
				: committed.multiply(BigDecimal.valueOf(100)).divide(totalIncome, 0, RoundingMode.HALF_UP).intValue();

		String soonLimit = ym.plusMonths(2).toString();
		List<EndingSoon> ending = active.stream()
				.filter(c -> c.getEndMonth() != null && c.getEndMonth().compareTo(soonLimit) <= 0)
				.map(c -> new EndingSoon(c.getId(), c.getName(), c.getAmount(), c.getEndMonth())).toList();

		return new PlanView(key, salary == null ? null : salary.getAmount(),
				salary == null ? null : salary.getEffectiveMonth(), extra, totalIncome,
				active.stream().map(c -> CommitmentView.of(c, ym)).toList(), committed, savings, free, spent,
				remaining, daysLeft, daily, percent, ending);
	}

	/** Current month: today to month end inclusive. Future month: its length. Past month: 0. */
	private int daysLeft(YearMonth ym) {
		YearMonth now = YearMonth.from(today());
		if (ym.isBefore(now)) {
			return 0;
		}
		if (ym.isAfter(now)) {
			return ym.lengthOfMonth();
		}
		return ym.lengthOfMonth() - today().getDayOfMonth() + 1;
	}

	// ---- Commitments ----

	@Transactional(readOnly = true)
	public List<CommitmentView> commitments(Long userId) {
		YearMonth now = YearMonth.from(today());
		return commitments.findByUserOrderByStartMonthDescIdDesc(users.require(userId)).stream()
				.map(c -> CommitmentView.of(c, now)).toList();
	}

	@Transactional
	public CommitmentView createCommitment(Long userId, CommitmentRequest request) {
		Commitment c = new Commitment();
		c.setUser(users.require(userId));
		c.setCreatedAt(Instant.now(clock));
		apply(c, request);
		return CommitmentView.of(commitments.save(c), YearMonth.from(today()));
	}

	@Transactional
	public CommitmentView updateCommitment(Long userId, Long id, CommitmentRequest request) {
		Commitment c = requireOwnCommitment(userId, id);
		apply(c, request);
		return CommitmentView.of(c, YearMonth.from(today()));
	}

	@Transactional
	public void deleteCommitment(Long userId, Long id) {
		commitments.delete(requireOwnCommitment(userId, id));
	}

	private void apply(Commitment c, CommitmentRequest request) {
		YearMonth start = parseMonth(request.startMonth());
		YearMonth end = request.endMonth() == null || request.endMonth().isBlank() ? null
				: parseMonth(request.endMonth());
		if (end != null && end.isBefore(start)) {
			throw ApiException.badRequest("endMonth can't be before startMonth");
		}
		c.setName(request.name().trim());
		c.setKind(request.kind());
		c.setAmount(money(request.amount()));
		c.setDueDay(request.dueDay());
		c.setStartMonth(start.toString());
		c.setEndMonth(end == null ? null : end.toString());
	}

	// ---- Extra income ----

	@Transactional(readOnly = true)
	public List<ExtraIncomeView> extraIncome(Long userId, String month) {
		return extras.findByUserAndIncomeMonthOrderByIdDesc(users.require(userId), parseMonth(month).toString())
				.stream().map(ExtraIncomeView::of).toList();
	}

	@Transactional
	public ExtraIncomeView addExtraIncome(Long userId, ExtraIncomeRequest request) {
		ExtraIncome e = new ExtraIncome();
		e.setUser(users.require(userId));
		e.setIncomeMonth(parseMonth(request.month()).toString());
		e.setAmount(money(request.amount()));
		e.setLabel(request.label().trim());
		e.setCreatedAt(Instant.now(clock));
		return ExtraIncomeView.of(extras.save(e));
	}

	@Transactional
	public void deleteExtraIncome(Long userId, Long id) {
		users.require(userId);
		ExtraIncome e = extras.findById(id).orElseThrow(() -> ApiException.notFound("Unknown extra income: " + id));
		if (!e.getUser().getId().equals(userId)) {
			throw ApiException.notFound("Unknown extra income: " + id);
		}
		extras.delete(e);
	}

	// ---- Helpers ----

	/** Someone else's commitment answers 404, so ids can't be probed. */
	private Commitment requireOwnCommitment(Long userId, Long id) {
		users.require(userId);
		Commitment c = commitments.findById(id).orElseThrow(() -> ApiException.notFound("Unknown commitment: " + id));
		if (!c.getUser().getId().equals(userId)) {
			throw ApiException.notFound("Unknown commitment: " + id);
		}
		return c;
	}

	private static BigDecimal money(BigDecimal v) {
		return v.setScale(2, RoundingMode.HALF_UP);
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
