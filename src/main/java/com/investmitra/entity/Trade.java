package com.investmitra.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Immutable record of an executed buy or sell. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Trade {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private Portfolio portfolio;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private Stock stock;

	/** Who placed the trade: the owner or their advisor. */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private AppUser placedBy;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TradeSide side;

	@Column(nullable = false)
	private long quantity;

	@Column(nullable = false, precision = 19, scale = 4)
	private BigDecimal price;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	/** Profit or loss booked by a sell; null for buys. */
	@Column(precision = 19, scale = 2)
	private BigDecimal realizedPnl;

	@Column(nullable = false)
	private Instant executedAt;
}
