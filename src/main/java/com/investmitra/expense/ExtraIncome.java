package com.investmitra.expense;

import java.math.BigDecimal;
import java.time.Instant;

import com.investmitra.entity.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One-off income in a single month (bonus, freelance), on top of the salary. */
@Entity
@Table(name = "extra_income")
@Getter
@Setter
@NoArgsConstructor
public class ExtraIncome {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private AppUser user;

	/** YYYY-MM */
	@Column(name = "income_month", nullable = false, length = 7)
	private String incomeMonth;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	@Column(nullable = false, length = 60)
	private String label;

	@Column(nullable = false)
	private Instant createdAt;
}
