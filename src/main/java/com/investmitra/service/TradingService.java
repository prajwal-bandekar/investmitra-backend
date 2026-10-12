package com.investmitra.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.investmitra.entity.AppUser;
import com.investmitra.entity.Holding;
import com.investmitra.entity.Portfolio;
import com.investmitra.entity.Stock;
import com.investmitra.entity.StockPrice;
import com.investmitra.entity.Trade;
import com.investmitra.entity.TradeSide;
import com.investmitra.marketdata.MarketCalendar;
import com.investmitra.repository.AppUserRepository;
import com.investmitra.repository.HoldingRepository;
import com.investmitra.repository.StockPriceRepository;
import com.investmitra.repository.TradeRepository;
import com.investmitra.web.dto.Dtos.TradeRequest;
import com.investmitra.web.dto.Dtos.TradeResult;

/**
 * Executes paper trades at the latest stored closing price. Cash, holding and trade log
 * change together in one transaction.
 */
@Service
public class TradingService {

	private final PortfolioService portfolios;
	private final StockService stockService;
	private final AppUserRepository users;
	private final HoldingRepository holdings;
	private final TradeRepository trades;
	private final StockPriceRepository prices;
	private final Clock clock;
	private final MarketCalendar calendar;

	/** A price older than this many calendar days (about 5 trading days) is too stale to buy at. */
	public static final int MAX_PRICE_AGE_DAYS = 7;

	public TradingService(PortfolioService portfolios, StockService stockService, AppUserRepository users,
			HoldingRepository holdings, TradeRepository trades, StockPriceRepository prices, Clock clock,
			MarketCalendar calendar) {
		this.portfolios = portfolios;
		this.stockService = stockService;
		this.users = users;
		this.holdings = holdings;
		this.trades = trades;
		this.prices = prices;
		this.clock = clock;
		this.calendar = calendar;
	}

	@Transactional
	public TradeResult trade(Long actorId, Long portfolioId, TradeRequest request) {
		Portfolio portfolio = portfolios.requireManaged(actorId, portfolioId);
		Stock stock = stockService.require(request.symbol());
		StockPrice price = prices.findTopByStockOrderByTradeDateDesc(stock)
				.orElseThrow(() -> ApiException.conflict("No price available yet for " + stock.getSymbol()));
		if (price.getClose() == null || price.getClose().signum() <= 0) {
			throw ApiException.conflict("No usable price for " + stock.getSymbol() + " (latest close is "
					+ price.getClose() + ")");
		}
		AppUser owner = users.findByIdForUpdate(portfolio.getOwner().getId()).orElseThrow();
		AppUser actor = actorId.equals(owner.getId()) ? owner : users.getReferenceById(actorId);

		String requestId = request.clientRequestId() == null || request.clientRequestId().isBlank() ? null
				: request.clientRequestId().trim();
		if (requestId != null) {
			// The owner row is locked, so two submissions of the same request run one after the other.
			Trade earlier = trades.findByPortfolioAndClientRequestId(portfolio, requestId).orElse(null);
			if (earlier != null) {
				return replay(earlier, stock, request, price, owner, portfolio);
			}
		}
		if (request.side() == TradeSide.BUY
				&& price.getTradeDate().isBefore(calendar.lastCompletedTradingDay().minusDays(MAX_PRICE_AGE_DAYS))) {
			throw ApiException.conflict("Latest price for " + stock.getSymbol() + " is from " + price.getTradeDate()
					+ " and too old to trade on; the stock may be delisted or its data feed has stopped");
		}

		Holding holding = request.side() == TradeSide.BUY
				? buy(portfolio, owner, stock, request.quantity(), price.getClose())
				: null;
		Trade trade = new Trade();
		trade.setPortfolio(portfolio);
		trade.setStock(stock);
		trade.setPlacedBy(actor);
		trade.setSide(request.side());
		trade.setQuantity(request.quantity());
		trade.setPrice(price.getClose());
		trade.setAmount(PositionMath.amount(price.getClose(), request.quantity()));
		trade.setExecutedAt(Instant.now(clock));
		trade.setClientRequestId(requestId);

		if (request.side() == TradeSide.SELL) {
			holding = sell(portfolio, owner, stock, request.quantity(), price.getClose(), trade);
		}
		trades.save(trade);

		return new TradeResult(PortfolioService.tradeView(trade, price.getTradeDate()),
				holding == null ? null : PortfolioService.holdingView(holding, price), owner.getCashBalance());
	}

	/** A repeated submission returns the original result instead of trading again. */
	private TradeResult replay(Trade earlier, Stock stock, TradeRequest request, StockPrice price, AppUser owner,
			Portfolio portfolio) {
		if (!earlier.getStock().getId().equals(stock.getId()) || earlier.getSide() != request.side()
				|| earlier.getQuantity() != request.quantity()) {
			throw ApiException.conflict("clientRequestId was already used for a different order");
		}
		Holding holding = holdings.findByPortfolioAndStock(portfolio, stock).orElse(null);
		return new TradeResult(PortfolioService.tradeView(earlier, price.getTradeDate()),
				holding == null ? null : PortfolioService.holdingView(holding, price), owner.getCashBalance());
	}

	private Holding buy(Portfolio portfolio, AppUser owner, Stock stock, long qty, BigDecimal price) {
		if (portfolio.getSector() != null && !portfolio.getSector().equals(stock.getSector())) {
			throw ApiException.badRequest(stock.getSymbol() + " is in " + stock.getSector() + "; portfolio '"
					+ portfolio.getName() + "' only holds " + portfolio.getSector() + " stocks");
		}
		BigDecimal cost = PositionMath.amount(price, qty);
		if (owner.getCashBalance().compareTo(cost) < 0) {
			throw ApiException.badRequest("Insufficient cash: need " + cost + ", available " + owner.getCashBalance());
		}
		owner.setCashBalance(owner.getCashBalance().subtract(cost));

		Holding holding = holdings.findByPortfolioAndStock(portfolio, stock).orElseGet(() -> {
			Holding h = new Holding();
			h.setPortfolio(portfolio);
			h.setStock(stock);
			h.setAvgBuyPrice(BigDecimal.ZERO);
			return h;
		});
		long newQty;
		try {
			newQty = Math.addExact(holding.getQuantity(), qty);
		} catch (ArithmeticException e) {
			throw ApiException.badRequest("Position in " + stock.getSymbol() + " would exceed the maximum size");
		}
		holding.setAvgBuyPrice(PositionMath.averageAfterBuy(holding.getQuantity(), holding.getAvgBuyPrice(), qty, price));
		holding.setQuantity(newQty);
		return holdings.save(holding);
	}

	/** Returns the remaining holding, or null when the position is fully closed. */
	private Holding sell(Portfolio portfolio, AppUser owner, Stock stock, long qty, BigDecimal price, Trade trade) {
		Holding holding = holdings.findByPortfolioAndStock(portfolio, stock)
				.orElseThrow(() -> ApiException.badRequest("No " + stock.getSymbol() + " in this portfolio to sell"));
		if (holding.getQuantity() < qty) {
			throw ApiException.badRequest("Cannot sell " + qty + " " + stock.getSymbol() + "; holding only "
					+ holding.getQuantity());
		}
		trade.setRealizedPnl(PositionMath.realizedPnl(holding.getAvgBuyPrice(), qty, price));
		owner.setCashBalance(owner.getCashBalance().add(PositionMath.amount(price, qty)));

		holding.setQuantity(holding.getQuantity() - qty);
		if (holding.getQuantity() == 0) {
			holdings.delete(holding);
			return null;
		}
		return holdings.save(holding);
	}
}
