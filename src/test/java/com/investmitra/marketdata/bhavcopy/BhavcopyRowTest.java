package com.investmitra.marketdata.bhavcopy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class BhavcopyRowTest {

	private static BhavcopyRow bse(String series, String isin) {
		return new BhavcopyRow(LocalDate.of(2026, 10, 9), "BSE", "STK", "X", series, isin, "X", null, null, null,
				BigDecimal.ONE, null, null);
	}

	@Test
	void bseBondsAndInvitsAreNotEquity() {
		for (String group : new String[] { "F", "IF", "G", "GS" }) {
			assertThat(bse(group, "INE000000001").isEquity()).as(group).isFalse();
		}
		for (String group : new String[] { "B", "X", "A", "XT", "M", "T", "MT", "Z", "P", "MS", "ZP", "TS", "R" }) {
			assertThat(bse(group, "INE000000001").isEquity()).as(group).isTrue();
		}
		assertThat(bse("A", "INF000000001").isEquity()).isFalse();
	}

	/** Runs only on a machine that has the real 2026-10-09 BSE file in its temp folder. */
	@Test
	void realBseFileHasNoBondsAmongEquities() throws Exception {
		Path file = Path.of(System.getenv().getOrDefault("LOCALAPPDATA", "."), "Temp", "bse_20261009.csv");
		Assumptions.assumeTrue(Files.exists(file));
		var equities = BhavcopyParser.equities(BhavcopyParser.parse(Files.readAllBytes(file)));
		assertThat(equities).isNotEmpty();
		assertThat(equities).noneMatch(r -> r.name() != null && (r.name().contains("%") || r.name().toUpperCase().endsWith("-BOND")));
	}
}
