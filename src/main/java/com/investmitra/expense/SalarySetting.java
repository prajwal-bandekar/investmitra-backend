package com.investmitra.expense;

import java.math.BigDecimal;
import java.time.Instant;

import com.investmitra.entity.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** Monthly salary from a month onward. The salary for month M is the row with the greatest effectiveMonth <= M. */
@Entity
@Table(name = "salary_setting", uniqueConstraints = @UniqueConstraint(columnNames = { "user_id", "effective_month" }))
@Getter
@Setter
@NoArgsConstructor
public class SalarySetting {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private AppUser user;

	/** YYYY-MM */
	@Column(name = "effective_month", nullable = false, length = 7)
	private String effectiveMonth;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;
}
