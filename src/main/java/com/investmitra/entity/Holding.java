package com.investmitra.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Current position in one stock within a portfolio. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "portfolio_id", "stock_id" }))
@Getter
@Setter
@NoArgsConstructor
public class Holding {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private Portfolio portfolio;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private Stock stock;

	@Column(nullable = false)
	private long quantity;

	@Column(nullable = false, precision = 19, scale = 4)
	private BigDecimal avgBuyPrice;
}
