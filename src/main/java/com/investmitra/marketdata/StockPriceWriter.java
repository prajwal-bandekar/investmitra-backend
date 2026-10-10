package com.investmitra.marketdata;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.investmitra.entity.Stock;
import com.investmitra.entity.StockPrice;
import com.investmitra.marketdata.external.GlobalQuoteResponse.GlobalQuote;
import com.investmitra.repository.StockPriceRepository;

/** Saves a quote as the price for its trading day, replacing any earlier row for that date. */
@Component
public class StockPriceWriter {

	private final StockPriceRepository prices;
	private final Clock clock;

	public StockPriceWriter(StockPriceRepository prices, Clock clock) {
		this.prices = prices;
		this.clock = clock;
	}

	@Transactional
	public StockPrice upsert(Stock stock, GlobalQuote quote) {
		StockPrice price = prices.findByStockAndTradeDate(stock, quote.latestTradingDay()).orElseGet(() -> {
			StockPrice p = new StockPrice();
			p.setStock(stock);
			p.setTradeDate(quote.latestTradingDay());
			return p;
		});
		price.setOpen(quote.open());
		price.setHigh(quote.high());
		price.setLow(quote.low());
		price.setClose(quote.price());
		price.setPreviousClose(quote.previousClose());
		price.setVolume(quote.volume());
		price.setFetchedAt(Instant.now(clock));
		return prices.save(price);
	}
}
