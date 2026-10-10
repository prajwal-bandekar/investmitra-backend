package com.investmitra.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** End-of-day price snapshot, one row per stock per trading day. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "stock_id", "trade_date" }))
@Getter
@Setter
@NoArgsConstructor
public class StockPrice {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "stock_id")
	private Stock stock;

	@Column(name = "trade_date", nullable = false)
	private LocalDate tradeDate;

	@Column(name = "open_price", precision = 19, scale = 4)
	private BigDecimal open;

	@Column(name = "high_price", precision = 19, scale = 4)
	private BigDecimal high;

	@Column(name = "low_price", precision = 19, scale = 4)
	private BigDecimal low;

	@Column(name = "close_price", nullable = false, precision = 19, scale = 4)
	private BigDecimal close;

	@Column(precision = 19, scale = 4)
	private BigDecimal previousClose;

	private Long volume;

	@Column(nullable = false)
	private Instant fetchedAt;
}
