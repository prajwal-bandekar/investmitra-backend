package com.investmitra.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

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

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "user_id", "stock_id" }))
@Getter
@Setter
@NoArgsConstructor
public class WatchlistItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private AppUser user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private Stock stock;

	@Column(precision = 19, scale = 4)
	private BigDecimal targetPrice;

	@Column(nullable = false)
	private LocalDate addedOn;
}
