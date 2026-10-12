package com.investmitra.marketdata;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.investmitra.config.AlphaVantageProperties;
import com.investmitra.entity.FetchTrigger;
import com.investmitra.entity.JobRun;
import com.investmitra.entity.JobStatus;
import com.investmitra.entity.Stock;
import com.investmitra.entity.StockPrice;
import com.investmitra.repository.JobRunRepository;
import com.investmitra.repository.StockPriceRepository;
import com.investmitra.repository.StockRepository;
import com.investmitra.service.ApiException;
import com.investmitra.web.dto.PriceView;

/**
 * The only place that talks to Alpha Vantage. Prices are stored per trading day, and a stored
 * price that already covers the last completed trading day is reused instead of calling the API.
 * All calls go through one lock so the job and on-demand requests never exceed 1 request/second.
 */
@Service
public class MarketDataService {

	public static final String SOURCE_DATABASE = "DATABASE";
	public static final String SOURCE_ALPHA_VANTAGE = "ALPHA_VANTAGE";

	private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);

	private final AlphaVantageClient client;
	private final AlphaVantageProperties props;
	private final StockRepository stocks;
	private final StockPriceRepository prices;
	private final StockPriceWriter writer;
	private final JobRunRepository jobRuns;
	private final MarketCalendar calendar;
	private final Pacer pacer;
	private final Clock clock;

	private final ReentrantLock apiLock = new ReentrantLock();
	private Instant lastCallAt = Instant.EPOCH;

	public MarketDataService(AlphaVantageClient client, AlphaVantageProperties props, StockRepository stocks,
			StockPriceRepository prices, StockPriceWriter writer, JobRunRepository jobRuns, MarketCalendar calendar,
			Pacer pacer, Clock clock) {
		this.client = client;
		this.props = props;
		this.stocks = stocks;
		this.prices = prices;
		this.writer = writer;
		this.jobRuns = jobRuns;
		this.calendar = calendar;
		this.pacer = pacer;
		this.clock = clock;
	}

	/**
	 * Latest price for one stock. Served from the database when fresh; otherwise fetched from
	 * Alpha Vantage (one call), saved for its trading day, and returned. If the daily quota is used
	 * up, the last stored price is returned with {@code fresh=false}.
	 */
	public PriceView getQuote(String symbol) {
		Stock stock = stocks.findBySymbolIgnoreCase(symbol)
				.orElseThrow(() -> ApiException.notFound("Unknown stock: " + symbol));
		Optional<StockPrice> stored = prices.findTopByStockOrderByTradeDateDesc(stock);
		if (stored.isPresent() && isFresh(stored.get())) {
			return PriceView.of(stored.get(), SOURCE_DATABASE, true);
		}

		if (stock.getAvSymbol() == null) {
			// Not an Alpha Vantage stock: only the bhavcopy import supplies its prices.
			return stored.map(p -> PriceView.of(p, SOURCE_DATABASE, isFresh(p)))
					.orElseThrow(() -> ApiException.unavailable("No price yet for " + stock.getSymbol()
							+ "; prices arrive with the daily bhavcopy import"));
		}

		apiLock.lock();
		try {
			// Another request may have fetched it while we waited for the lock.
			stored = prices.findTopByStockOrderByTradeDateDesc(stock);
			if (stored.isPresent() && isFresh(stored.get())) {
				return PriceView.of(stored.get(), SOURCE_DATABASE, true);
			}
			if (remainingCallsToday() <= 0 || !client.isConfigured()) {
				return stored.map(p -> PriceView.of(p, SOURCE_DATABASE, false))
						.orElseThrow(() -> ApiException.unavailable(
								"No stored price for " + stock.getSymbol() + " and no Alpha Vantage calls left today"));
			}

			JobRun run = startRun(FetchTrigger.ON_DEMAND);
			try {
				StockPrice saved = fetchAndSave(stock, run);
				finishRun(run, JobStatus.SUCCESS, stock.getSymbol());
				return PriceView.of(saved, SOURCE_ALPHA_VANTAGE, isFresh(saved));
			} catch (AlphaVantageException e) {
				run.setFailures(1);
				finishRun(run, JobStatus.FAILED, e.getMessage());
				return stored.map(p -> PriceView.of(p, SOURCE_DATABASE, false))
						.orElseThrow(() -> ApiException.unavailable(e.getMessage()));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				finishRun(run, JobStatus.FAILED, "Interrupted");
				throw ApiException.unavailable("Interrupted while fetching " + stock.getSymbol());
			}
		} finally {
			apiLock.unlock();
		}
	}

	/**
	 * Daily job body: fetches every tracked stock whose stored price is not fresh, stalest first,
	 * until done or the daily budget runs out. Symbols left over are picked up first next time.
	 */
	public JobRun refreshTrackedStocks(FetchTrigger trigger) {
		if (!apiLock.tryLock()) {
			throw ApiException.conflict("A price refresh is already running");
		}
		JobRun run = startRun(trigger);
		try {
			if (!client.isConfigured()) {
				return finishRun(run, JobStatus.FAILED, "ALPHAVANTAGE_API_KEY is not set");
			}
			List<Stock> due = staleTrackedStocks();
			int budget = remainingCallsToday();
			List<String> notes = new ArrayList<>();
			boolean stoppedEarly = false;

			for (Stock stock : due) {
				if (run.getCallsMade() >= budget) {
					notes.add("Daily budget reached; " + (due.size() - run.getCallsMade()) + " stocks left for next run");
					stoppedEarly = true;
					break;
				}
				try {
					fetchAndSave(stock, run);
					run.setPricesSaved(run.getPricesSaved() + 1);
				} catch (AlphaVantageException.RateLimited e) {
					notes.add("Rate limited after " + run.getCallsMade() + " calls: " + e.getMessage());
					stoppedEarly = true;
					break;
				} catch (AlphaVantageException e) {
					run.setFailures(run.getFailures() + 1);
					notes.add(e.getMessage());
					log.warn("Price fetch failed: {}", e.getMessage());
				}
			}

			if (due.isEmpty()) {
				notes.add("All tracked prices already fresh; no calls made");
			}
			JobStatus status = stoppedEarly || run.getFailures() > 0 ? JobStatus.PARTIAL : JobStatus.SUCCESS;
			return finishRun(run, status, String.join("; ", notes));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return finishRun(run, JobStatus.FAILED, "Interrupted");
		} catch (RuntimeException e) {
			log.error("Price refresh failed", e);
			return finishRun(run, JobStatus.FAILED, e.getMessage());
		} finally {
			apiLock.unlock();
		}
	}

	public int remainingCallsToday() {
		return Math.max(0, props.dailyBudget() - jobRuns.callsMadeSince(calendar.startOfToday()));
	}

	public String alphaVantageSymbol(Stock stock) {
		return stock.getAvSymbol() != null ? stock.getAvSymbol() : stock.getSymbol() + props.exchangeSuffix();
	}

	public boolean isFresh(StockPrice price) {
		return calendar.isFresh(price.getTradeDate(), price.getFetchedAt());
	}

	/** Tracked stocks without a fresh price; never-fetched first, then oldest fetch first. */
	List<Stock> staleTrackedStocks() {
		List<Stock> tracked = stocks.findByAvSymbolIsNotNull();
		Map<Long, StockPrice> latest = prices.findLatestFor(tracked.stream().map(Stock::getId).toList()).stream()
				.collect(Collectors.toMap(p -> p.getStock().getId(), Function.identity(), (a, b) -> a));
		return tracked.stream()
				.filter(s -> latest.get(s.getId()) == null || !isFresh(latest.get(s.getId())))
				.sorted(Comparator.comparing((Stock s) -> latest.get(s.getId()) == null
						? Instant.EPOCH
						: latest.get(s.getId()).getFetchedAt()))
				.toList();
	}

	/** Caller must hold {@link #apiLock}. Waits as needed to respect the per-second limit. */
	private StockPrice fetchAndSave(Stock stock, JobRun run) throws InterruptedException {
		Instant earliest = lastCallAt.plus(props.requestDelay());
		Instant now = Instant.now(clock);
		if (now.isBefore(earliest)) {
			pacer.pause(java.time.Duration.between(now, earliest));
		}
		run.setCallsMade(run.getCallsMade() + 1);
		lastCallAt = Instant.now(clock);
		return writer.upsert(stock, client.getGlobalQuote(alphaVantageSymbol(stock)));
	}

	private JobRun startRun(FetchTrigger trigger) {
		JobRun run = new JobRun();
		run.setTrigger(trigger);
		run.setSource(JobRun.SOURCE_ALPHA_VANTAGE);
		run.setStatus(JobStatus.RUNNING);
		run.setStartedAt(Instant.now(clock));
		return jobRuns.save(run);
	}

	private JobRun finishRun(JobRun run, JobStatus status, String notes) {
		run.setStatus(status);
		run.setFinishedAt(Instant.now(clock));
		if (notes != null && !notes.isBlank()) {
			run.setNotes(notes.length() > 2000 ? notes.substring(0, 2000) : notes);
		}
		return jobRuns.save(run);
	}
}
