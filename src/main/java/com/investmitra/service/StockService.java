package com.investmitra.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.investmitra.entity.Holding;
import com.investmitra.entity.Stock;
import com.investmitra.entity.StockPrice;
import com.investmitra.marketdata.MarketDataService;
import com.investmitra.repository.HoldingRepository;
import com.investmitra.repository.StockPriceRepository;
import com.investmitra.repository.StockRepository;
import com.investmitra.web.dto.Dtos.PositionView;
import com.investmitra.web.dto.Dtos.SectorView;
import com.investmitra.web.dto.Dtos.StockView;
import com.investmitra.web.dto.PriceView;

/** Stock search. Reads only from the database, so it never spends Alpha Vantage calls. */
@Service
public class StockService {

	private final StockRepository stocks;
	private final StockPriceRepository prices;
	private final HoldingRepository holdings;
	private final MarketDataService marketData;

	public StockService(StockRepository stocks, StockPriceRepository prices, HoldingRepository holdings,
			MarketDataService marketData) {
		this.stocks = stocks;
		this.prices = prices;
		this.holdings = holdings;
		this.marketData = marketData;
	}

	/**
	 * @param userId when given, each result includes the caller's combined position and average buy price
	 */
	@Transactional(readOnly = true)
	public Page<StockView> search(String query, String sector, Long userId, int page, int size) {
		String q = query == null || query.isBlank() ? null : query.trim();
		String s = sector == null || sector.isBlank() ? null : sector.trim();
		Page<Stock> result = stocks.search(q, s, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50)));
		List<Long> ids = result.getContent().stream().map(Stock::getId).toList();
		Map<Long, StockPrice> latest = latestPrices(ids);
		Map<Long, List<Holding>> held = userId == null ? Map.of()
				: holdings.findManagedByUserIn(userId, ids).stream()
						.collect(Collectors.groupingBy(h -> h.getStock().getId()));
		return result.map(stock -> view(stock, latest.get(stock.getId()), held.getOrDefault(stock.getId(), List.of())));
	}

	@Transactional(readOnly = true)
	public StockView get(String symbol, Long userId) {
		Stock stock = require(symbol);
		StockPrice price = latestPrices(List.of(stock.getId())).get(stock.getId());
		List<Holding> held = userId == null ? List.of() : holdings.findManagedByUserIn(userId, List.of(stock.getId()));
		return view(stock, price, held);
	}

	/** Stocks the daily job fetches, with their latest stored price. Feeds the ticker tape. */
	@Transactional(readOnly = true)
	public List<StockView> tracked() {
		List<Stock> tracked = stocks.findByAvSymbolIsNotNull();
		Map<Long, StockPrice> latest = latestPrices(tracked.stream().map(Stock::getId).toList());
		return tracked.stream()
				.sorted(java.util.Comparator.comparing(Stock::getSymbol))
				.map(s -> view(s, latest.get(s.getId()), List.of()))
				.toList();
	}

	@Transactional(readOnly = true)
	public List<SectorView> sectors() {
		return stocks.sectorSummary().stream()
				.map(r -> new SectorView((String) r[0], (Long) r[1], (Long) r[2]))
				.toList();
	}

	public Stock require(String symbol) {
		return stocks.findBySymbolIgnoreCase(symbol.trim())
				.orElseThrow(() -> ApiException.notFound("Unknown stock: " + symbol));
	}

	public Map<Long, StockPrice> latestPrices(Collection<Long> stockIds) {
		if (stockIds.isEmpty()) {
			return Map.of();
		}
		return prices.findLatestFor(stockIds).stream()
				.collect(Collectors.toMap(p -> p.getStock().getId(), Function.identity(), (a, b) -> a));
	}

	public PriceView priceView(StockPrice price) {
		return price == null ? null : PriceView.of(price, MarketDataService.SOURCE_DATABASE, marketData.isFresh(price));
	}

	private StockView view(Stock stock, StockPrice price, List<Holding> held) {
		return new StockView(stock.getSymbol(), stock.getName(), stock.getSector(), stock.getIsin(), stock.isTracked(),
				priceView(price), combinedPosition(held, price));
	}

	/** Merges holdings from several portfolios into one position with a weighted average price. */
	static PositionView combinedPosition(List<Holding> held, StockPrice price) {
		long qty = held.stream().mapToLong(Holding::getQuantity).sum();
		if (qty == 0) {
			return null;
		}
		BigDecimal invested = held.stream()
				.map(h -> PositionMath.amount(h.getAvgBuyPrice(), h.getQuantity()))
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal avg = invested.divide(BigDecimal.valueOf(qty), 4, java.math.RoundingMode.HALF_UP);
		if (price == null) {
			return new PositionView(qty, avg, invested, null, null, null);
		}
		BigDecimal value = PositionMath.amount(price.getClose(), qty);
		BigDecimal pnl = value.subtract(invested);
		return new PositionView(qty, avg, invested, value, pnl, PositionMath.percent(pnl, invested));
	}
}
