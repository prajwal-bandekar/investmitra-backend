package com.investmitra.expense;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.investmitra.entity.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Real money a user spent, logged for tracking only. Nothing is ever moved. */
@Entity
@Table(indexes = @Index(columnList = "user_id, spentOn"))
@Getter
@Setter
@NoArgsConstructor
public class Expense {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private AppUser user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private ExpenseCategory category;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	@Column(nullable = false)
	private LocalDate spentOn;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentMode paymentMode;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ExpenseSource source;

	private String note;

	@Column(nullable = false)
	private Instant createdAt;
}
