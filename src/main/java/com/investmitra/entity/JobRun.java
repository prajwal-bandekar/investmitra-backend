package com.investmitra.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Audit row for one batch of Alpha Vantage calls: a daily job run, a manual run,
 * or a single on-demand quote. Summing {@code callsMade} gives today's quota usage.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class JobRun {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "run_trigger", nullable = false)
	private FetchTrigger trigger;

	@Column(nullable = false)
	private Instant startedAt;

	private Instant finishedAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private JobStatus status;

	/** API calls actually sent; counted against the daily budget. */
	private int callsMade;

	private int pricesSaved;

	private int failures;

	@Column(length = 2000)
	private String notes;
}
