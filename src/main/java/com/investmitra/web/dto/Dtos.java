package com.investmitra.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.investmitra.entity.FetchTrigger;
import com.investmitra.entity.JobRun;
import com.investmitra.entity.JobStatus;
import com.investmitra.entity.Role;
import com.investmitra.entity.TradeSide;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Request and response bodies for the REST API. */
public final class Dtos {

	public static final long MAX_TRADE_QUANTITY = 10_000_000L;

	private Dtos() {
	}

	// ---- Users ----

	public record CreateUserRequest(@NotBlank @Size(max = 80) String name, @NotBlank @Email @Size(max = 254) String email,
			@NotBlank @Size(min = 6, max = 72) String password, @NotNull Role role) {
	}

	public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
	}

	/** @param clientId the ID a client gives an advisor, e.g. IM-2031 */
	public record UserView(Long id, String clientId, String name, String email, Role role, BigDecimal cashBalance) {
	}

	/** Display form of a user ID, as shared between clients and advisors. */
	public static String clientId(Long userId) {
		return String.format("IM-%04d", userId);
	}

	// ---- Stocks ----

	/** The caller's combined position in a stock across every portfolio they own or advise. */
	public record PositionView(long quantity, BigDecimal avgBuyPrice, BigDecimal invested, BigDecimal currentValue,
			BigDecimal unrealizedPnl, BigDecimal unrealizedPnlPercent) {
	}

	public record StockView(String symbol, String name, String sector, String isin, boolean tracked, PriceView price,
			PositionView myPosition) {
	}

	public record SectorView(String sector, long stocks, long trackedStocks) {
	}

	// ---- Portfolios ----

	/**
	 * @param ownerId set by an advisor creating a portfolio for a client; defaults to the caller
	 * @param sector  optional; restricts buys to stocks of this sector
	 * @param advisorId set by an owner creating their own portfolio to appoint an advisor (consent); the advisor
	 *                  must have the ADVISOR role
	 */
	public record CreatePortfolioRequest(@NotBlank @Size(max = 60) String name, @Size(max = 100) String sector,
			Long ownerId, Long advisorId) {
	}

	public record HoldingView(String symbol, String name, String sector, long quantity, BigDecimal avgBuyPrice,
			BigDecimal invested, BigDecimal lastPrice, LocalDate priceAsOf, BigDecimal currentValue,
			BigDecimal unrealizedPnl, BigDecimal unrealizedPnlPercent) {
	}

	/** @param totalPnl unrealised plus realised profit or loss; null when listing without valuation */
	public record PortfolioSummary(Long id, String name, String sector, Long ownerId, String ownerClientId,
			String ownerName, Long advisorId, LocalDate createdOn, BigDecimal currentValue, BigDecimal totalPnl) {
	}

	public record PortfolioDetail(PortfolioSummary portfolio, List<HoldingView> holdings, BigDecimal invested,
			BigDecimal currentValue, BigDecimal unrealizedPnl, BigDecimal unrealizedPnlPercent, BigDecimal realizedPnl,
			BigDecimal ownerCash) {
	}

	// ---- Trades ----

	/** @param clientRequestId optional idempotency key (max 64 chars); a repeat for the same portfolio returns the first result */
	public record TradeRequest(@NotBlank @Size(max = 40) String symbol, @NotNull TradeSide side,
			@Min(1) @Max(MAX_TRADE_QUANTITY) long quantity, @Size(max = 64) String clientRequestId) {
	}

	public record TradeView(Long id, String symbol, TradeSide side, long quantity, BigDecimal price,
			BigDecimal amount, BigDecimal realizedPnl, LocalDate priceAsOf, Long placedById, Instant executedAt) {
	}

	public record TradeResult(TradeView trade, HoldingView holding, BigDecimal ownerCash) {
	}

	// ---- Watchlist ----

	public record WatchlistRequest(@NotBlank @Size(max = 40) String symbol,
			@Positive @Digits(integer = 12, fraction = 2) BigDecimal targetPrice) {
	}

	public record WatchlistView(String symbol, String name, String sector, BigDecimal targetPrice, LocalDate addedOn,
			PriceView price, boolean targetReached) {
	}

	// ---- Price job ----

	public record JobRunView(Long id, FetchTrigger trigger, JobStatus status, Instant startedAt, Instant finishedAt,
			int callsMade, int pricesSaved, int failures, String notes, String source) {

		public static JobRunView of(JobRun r) {
			return new JobRunView(r.getId(), r.getTrigger(), r.getStatus(), r.getStartedAt(), r.getFinishedAt(),
					r.getCallsMade(), r.getPricesSaved(), r.getFailures(),
					com.investmitra.marketdata.SecretMasker.mask(r.getNotes()),
					r.getSource() == null ? JobRun.SOURCE_ALPHA_VANTAGE : r.getSource());
		}
	}

	public record QuotaView(int dailyBudget, int remainingToday) {
	}
}
