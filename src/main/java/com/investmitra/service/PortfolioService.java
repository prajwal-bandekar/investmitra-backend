package com.investmitra.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.investmitra.entity.AppUser;
import com.investmitra.entity.Holding;
import com.investmitra.entity.Portfolio;
import com.investmitra.entity.Role;
import com.investmitra.entity.StockPrice;
import com.investmitra.entity.Trade;
import com.investmitra.marketdata.MarketCalendar;
import com.investmitra.repository.HoldingRepository;
import com.investmitra.repository.PortfolioRepository;
import com.investmitra.repository.StockRepository;
import com.investmitra.repository.TradeRepository;
import com.investmitra.web.dto.Dtos;
import com.investmitra.web.dto.Dtos.CreatePortfolioRequest;
import com.investmitra.web.dto.Dtos.HoldingView;
import com.investmitra.web.dto.Dtos.PortfolioDetail;
import com.investmitra.web.dto.Dtos.PortfolioSummary;
import com.investmitra.web.dto.Dtos.TradeView;

@Service
public class PortfolioService {

	private final PortfolioRepository portfolios;
	private final HoldingRepository holdings;
	private final TradeRepository trades;
	private final StockRepository stocks;
	private final UserService users;
	private final StockService stockService;
	private final Clock clock;

	public PortfolioService(PortfolioRepository portfolios, HoldingRepository holdings, TradeRepository trades,
			StockRepository stocks, UserService users, StockService stockService, Clock clock) {
		this.portfolios = portfolios;
		this.holdings = holdings;
		this.trades = trades;
		this.stocks = stocks;
		this.users = users;
		this.stockService = stockService;
		this.clock = clock;
	}

	/** A user creates their own portfolio; an advisor may also create one for a client via ownerId. */
	@Transactional
	public PortfolioSummary create(Long actorId, CreatePortfolioRequest request) {
		AppUser actor = users.require(actorId);
		Portfolio p = new Portfolio();
		p.setName(request.name().trim());
		p.setCreatedOn(LocalDate.now(clock.withZone(MarketCalendar.IST)));

		if (request.ownerId() == null || request.ownerId().equals(actorId)) {
			p.setOwner(actor);
			if (request.advisorId() != null) {
				// The owner is the one creating the portfolio, so naming an advisor is their consent.
				AppUser advisor = users.require(request.advisorId());
				if (advisor.getRole() != Role.ADVISOR || advisor.getId().equals(actorId)) {
					throw ApiException.badRequest("User " + request.advisorId() + " is not an advisor");
				}
				p.setAdvisor(advisor);
			}
		} else {
			if (actor.getRole() != Role.ADVISOR) {
				throw ApiException.forbidden("Only advisors can create portfolios for other users");
			}
			AppUser owner = users.require(request.ownerId());
			if (!portfolios.existsByOwnerIdAndAdvisorId(owner.getId(), actorId)) {
				throw ApiException.forbidden("User " + request.ownerId() + " is not your client yet. Ask them to create "
						+ "a portfolio and appoint you as advisor; after that you can create more for them");
			}
			p.setOwner(owner);
			p.setAdvisor(actor);
		}

		if (request.sector() != null && !request.sector().isBlank()) {
			String sector = request.sector().trim();
			boolean known = stocks.sectorSummary().stream().anyMatch(r -> sector.equals(r[0]));
			if (!known) {
				throw ApiException.badRequest("Unknown sector: " + sector + ". See GET /api/sectors");
			}
			p.setSector(sector);
		}
		return summary(portfolios.save(p));
	}

	/** Every portfolio the user owns or advises, each valued at the latest stored closes. */
	@Transactional(readOnly = true)
	public List<PortfolioSummary> listManagedBy(Long userId) {
		List<Portfolio> list = portfolios.findManagedBy(userId);
		List<Holding> held = list.isEmpty() ? List.of() : holdings.findByPortfolioIn(list);
		Map<Long, StockPrice> latest = stockService.latestPrices(held.stream().map(h -> h.getStock().getId()).distinct().toList());
		Map<Long, List<Holding>> byPortfolio = held.stream().collect(java.util.stream.Collectors.groupingBy(h -> h.getPortfolio().getId()));
		return list.stream().map(p -> {
			List<Holding> mine = byPortfolio.getOrDefault(p.getId(), List.of());
			BigDecimal invested = BigDecimal.ZERO;
			BigDecimal value = BigDecimal.ZERO;
			for (Holding h : mine) {
				BigDecimal cost = PositionMath.amount(h.getAvgBuyPrice(), h.getQuantity());
				StockPrice price = latest.get(h.getStock().getId());
				invested = invested.add(cost);
				value = value.add(price == null ? cost : PositionMath.amount(price.getClose(), h.getQuantity()));
			}
			BigDecimal pnl = value.subtract(invested).add(trades.totalRealizedPnl(p)).setScale(2, java.math.RoundingMode.HALF_UP);
			return summary(p, value, pnl);
		}).toList();
	}

	@Transactional(readOnly = true)
	public PortfolioDetail detail(Long actorId, Long portfolioId) {
		Portfolio p = requireManaged(actorId, portfolioId);
		List<Holding> held = holdings.findByPortfolio(p);
		Map<Long, StockPrice> latest = stockService.latestPrices(held.stream().map(h -> h.getStock().getId()).toList());
		List<HoldingView> views = held.stream().map(h -> holdingView(h, latest.get(h.getStock().getId()))).toList();

		BigDecimal invested = views.stream().map(HoldingView::invested).reduce(BigDecimal.ZERO, BigDecimal::add);
		// Holdings without a stored price are valued at cost.
		BigDecimal value = views.stream()
				.map(v -> v.currentValue() != null ? v.currentValue() : v.invested())
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal pnl = value.subtract(invested);
		return new PortfolioDetail(summary(p), views, invested, value, pnl, PositionMath.percent(pnl, invested),
				trades.totalRealizedPnl(p).setScale(2, java.math.RoundingMode.HALF_UP), p.getOwner().getCashBalance());
	}

	@Transactional(readOnly = true)
	public List<TradeView> tradeHistory(Long actorId, Long portfolioId) {
		Portfolio p = requireManaged(actorId, portfolioId);
		return trades.findByPortfolioNewestFirst(p).stream().map(t -> tradeView(t, null)).toList();
	}

	public Portfolio requireManaged(Long actorId, Long portfolioId) {
		Portfolio p = portfolios.findById(portfolioId)
				.orElseThrow(() -> ApiException.notFound("Unknown portfolio: " + portfolioId));
		if (!p.canBeManagedBy(actorId)) {
			throw ApiException.forbidden("You do not manage portfolio " + portfolioId);
		}
		return p;
	}

	static PortfolioSummary summary(Portfolio p) {
		return summary(p, null, null);
	}

	static PortfolioSummary summary(Portfolio p, BigDecimal value, BigDecimal pnl) {
		return new PortfolioSummary(p.getId(), p.getName(), p.getSector(), p.getOwner().getId(),
				Dtos.clientId(p.getOwner().getId()), p.getOwner().getName(),
				p.getAdvisor() == null ? null : p.getAdvisor().getId(), p.getCreatedOn(), value, pnl);
	}

	static HoldingView holdingView(Holding h, StockPrice price) {
		BigDecimal invested = PositionMath.amount(h.getAvgBuyPrice(), h.getQuantity());
		BigDecimal value = price == null ? null : PositionMath.amount(price.getClose(), h.getQuantity());
		BigDecimal pnl = value == null ? null : value.subtract(invested);
		return new HoldingView(h.getStock().getSymbol(), h.getStock().getName(), h.getStock().getSector(),
				h.getQuantity(), h.getAvgBuyPrice(), invested, price == null ? null : price.getClose(),
				price == null ? null : price.getTradeDate(), value, pnl,
				pnl == null ? null : PositionMath.percent(pnl, invested));
	}

	static TradeView tradeView(Trade t, LocalDate priceAsOf) {
		return new TradeView(t.getId(), t.getStock().getSymbol(), t.getSide(), t.getQuantity(), t.getPrice(),
				t.getAmount(), t.getRealizedPnl(), priceAsOf, t.getPlacedBy().getId(), t.getExecutedAt());
	}
}
