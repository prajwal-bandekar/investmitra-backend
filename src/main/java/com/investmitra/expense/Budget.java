package com.investmitra.expense;

import java.math.BigDecimal;

import com.investmitra.entity.AppUser;

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

/** Monthly spending limit for one category, or for the whole month when category is null. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "user_id", "category_id", "budget_month" }))
@Getter
@Setter
@NoArgsConstructor
public class Budget {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private AppUser user;

	@ManyToOne(fetch = FetchType.LAZY)
	private ExpenseCategory category;

	/** YYYY-MM */
	@Column(name = "budget_month", nullable = false, length = 7)
	private String month;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal limitAmount;
}
