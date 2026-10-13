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

/** A fixed monthly outgo (EMI, rent, subscription, SIP...). Active from startMonth to endMonth inclusive. */
@Entity
@Table(name = "commitment")
@Getter
@Setter
@NoArgsConstructor
public class Commitment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private AppUser user;

	@Column(nullable = false, length = 60)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "commitment_kind", nullable = false)
	private CommitmentKind kind;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	@Column(nullable = false)
	private int dueDay;

	/** YYYY-MM */
	@Column(name = "start_month", nullable = false, length = 7)
	private String startMonth;

	/** YYYY-MM, null means no end. */
	@Column(name = "end_month", length = 7)
	private String endMonth;

	@Column(nullable = false)
	private Instant createdAt;

	/** YYYY-MM strings sort chronologically. */
	public boolean activeIn(String month) {
		return startMonth.compareTo(month) <= 0 && (endMonth == null || endMonth.compareTo(month) >= 0);
	}
}
