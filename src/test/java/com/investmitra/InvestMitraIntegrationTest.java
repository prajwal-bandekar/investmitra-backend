package com.investmitra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.investmitra.entity.FetchTrigger;
import com.investmitra.entity.JobRun;
import com.investmitra.entity.JobStatus;
import com.investmitra.entity.Role;
import com.investmitra.expense.CommitmentRepository;
import com.investmitra.expense.ExtraIncomeRepository;
import com.investmitra.expense.SalarySettingRepository;
import com.investmitra.entity.StockPrice;
import com.investmitra.entity.TradeSide;
import com.investmitra.marketdata.AlphaVantageClient;
import com.investmitra.marketdata.AlphaVantageException;
import com.investmitra.marketdata.MarketCalendar;
import com.investmitra.marketdata.MarketDataService;
import com.investmitra.marketdata.external.GlobalQuoteResponse.GlobalQuote;
import com.investmitra.repository.AppUserRepository;
import com.investmitra.repository.HoldingRepository;
import com.investmitra.repository.JobRunRepository;
import com.investmitra.repository.PortfolioRepository;
import com.investmitra.repository.StockPriceRepository;
import com.investmitra.repository.StockRepository;
import com.investmitra.repository.TradeRepository;
import com.investmitra.repository.WatchlistItemRepository;
import com.investmitra.service.ApiException;
import com.investmitra.service.PortfolioService;
import com.investmitra.service.StockService;
import com.investmitra.service.TradingService;
import com.investmitra.service.UserService;
import com.investmitra.service.WatchlistService;
import com.investmitra.web.dto.Dtos.CreatePortfolioRequest;
import com.investmitra.web.dto.Dtos.CreateUserRequest;
import com.investmitra.web.dto.Dtos.LoginRequest;
import com.investmitra.web.dto.Dtos.PortfolioDetail;
import com.investmitra.web.dto.Dtos.StockView;
import com.investmitra.web.dto.Dtos.TradeRequest;
import com.investmitra.web.dto.Dtos.TradeResult;
import com.investmitra.web.dto.Dtos.WatchlistRequest;
import com.investmitra.web.dto.PriceView;

/** Runs against in-memory H2 with Alpha Vantage mocked, so no real API calls are spent. */
@SpringBootTest
class InvestMitraIntegrationTest {

	@MockitoBean
	AlphaVantageClient alphaVantage;

	/** Declared (unused) so this class shares one Spring context with BhavcopyImporterTest. */
	@MockitoBean
	com.investmitra.marketdata.bhavcopy.BhavcopyClient bhavcopyClient;

	@Autowired MarketDataService marketData;
	@Autowired MarketCalendar calendar;
	@Autowired UserService users;
	@Autowired PortfolioService portfolios;
	@Autowired TradingService trading;
	@Autowired StockService stockService;
	@Autowired WatchlistService watchlist;

	@Autowired StockRepository stockRepo;
	@Autowired StockPriceRepository priceRepo;
	@Autowired JobRunRepository jobRunRepo;
	@Autowired AppUserRepository userRepo;
	@Autowired CommitmentRepository commitmentRepo;
	@Autowired ExtraIncomeRepository extraIncomeRepo;
	@Autowired SalarySettingRepository salaryRepo;
	@Autowired PortfolioRepository portfolioRepo;
	@Autowired HoldingRepository holdingRepo;
	@Autowired TradeRepository tradeRepo;
	@Autowired WatchlistItemRepository watchlistRepo;

	@BeforeEach
	void reset() {
		tradeRepo.deleteAll();
		holdingRepo.deleteAll();
		watchlistRepo.deleteAll();
		portfolioRepo.deleteAll();
		commitmentRepo.deleteAll();
		extraIncomeRepo.deleteAll();
		salaryRepo.deleteAll();
		userRepo.deleteAll();
		priceRepo.deleteAll();
		jobRunRepo.deleteAll();
		when(alphaVantage.isConfigured()).thenReturn(true);
	}

	// ---------- catalog ----------

	@Test
	void catalogIsSeededAndConfiguredSymbolsAreTracked() {
		assertThat(stockRepo.count()).isGreaterThan(400);
		assertThat(stockRepo.findByAvSymbolIsNotNull())
				.extracting(s -> s.getAvSymbol())
				.containsExactlyInAnyOrder("TCS.BSE", "INFY.BSE", "HDFCBANK.BSE", "RELIANCE.BSE");
	}

	// ---------- daily job ----------

	@Test
	void jobStopsAtDailyBudgetAndNextRunDoesNotOverspend() {
		when(alphaVantage.getGlobalQuote(anyString())).thenAnswer(inv -> quote(inv.getArgument(0), "100"));

		JobRun first = marketData.refreshTrackedStocks(FetchTrigger.MANUAL);
		assertThat(first.getStatus()).isEqualTo(JobStatus.PARTIAL);
		assertThat(first.getCallsMade()).isEqualTo(3); // test budget is 3, four stocks tracked
		assertThat(first.getPricesSaved()).isEqualTo(3);
		assertThat(first.getNotes()).contains("Daily budget reached");

		JobRun second = marketData.refreshTrackedStocks(FetchTrigger.MANUAL);
		assertThat(second.getCallsMade()).isZero();
		verify(alphaVantage, times(3)).getGlobalQuote(anyString());
	}

	@Test
	void jobSkipsStocksThatAlreadyHaveTodaysPrice() {
		savePrice("TCS", "100");
		savePrice("INFY", "100");
		savePrice("HDFCBANK", "100");
		savePrice("RELIANCE", "100");

		JobRun run = marketData.refreshTrackedStocks(FetchTrigger.MANUAL);

		assertThat(run.getStatus()).isEqualTo(JobStatus.SUCCESS);
		assertThat(run.getCallsMade()).isZero();
		verify(alphaVantage, never()).getGlobalQuote(anyString());
	}

	@Test
	void jobStopsImmediatelyWhenRateLimited() {
		when(alphaVantage.getGlobalQuote(anyString()))
				.thenThrow(new AlphaVantageException.RateLimited("25 requests per day"));

		JobRun run = marketData.refreshTrackedStocks(FetchTrigger.MANUAL);

		assertThat(run.getStatus()).isEqualTo(JobStatus.PARTIAL);
		assertThat(run.getCallsMade()).isEqualTo(1);
		assertThat(run.getNotes()).contains("Rate limited");
	}

	@Test
	void reRunningForSameTradingDayUpdatesInsteadOfDuplicating() {
		when(alphaVantage.getGlobalQuote(anyString())).thenAnswer(inv -> quote(inv.getArgument(0), "100"));
		marketData.getQuote("INFY");
		priceRepo.findAll().forEach(p -> {
			p.setFetchedAt(Instant.parse("2020-01-01T00:00:00Z"));
			p.setTradeDate(calendar.lastCompletedTradingDay().minusDays(7));
			priceRepo.save(p);
		});
		marketData.getQuote("INFY");
		marketData.getQuote("INFY");

		assertThat(priceRepo.count()).isEqualTo(2); // old week + latest day, no duplicates
		verify(alphaVantage, times(2)).getGlobalQuote("INFY.BSE");
	}

	// ---------- on-demand quote (date-wise cache) ----------

	@Test
	void quoteIsServedFromDatabaseWhenFresh() {
		savePrice("TCS", "3220.15");

		PriceView view = marketData.getQuote("TCS");

		assertThat(view.source()).isEqualTo(MarketDataService.SOURCE_DATABASE);
		assertThat(view.close()).isEqualByComparingTo("3220.15");
		verify(alphaVantage, never()).getGlobalQuote(anyString());
	}

	@Test
	void staleQuoteIsFetchedOnceThenServedFromDatabase() {
		when(alphaVantage.getGlobalQuote("INFY.BSE")).thenReturn(quote("INFY.BSE", "250.50"));

		PriceView first = marketData.getQuote("INFY");
		PriceView second = marketData.getQuote("infy");

		assertThat(first.source()).isEqualTo(MarketDataService.SOURCE_ALPHA_VANTAGE);
		assertThat(second.source()).isEqualTo(MarketDataService.SOURCE_DATABASE);
		assertThat(second.close()).isEqualByComparingTo("250.50");
		verify(alphaVantage, times(1)).getGlobalQuote("INFY.BSE");
	}

	@Test
	void whenBudgetIsUsedUpTheLastStoredPriceIsReturnedAsStale() {
		StockPrice old = savePrice("TCS", "3000");
		old.setTradeDate(calendar.lastCompletedTradingDay().minusDays(10));
		old.setFetchedAt(Instant.parse("2020-01-01T00:00:00Z"));
		priceRepo.save(old);
		JobRun used = new JobRun();
		used.setTrigger(FetchTrigger.MANUAL);
		used.setStatus(JobStatus.SUCCESS);
		used.setStartedAt(Instant.now());
		used.setCallsMade(3);
		jobRunRepo.save(used);

		PriceView view = marketData.getQuote("TCS");

		assertThat(view.fresh()).isFalse();
		assertThat(view.close()).isEqualByComparingTo("3000");
		verify(alphaVantage, never()).getGlobalQuote(anyString());
	}

	// ---------- trading and average price ----------

	@Test
	void averageBuyPriceCashAndRealizedPnlFollowBrokerRules() {
		Long user = newUser("fresher@example.com", Role.USER);
		Long pid = portfolios.create(user, new CreatePortfolioRequest("IT bets", "Information Technology", null, null)).id();

		savePrice("TCS", "3000");
		trading.trade(user, pid, new TradeRequest("TCS", TradeSide.BUY, 10, null));
		savePrice("TCS", "3400");
		TradeResult secondBuy = trading.trade(user, pid, new TradeRequest("TCS", TradeSide.BUY, 30, null));

		assertThat(secondBuy.holding().quantity()).isEqualTo(40);
		assertThat(secondBuy.holding().avgBuyPrice()).isEqualByComparingTo("3300");
		assertThat(secondBuy.ownerCash()).isEqualByComparingTo("868000.00"); // 10L - 30000 - 102000

		// Search shows the advisor/user what they paid on average and the gain at today's price.
		StockView found = stockService.search("TCS", null, user, 0, 10).getContent().getFirst();
		assertThat(found.myPosition().avgBuyPrice()).isEqualByComparingTo("3300");
		assertThat(found.myPosition().unrealizedPnl()).isEqualByComparingTo("4000.00"); // (3400-3300)*40

		TradeResult sell = trading.trade(user, pid, new TradeRequest("TCS", TradeSide.SELL, 15, null));
		assertThat(sell.trade().realizedPnl()).isEqualByComparingTo("1500.00"); // (3400-3300)*15
		assertThat(sell.holding().avgBuyPrice()).isEqualByComparingTo("3300"); // unchanged by selling
		assertThat(sell.ownerCash()).isEqualByComparingTo("919000.00");

		PortfolioDetail detail = portfolios.detail(user, pid);
		assertThat(detail.invested()).isEqualByComparingTo("82500.00");
		assertThat(detail.currentValue()).isEqualByComparingTo("85000.00");
		assertThat(detail.realizedPnl()).isEqualByComparingTo("1500.00");

		trading.trade(user, pid, new TradeRequest("TCS", TradeSide.SELL, 25, null));
		assertThat(holdingRepo.count()).isZero();
		assertThat(portfolios.tradeHistory(user, pid)).hasSize(4);
	}

	@Test
	void sectorPortfolioRejectsStocksFromOtherSectors() {
		Long user = newUser("energy@example.com", Role.USER);
		Long pid = portfolios.create(user, new CreatePortfolioRequest("Energy", "Oil Gas & Consumable Fuels", null, null)).id();
		savePrice("TCS", "3000");

		assertThatThrownBy(() -> trading.trade(user, pid, new TradeRequest("TCS", TradeSide.BUY, 1, null)))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("only holds Oil Gas & Consumable Fuels");
	}

	@Test
	void cannotOverspendOrOversell() {
		Long user = newUser("careful@example.com", Role.USER);
		Long pid = portfolios.create(user, new CreatePortfolioRequest("Main", null, null, null)).id();
		savePrice("TCS", "3000");

		assertThatThrownBy(() -> trading.trade(user, pid, new TradeRequest("TCS", TradeSide.BUY, 1000, null)))
				.hasMessageContaining("Insufficient cash");
		trading.trade(user, pid, new TradeRequest("TCS", TradeSide.BUY, 2, null));
		assertThatThrownBy(() -> trading.trade(user, pid, new TradeRequest("TCS", TradeSide.SELL, 3, null)))
				.hasMessageContaining("holding only 2");
	}

	@Test
	void advisorManagesClientPortfolioButStrangersCannot() {
		Long client = newUser("client@example.com", Role.USER);
		Long advisor = newUser("advisor@example.com", Role.ADVISOR);
		Long stranger = newUser("stranger@example.com", Role.USER);
		savePrice("INFY", "1500");

		// Not a client yet: the advisor cannot open portfolios in this user's name.
		assertThatThrownBy(() -> portfolios.create(advisor, new CreatePortfolioRequest("Client IT", null, client, null)))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
		// The client appoints the advisor on their own portfolio; after that the advisor may create more.
		portfolios.create(client, new CreatePortfolioRequest("First", null, null, advisor));
		Long pid = portfolios.create(advisor, new CreatePortfolioRequest("Client IT", null, client, null)).id();
		TradeResult result = trading.trade(advisor, pid, new TradeRequest("INFY", TradeSide.BUY, 10, null));

		assertThat(result.ownerCash()).isEqualByComparingTo("985000.00"); // client's cash is used
		assertThat(stockService.get("INFY", advisor).myPosition().quantity()).isEqualTo(10);
		assertThatThrownBy(() -> portfolios.detail(stranger, pid))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
		assertThatThrownBy(() -> portfolios.create(stranger, new CreatePortfolioRequest("x", null, client, null)))
				.hasMessageContaining("Only advisors");
	}

	@Test
	void watchlistFlagsTargetPriceReached() {
		Long user = newUser("watcher@example.com", Role.USER);
		savePrice("RELIANCE", "1400");

		watchlist.add(user, new WatchlistRequest("RELIANCE", new BigDecimal("1450")));
		watchlist.add(user, new WatchlistRequest("TCS", new BigDecimal("2000")));

		assertThat(watchlist.list(user))
				.extracting(w -> w.symbol() + ":" + w.targetReached())
				.containsExactlyInAnyOrder("RELIANCE:true", "TCS:false");
	}

	@Test
	void loginNeedsTheRightPasswordAndNeverStoresItPlain() {
		Long id = newUser("Login@Example.com", Role.USER);

		assertThat(users.login(new LoginRequest("login@example.com", "secret123")).id()).isEqualTo(id);
		assertThat(users.login(new LoginRequest("login@example.com", "secret123")).clientId()).isEqualTo(String.format("IM-%04d", id));
		assertThatThrownBy(() -> users.login(new LoginRequest("login@example.com", "wrong-pass")))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
		assertThatThrownBy(() -> users.login(new LoginRequest("nobody@example.com", "secret123")))
				.hasMessage("Wrong email or password");
		assertThat(userRepo.findById(id).orElseThrow().getPasswordHash()).startsWith("$2").doesNotContain("secret123");
	}

	@Test
	void portfolioListIsValuedAtLatestClose() {
		Long user = newUser("valued@example.com", Role.USER);
		Long pid = portfolios.create(user, new CreatePortfolioRequest("Main", null, null, null)).id();
		savePrice("TCS", "3000");
		trading.trade(user, pid, new TradeRequest("TCS", TradeSide.BUY, 10, null));
		savePrice("TCS", "3100");

		var summary = portfolios.listManagedBy(user).getFirst();
		assertThat(summary.currentValue()).isEqualByComparingTo("31000.00");
		assertThat(summary.totalPnl()).isEqualByComparingTo("1000.00");
		assertThat(stockService.tracked()).extracting(s -> s.symbol()).contains("TCS", "INFY");
	}

	// ---------- review fixes ----------

	@Test
	void onlyAdvisorsCanBeAppointedAndOnlyByTheOwner() {
		Long owner = newUser("o1@example.com", Role.USER);
		Long plain = newUser("o2@example.com", Role.USER);

		assertThatThrownBy(() -> portfolios.create(owner, new CreatePortfolioRequest("x", null, null, plain)))
				.hasMessageContaining("not an advisor");
	}

	@Test
	void sectorMustBeKnown() {
		Long user = newUser("sec@example.com", Role.USER);
		assertThatThrownBy(() -> portfolios.create(user, new CreatePortfolioRequest("x", "Made Up", null, null)))
				.hasMessageContaining("Unknown sector");
		assertThat(portfolios.create(user, new CreatePortfolioRequest("ok", "  ", null, null)).sector()).isNull();
	}

	@Test
	void duplicateEmailIsAConflict() {
		newUser("dup@example.com", Role.USER);
		assertThatThrownBy(() -> users.create(new CreateUserRequest("T", "DUP@example.com", "secret123", Role.USER)))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
	}

	@Test
	void repeatedClientRequestIdDoesNotTradeTwice() {
		Long user = newUser("idem@example.com", Role.USER);
		Long pid = portfolios.create(user, new CreatePortfolioRequest("Main", null, null, null)).id();
		savePrice("TCS", "3000");

		TradeResult first = trading.trade(user, pid, new TradeRequest("TCS", TradeSide.BUY, 10, "req-1"));
		TradeResult again = trading.trade(user, pid, new TradeRequest("TCS", TradeSide.BUY, 10, "req-1"));

		assertThat(again.trade().id()).isEqualTo(first.trade().id());
		assertThat(again.ownerCash()).isEqualByComparingTo("970000.00");
		assertThat(tradeRepo.count()).isEqualTo(1);
		assertThat(holdingRepo.findAll().getFirst().getQuantity()).isEqualTo(10);
		assertThatThrownBy(() -> trading.trade(user, pid, new TradeRequest("TCS", TradeSide.BUY, 11, "req-1")))
				.hasMessageContaining("different order");
		// Same key on another portfolio is a separate order.
		Long other = portfolios.create(user, new CreatePortfolioRequest("Other", null, null, null)).id();
		trading.trade(user, other, new TradeRequest("TCS", TradeSide.BUY, 1, "req-1"));
		assertThat(tradeRepo.count()).isEqualTo(2);
	}

	@Test
	void tradesWithoutRequestIdAreNeverDeduplicated() {
		Long user = newUser("noid@example.com", Role.USER);
		Long pid = portfolios.create(user, new CreatePortfolioRequest("Main", null, null, null)).id();
		savePrice("TCS", "3000");
		trading.trade(user, pid, new TradeRequest("TCS", TradeSide.BUY, 1, null));
		trading.trade(user, pid, new TradeRequest("TCS", TradeSide.BUY, 1, null));
		assertThat(tradeRepo.count()).isEqualTo(2);
	}

	@Test
	void buyingOnAVeryStalePriceIsBlockedButSellingStillWorks() {
		Long user = newUser("stale@example.com", Role.USER);
		Long pid = portfolios.create(user, new CreatePortfolioRequest("Main", null, null, null)).id();
		StockPrice p = savePrice("TCS", "3000");
		trading.trade(user, pid, new TradeRequest("TCS", TradeSide.BUY, 5, null));
		p.setTradeDate(calendar.lastCompletedTradingDay().minusDays(30));
		priceRepo.save(p);

		assertThatThrownBy(() -> trading.trade(user, pid, new TradeRequest("TCS", TradeSide.BUY, 1, null)))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT))
				.hasMessageContaining("too old");
		assertThat(trading.trade(user, pid, new TradeRequest("TCS", TradeSide.SELL, 5, null)).holding()).isNull();
	}

	@Test
	void zeroPriceMeansNoTradeEvenForSells() {
		Long user = newUser("zeroprice@example.com", Role.USER);
		Long pid = portfolios.create(user, new CreatePortfolioRequest("Main", null, null, null)).id();
		savePrice("TCS", "0");
		for (TradeSide side : TradeSide.values()) {
			assertThatThrownBy(() -> trading.trade(user, pid, new TradeRequest("TCS", side, 1, null)))
					.isInstanceOfSatisfying(ApiException.class,
							e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT))
					.hasMessageContaining("No usable price");
		}
	}

	@Test
	void noPriceMeansNoTrade() {
		Long user = newUser("noprice@example.com", Role.USER);
		Long pid = portfolios.create(user, new CreatePortfolioRequest("Main", null, null, null)).id();
		assertThatThrownBy(() -> trading.trade(user, pid, new TradeRequest("TCS", TradeSide.SELL, 1, null)))
				.hasMessageContaining("No price available");
	}

	@Test
	void sellAllThenBuyAgainStartsAFreshAverage() {
		Long user = newUser("again@example.com", Role.USER);
		Long pid = portfolios.create(user, new CreatePortfolioRequest("Main", null, null, null)).id();
		savePrice("TCS", "3000");
		trading.trade(user, pid, new TradeRequest("TCS", TradeSide.BUY, 10, null));
		trading.trade(user, pid, new TradeRequest("TCS", TradeSide.SELL, 10, null));
		savePrice("TCS", "3500");

		TradeResult r = trading.trade(user, pid, new TradeRequest("TCS", TradeSide.BUY, 2, null));
		assertThat(r.holding().avgBuyPrice()).isEqualByComparingTo("3500");
	}

	@Test
	void oddPricesRoundHalfUpInsteadOfThrowing() {
		Long user = newUser("round@example.com", Role.USER);
		Long pid = portfolios.create(user, new CreatePortfolioRequest("Main", null, null, null)).id();
		savePrice("TCS", "100.1234");
		trading.trade(user, pid, new TradeRequest("TCS", TradeSide.BUY, 3, null));
		savePrice("TCS", "100.1299");

		assertThat(portfolios.listManagedBy(user).getFirst().totalPnl()).isEqualByComparingTo("0.02");
		assertThat(portfolios.detail(user, pid).realizedPnl()).isEqualByComparingTo("0.00");
	}

	@Test
	void positionSizeOverflowIsRejected() {
		Long user = newUser("big@example.com", Role.USER);
		Long pid = portfolios.create(user, new CreatePortfolioRequest("Main", null, null, null)).id();
		savePrice("TCS", "0.0001");
		var h = new com.investmitra.entity.Holding();
		h.setPortfolio(portfolioRepo.findById(pid).orElseThrow());
		h.setStock(stockService.require("TCS"));
		h.setQuantity(Long.MAX_VALUE - 1);
		h.setAvgBuyPrice(BigDecimal.ONE);
		holdingRepo.save(h);

		assertThatThrownBy(() -> trading.trade(user, pid, new TradeRequest("TCS", TradeSide.BUY, 5, null)))
				.hasMessageContaining("maximum size");
	}

	// ---------- helpers ----------

	private Long newUser(String email, Role role) {
		return users.create(new CreateUserRequest("Test", email, "secret123", role)).id();
	}

	private StockPrice savePrice(String symbol, String close) {
		LocalDate day = calendar.lastCompletedTradingDay();
		StockPrice p = priceRepo.findByStockAndTradeDate(stockService.require(symbol), day).orElseGet(StockPrice::new);
		p.setStock(stockService.require(symbol));
		p.setTradeDate(day);
		p.setClose(new BigDecimal(close));
		p.setPreviousClose(new BigDecimal(close));
		p.setFetchedAt(Instant.now());
		return priceRepo.save(p);
	}

	private GlobalQuote quote(String avSymbol, String price) {
		BigDecimal p = new BigDecimal(price);
		return new GlobalQuote(avSymbol, p, p, p, p, 1000L, calendar.lastCompletedTradingDay(), p, BigDecimal.ZERO,
				"0.0000%");
	}
}
