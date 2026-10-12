package com.investmitra.expense;

import com.investmitra.entity.AppUser;

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

/** A spending category. System defaults have no user; users can add their own. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class ExpenseCategory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Null for the shared defaults. */
	@ManyToOne(fetch = FetchType.LAZY)
	private AppUser user;

	@Column(nullable = false)
	private String name;

	/** Short emoji shown next to the name. */
	private String icon;

	/** "Want, not need": spend here feeds the could-have-bought cards. */
	@Column(nullable = false)
	private boolean want;

	@Column(nullable = false)
	private boolean archived;
}
