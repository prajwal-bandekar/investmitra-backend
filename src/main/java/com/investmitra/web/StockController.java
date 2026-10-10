package com.investmitra.web;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.investmitra.marketdata.MarketDataService;
import com.investmitra.service.StockService;
import com.investmitra.web.dto.Dtos.SectorView;
import com.investmitra.web.dto.Dtos.StockView;
import com.investmitra.web.dto.PriceView;
import com.investmitra.web.dto.ResponseStructure;

/**
 * Search and sector endpoints read only stored prices. {@code /quote} may call Alpha Vantage,
 * but only when the stored price for the stock is older than the last trading day.
 * <p>
 * The caller is identified by the {@code X-User-Id} header until login is added.
 */
@RestController
@RequestMapping("/api")
public class StockController {

	private final StockService stocks;
	private final MarketDataService marketData;

	public StockController(StockService stocks, MarketDataService marketData) {
		this.stocks = stocks;
		this.marketData = marketData;
	}

	@GetMapping("/stocks")
	public ResponseStructure<Page<StockView>> search(@RequestParam(required = false) String query,
			@RequestParam(required = false) String sector, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestHeader(name = "X-User-Id", required = false) Long userId) {
		return ResponseStructure.ok(stocks.search(query, sector, userId, page, size), "Stocks found");
	}

	@GetMapping("/stocks/tracked")
	public ResponseStructure<List<StockView>> tracked() {
		return ResponseStructure.ok(stocks.tracked(), "Tracked stocks");
	}

	@GetMapping("/stocks/{symbol}")
	public ResponseStructure<StockView> get(@PathVariable String symbol,
			@RequestHeader(name = "X-User-Id", required = false) Long userId) {
		return ResponseStructure.ok(stocks.get(symbol, userId), "Stock found");
	}

	/** Latest price, fetched from Alpha Vantage only if no price for the last trading day is stored. */
	@GetMapping("/stocks/{symbol}/quote")
	public ResponseStructure<PriceView> quote(@PathVariable String symbol) {
		PriceView price = marketData.getQuote(symbol);
		String message = MarketDataService.SOURCE_ALPHA_VANTAGE.equals(price.source())
				? "Fetched from Alpha Vantage and saved"
				: price.fresh() ? "Served from database" : "Stored price is stale; no fresh data available";
		return ResponseStructure.ok(price, message);
	}

	@GetMapping("/sectors")
	public ResponseStructure<List<SectorView>> sectors() {
		return ResponseStructure.ok(stocks.sectors(), "Sectors");
	}
}
