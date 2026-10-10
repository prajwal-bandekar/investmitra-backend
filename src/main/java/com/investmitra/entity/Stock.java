package com.investmitra.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A listed company. Only stocks with an Alpha Vantage symbol get daily prices. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Stock {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** NSE trading symbol, e.g. TCS. */
	@Column(nullable = false, unique = true)
	private String symbol;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String sector;

	private String isin;

	/** Symbol used for Alpha Vantage, e.g. TCS.BSE. Null when the stock is not tracked. */
	private String avSymbol;

	public boolean isTracked() {
		return avSymbol != null;
	}
}
