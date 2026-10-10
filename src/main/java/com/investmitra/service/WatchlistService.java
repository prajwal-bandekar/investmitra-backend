package com.investmitra.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.investmitra.entity.AppUser;
import com.investmitra.entity.Stock;
import com.investmitra.entity.StockPrice;
import com.investmitra.entity.WatchlistItem;
import com.investmitra.marketdata.MarketCalendar;
import com.investmitra.repository.WatchlistItemRepository;
import com.investmitra.web.dto.Dtos.WatchlistRequest;
import com.investmitra.web.dto.Dtos.WatchlistView;

@Service
public class WatchlistService {

	private final WatchlistItemRepository items;
	private final UserService users;
	private final StockService stockService;
	private final Clock clock;

	public WatchlistService(WatchlistItemRepository items, UserService users, StockService stockService, Clock clock) {
		this.items = items;
		this.users = users;
		this.stockService = stockService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<WatchlistView> list(Long userId) {
		List<WatchlistItem> list = items.findByUser(users.require(userId));
		Map<Long, StockPrice> latest = stockService.latestPrices(list.stream().map(i -> i.getStock().getId()).toList());
		return list.stream().map(i -> view(i, latest.get(i.getStock().getId()))).toList();
	}

	/** Adds the stock, or updates its target price if it is already on the list. */
	@Transactional
	public WatchlistView add(Long userId, WatchlistRequest request) {
		AppUser user = users.require(userId);
		Stock stock = stockService.require(request.symbol());
		WatchlistItem item = items.findByUserAndStock(user, stock).orElseGet(() -> {
			WatchlistItem i = new WatchlistItem();
			i.setUser(user);
			i.setStock(stock);
			i.setAddedOn(LocalDate.now(clock.withZone(MarketCalendar.IST)));
			return i;
		});
		item.setTargetPrice(request.targetPrice());
		items.save(item);
		return view(item, stockService.latestPrices(List.of(stock.getId())).get(stock.getId()));
	}

	@Transactional
	public void remove(Long userId, String symbol) {
		AppUser user = users.require(userId);
		Stock stock = stockService.require(symbol);
		WatchlistItem item = items.findByUserAndStock(user, stock)
				.orElseThrow(() -> ApiException.notFound(stock.getSymbol() + " is not on your watchlist"));
		items.delete(item);
	}

	private WatchlistView view(WatchlistItem i, StockPrice price) {
		boolean reached = i.getTargetPrice() != null && price != null
				&& price.getClose().compareTo(i.getTargetPrice()) <= 0;
		return new WatchlistView(i.getStock().getSymbol(), i.getStock().getName(), i.getStock().getSector(),
				i.getTargetPrice(), i.getAddedOn(), stockService.priceView(price), reached);
	}
}
