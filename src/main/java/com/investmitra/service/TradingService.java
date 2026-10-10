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

	public TradingService(PortfolioService portfolios, StockService stockService, AppUserRepository users,
			HoldingRepository holdings, TradeRepository trades, StockPriceRepository prices, Clock clock) {
		this.portfolios = portfolios;
		this.stockService = stockService;
		this.users = users;
		this.holdings = holdings;
		this.trades = trades;
		this.prices = prices;
		this.clock = clock;
	}

	@Transactional
	public TradeResult trade(Long actorId, Long portfolioId, TradeRequest request) {
		Portfolio portfolio = portfolios.requireManaged(actorId, portfolioId);
		Stock stock = stockService.require(request.symbol());
		StockPrice price = prices.findTopByStockOrderByTradeDateDesc(stock)
				.orElseThrow(() -> ApiException.conflict("No price available yet for " + stock.getSymbol()));
		AppUser owner = users.findByIdForUpdate(portfolio.getOwner().getId()).orElseThrow();
		AppUser actor = actorId.equals(owner.getId()) ? owner : users.getReferenceById(actorId);

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

		if (request.side() == TradeSide.SELL) {
			holding = sell(portfolio, owner, stock, request.quantity(), price.getClose(), trade);
		}
		trades.save(trade);

		return new TradeResult(PortfolioService.tradeView(trade, price.getTradeDate()),
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
		holding.setAvgBuyPrice(PositionMath.averageAfterBuy(holding.getQuantity(), holding.getAvgBuyPrice(), qty, price));
		holding.setQuantity(holding.getQuantity() + qty);
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
