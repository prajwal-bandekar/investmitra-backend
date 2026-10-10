package com.investmitra.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Portfolio {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	/** Whose cash funds this portfolio. */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private AppUser owner;

	/** Advisor allowed to manage the portfolio for the owner, if any. */
	@ManyToOne(fetch = FetchType.LAZY)
	private AppUser advisor;

	/** When set, only stocks of this sector can be bought into the portfolio. */
	private String sector;

	@Column(nullable = false)
	private LocalDate createdOn;

	public boolean canBeManagedBy(Long userId) {
		return owner.getId().equals(userId) || (advisor != null && advisor.getId().equals(userId));
	}
}
