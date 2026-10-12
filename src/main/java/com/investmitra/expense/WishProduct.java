package com.investmitra.expense;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Something a Gen Z user wants, priced in rupees, used for the could-have-bought cards. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class WishProduct {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String name;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal price;

	/** Key of the illustration the frontend draws, e.g. "phone", "headphones". */
	@Column(nullable = false)
	private String image;

	@Column(nullable = false)
	private boolean active = true;
}
