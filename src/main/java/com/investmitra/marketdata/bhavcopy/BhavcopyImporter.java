package com.investmitra.marketdata.bhavcopy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.investmitra.entity.FetchTrigger;
import com.investmitra.entity.JobRun;
import com.investmitra.entity.JobStatus;
import com.investmitra.entity.Stock;
import com.investmitra.entity.StockPrice;
import com.investmitra.marketdata.bhavcopy.BhavcopyClient.Exchange;
import com.investmitra.repository.JobRunRepository;
import com.investmitra.repository.StockPriceRepository;
import com.investmitra.repository.StockRepository;
import com.investmitra.service.ApiException;

/**
 * Imports one day of NSE and BSE bhavcopy into the stock catalogue and price history.
 * NSE is primary; BSE only contributes companies whose ISIN is absent from NSE's file.
 */
@Service
public class BhavcopyImporter {

	public static final String DEFAULT_SECTOR = "Other";
	static final int BATCH_SIZE = 500;

	private static final Logger log = LoggerFactory.getLogger(BhavcopyImporter.class);

	/** Outcome of one import. {@code published} is false when neither exchange had a file (likely a holiday). */
	public record ImportResult(LocalDate date, JobStatus status, boolean published, int nseRows, int bseRows,
			int stocksCreated, int pricesSaved, int failures, String notes) {
	}

	private final BhavcopyClient client;
	private final StockRepository stocks;
	private final StockPriceRepository prices;
	private final JobRunRepository jobRuns;
	private final TransactionTemplate tx;
	private final Clock clock;
	private final ReentrantLock lock = new ReentrantLock();

	public BhavcopyImporter(BhavcopyClient client, StockRepository stocks, StockPriceRepository prices,
			JobRunRepository jobRuns, TransactionTemplate tx, Clock clock) {
		this.client = client;
		this.stocks = stocks;
		this.prices = prices;
		this.jobRuns = jobRuns;
		this.tx = tx;
		this.clock = clock;
	}

	public ImportResult importDay(LocalDate date, FetchTrigger trigger) {
		if (!lock.tryLock()) {
			throw ApiException.conflict("A bhavcopy import is already running");
		}
		try {
			JobRun run = new JobRun();
			run.setTrigger(trigger);
			run.setSource(JobRun.SOURCE_BHAVCOPY);
			run.setStatus(JobStatus.RUNNING);
			run.setStartedAt(Instant.now(clock));
			run = jobRuns.save(run);
			try {
				ImportResult result = doImport(date);
				run.setStatus(result.status());
				run.setPricesSaved(result.pricesSaved());
				run.setFailures(result.failures());
				run.setNotes(truncate(result.notes()));
				run.setFinishedAt(Instant.now(clock));
				jobRuns.save(run);
				return result;
			} catch (RuntimeException e) {
				log.error("Bhavcopy import for {} failed", date, e);
				run.setStatus(JobStatus.FAILED);
				run.setFailures(1);
				run.setNotes(truncate(String.valueOf(e.getMessage())));
				run.setFinishedAt(Instant.now(clock));
				jobRuns.save(run);
				return new ImportResult(date, JobStatus.FAILED, true, 0, 0, 0, 0, 1, run.getNotes());
			}
		} finally {
			lock.unlock();
		}
	}

	private ImportResult doImport(LocalDate date) {
		List<String> notes = new ArrayList<>();
		int failures = 0;
		boolean nseMissing = false;
		boolean bseMissing = false;

		List<BhavcopyRow> nse = List.of();
		List<BhavcopyRow> bse = List.of();
		try {
			nse = usable(client.fetch(Exchange.NSE, date), date, "NSE", notes);
		} catch (BhavcopyException.NotPublished e) {
			nseMissing = true;
			notes.add("NSE: " + (e.getMessage() != null && e.getMessage().startsWith("file is for") ? e.getMessage()
					: "not published"));
		} catch (BhavcopyException e) {
			failures++;
			nseMissing = true;
			notes.add("NSE failed: " + e.getMessage());
		}
		try {
			bse = usable(client.fetch(Exchange.BSE, date), date, "BSE", notes);
		} catch (BhavcopyException.NotPublished e) {
			bseMissing = true;
			notes.add("BSE: " + (e.getMessage() != null && e.getMessage().startsWith("file is for") ? e.getMessage()
					: "not published"));
		} catch (BhavcopyException e) {
			failures++;
			bseMissing = true;
			notes.add("BSE failed: " + e.getMessage());
		}

		if (nseMissing && bseMissing) {
			if (failures == 0) {
				return new ImportResult(date, JobStatus.SUCCESS, false, 0, 0, 0, 0, 0, "no bhavcopy (holiday?)");
			}
			return new ImportResult(date, JobStatus.FAILED, true, 0, 0, 0, 0, failures, String.join("; ", notes));
		}

		// NSE first; BSE adds only ISINs NSE did not list.
		Map<String, BhavcopyRow> byIsin = new LinkedHashMap<>();
		nse.forEach(r -> byIsin.putIfAbsent(r.isin(), r));
		int bseOnly = 0;
		for (BhavcopyRow r : bse) {
			if (byIsin.putIfAbsent(r.isin(), r) == null) {
				bseOnly++;
			}
		}

		Map<String, Stock> byIsinStock = new HashMap<>();
		Map<String, Stock> bySymbol = new HashMap<>();
		for (Stock s : stocks.findAll()) {
			if (s.getIsin() != null && !s.getIsin().isBlank()) {
				byIsinStock.put(s.getIsin(), s);
			}
			bySymbol.put(s.getSymbol().toUpperCase(Locale.ROOT), s);
		}

		// Resolve a Stock for every row; new ones are collected and saved in batches.
		Map<String, Stock> resolved = new HashMap<>();
		List<Stock> created = new ArrayList<>();
		Set<Stock> isinBackfilled = new HashSet<>();
		int skipped = 0;
		for (BhavcopyRow r : byIsin.values()) {
			Stock s = byIsinStock.get(r.isin());
			if (s == null) {
				String sym = r.symbol().trim().toUpperCase(Locale.ROOT);
				Stock bySym = bySymbol.get(sym);
				if (bySym == null) {
					s = new Stock();
					s.setSymbol(sym);
					s.setName(r.name() == null || r.name().isBlank() ? sym : r.name().trim());
					s.setSector(DEFAULT_SECTOR);
					s.setIsin(r.isin());
					created.add(s);
					bySymbol.put(sym, s);
				} else if (bySym.getIsin() == null || bySym.getIsin().isBlank()) {
					bySym.setIsin(r.isin());
					isinBackfilled.add(bySym);
					s = bySym;
				} else {
					skipped++; // symbol is used by a different ISIN
					continue;
				}
				byIsinStock.put(r.isin(), s);
			}
			resolved.put(r.isin(), s);
		}

		List<Stock> dirty = new ArrayList<>(created);
		dirty.addAll(isinBackfilled);
		for (List<Stock> chunk : partition(dirty)) {
			tx.executeWithoutResult(st -> stocks.saveAll(chunk));
		}

		// Upsert prices in batches; existing rows for the date are loaded per batch.
		List<BhavcopyRow> rows = byIsin.values().stream().filter(r -> resolved.containsKey(r.isin())).toList();
		Instant now = Instant.now(clock);
		int saved = 0;
		for (List<BhavcopyRow> chunk : partition(rows)) {
			Integer n = tx.execute(st -> savePrices(date, chunk, resolved, now));
			saved += n == null ? 0 : n;
		}

		if (nseMissing || bseMissing) {
			notes.add((nseMissing ? "NSE" : "BSE") + " missing, imported from " + (nseMissing ? "BSE" : "NSE") + " only");
		}
		notes.add("rows NSE " + nse.size() + ", BSE " + bse.size() + " (" + bseOnly + " BSE-only)");
		if (skipped > 0) {
			notes.add(skipped + " skipped: symbol already used by a different ISIN");
		}
		JobStatus status = nseMissing || bseMissing ? JobStatus.PARTIAL : JobStatus.SUCCESS;
		return new ImportResult(date, status, true, nse.size(), bse.size(), created.size(), saved, failures,
				String.join("; ", notes));
	}

	/**
	 * Equity rows for the requested date with a positive close. Rows dated otherwise or with a zero/negative
	 * close are dropped and counted in the notes. A file whose rows are all for another date counts as not published.
	 */
	private static List<BhavcopyRow> usable(List<BhavcopyRow> parsed, LocalDate date, String exchange, List<String> notes) {
		int malformed = BhavcopyParser.skippedOf(parsed);
		List<BhavcopyRow> equities = BhavcopyParser.equities(parsed);
		List<BhavcopyRow> onDate = equities.stream().filter(r -> date.equals(r.tradeDate())).toList();
		if (!equities.isEmpty() && onDate.isEmpty()) {
			throw new BhavcopyException.NotPublished("file is for " + equities.getFirst().tradeDate());
		}
		List<BhavcopyRow> out = onDate.stream().filter(r -> r.close() != null && r.close().signum() > 0).toList();
		int wrongDate = equities.size() - onDate.size();
		int badClose = onDate.size() - out.size();
		if (malformed > 0) {
			notes.add(exchange + ": " + malformed + " unreadable rows skipped");
		}
		if (wrongDate > 0) {
			notes.add(exchange + ": " + wrongDate + " rows skipped (other trade date)");
		}
		if (badClose > 0) {
			notes.add(exchange + ": " + badClose + " rows skipped (close <= 0)");
		}
		return out;
	}

	private int savePrices(LocalDate date, List<BhavcopyRow> chunk, Map<String, Stock> resolved, Instant now) {
		List<Long> ids = chunk.stream().map(r -> resolved.get(r.isin()).getId()).toList();
		Map<Long, StockPrice> existing = prices.findByTradeDateAndStockIdIn(date, ids).stream()
				.collect(Collectors.toMap(p -> p.getStock().getId(), Function.identity(), (a, b) -> a));
		List<StockPrice> out = new ArrayList<>(chunk.size());
		for (BhavcopyRow r : chunk) {
			if (r.close() == null || r.close().signum() <= 0) {
				continue;
			}
			Stock stock = resolved.get(r.isin());
			StockPrice p = existing.get(stock.getId());
			if (p == null) {
				p = new StockPrice();
				p.setStock(stocks.getReferenceById(stock.getId()));
				p.setTradeDate(date);
			}
			p.setOpen(r.open());
			p.setHigh(r.high());
			p.setLow(r.low());
			p.setClose(r.close());
			p.setPreviousClose(r.previousClose());
			p.setVolume(r.volume());
			p.setFetchedAt(now);
			out.add(p);
		}
		prices.saveAll(out);
		return out.size();
	}

	/** True when a bhavcopy import saved prices successfully since the given time (used to skip retry runs). */
	public boolean importedSince(Instant since) {
		return jobRuns.existsBySourceAndStatusAndStartedAtGreaterThanEqualAndPricesSavedGreaterThan(
				JobRun.SOURCE_BHAVCOPY, JobStatus.SUCCESS, since, 0);
	}

	private static <T> List<List<T>> partition(List<T> list) {
		List<List<T>> out = new ArrayList<>();
		for (int i = 0; i < list.size(); i += BATCH_SIZE) {
			out.add(list.subList(i, Math.min(list.size(), i + BATCH_SIZE)));
		}
		return out;
	}

	private static String truncate(String s) {
		return s != null && s.length() > 2000 ? s.substring(0, 2000) : s;
	}
}
