package com.investmitra.marketdata.bhavcopy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

class BhavcopyParserTest {

	private static final String HEADER = "TradDt,BizDt,Sgmt,Src,FinInstrmTp,FinInstrmId,ISIN,TckrSymb,SctySrs,FinInstrmNm,OpnPric,HghPric,LwPric,ClsPric,LastPric,PrvsClsgPric,TtlTradgVol";
	private static final String CSV = HEADER + "\n"
			+ "2026-10-09,2026-10-09,CM,NSE,STK,2885,INE002A01018,RELIANCE,EQ,RELIANCE INDUSTRIES LTD,1400.00,1420.50,1395.00,1410.25,1410.00,1398.00,1234567\n"
			+ "2026-10-09,2026-10-09,CM,NSE,STK,11536,INE467B01029,TCS,EQ,TATA CONSULTANCY SERVICES LTD,3000,3050,2990,3040.5,3040,3010,500000.00\n"
			+ "2026-10-09,2026-10-09,CM,NSE,NCD,99,INE000000001,SOMEBOND,N1,SOME BOND,100,100,100,100,100,100,10\n"
			+ "\n"
			+ "2026-10-09,2026-10-09,CM,NSE,STK,1,INE000000002,NOCLOSE,EQ,NO CLOSE LTD,10,11,9,,10,10,0\n";

	private static byte[] bytes(String s) {
		return s.getBytes(StandardCharsets.UTF_8);
	}

	@Test
	void parsesRowsByHeaderNameAndSkipsBlankCloseAndBlankLines() {
		List<BhavcopyRow> rows = BhavcopyParser.parse(bytes(CSV));

		assertThat(rows).hasSize(3);
		BhavcopyRow rel = rows.get(0);
		assertThat(rel.tradeDate()).isEqualTo(LocalDate.of(2026, 10, 9));
		assertThat(rel.exchange()).isEqualTo("NSE");
		assertThat(rel.symbol()).isEqualTo("RELIANCE");
		assertThat(rel.series()).isEqualTo("EQ");
		assertThat(rel.isin()).isEqualTo("INE002A01018");
		assertThat(rel.name()).isEqualTo("RELIANCE INDUSTRIES LTD");
		assertThat(rel.open()).isEqualByComparingTo("1400.00");
		assertThat(rel.high()).isEqualByComparingTo("1420.50");
		assertThat(rel.low()).isEqualByComparingTo("1395.00");
		assertThat(rel.close()).isEqualByComparingTo(new BigDecimal("1410.25"));
		assertThat(rel.previousClose()).isEqualByComparingTo("1398.00");
		assertThat(rel.volume()).isEqualTo(1234567L);
		assertThat(rows.get(1).volume()).isEqualTo(500000L);
		assertThat(rows).noneMatch(r -> r.symbol().equals("NOCLOSE"));
	}

	@Test
	void equitiesKeepsOnlyStk() {
		List<BhavcopyRow> equities = BhavcopyParser.equities(BhavcopyParser.parse(bytes(CSV)));

		assertThat(equities).extracting(BhavcopyRow::symbol).containsExactly("RELIANCE", "TCS");
	}

	private static final String GOOD = "2026-10-09,2026-10-09,CM,NSE,STK,1,INE000000010,GOOD1,EQ,G1,1,1,1,5,5,5,1\n";
	private static final String BAD_NUM = "2026-10-09,2026-10-09,CM,NSE,STK,1,INE000000011,BADNUM,EQ,B,1,1,1,abc,5,5,1\n";

	@Test
	void malformedRowsAreSkippedAndCounted() {
		String csv = HEADER + "\n" + GOOD + GOOD.replace("GOOD1", "GOOD2") + GOOD.replace("GOOD1", "GOOD3") + BAD_NUM
				+ "not-a-date,2026-10-09,CM,NSE,STK,1,INE000000012,BADDATE,EQ,B,1,1,1,5,5,5,1\n"
				+ "2026-10-09,2026-10-09,CM\n";

		List<BhavcopyRow> rows = BhavcopyParser.parse(bytes(csv));

		assertThat(rows).extracting(BhavcopyRow::symbol).containsExactly("GOOD1", "GOOD2", "GOOD3");
		assertThat(BhavcopyParser.skippedOf(rows)).isEqualTo(2);
	}

	@Test
	void failsWhenMoreThanHalfTheRowsAreBad() {
		String twoBad = HEADER + "\n" + GOOD + BAD_NUM + BAD_NUM.replace("BADNUM", "BADNUM2");

		assertThatThrownBy(() -> BhavcopyParser.parse(bytes(twoBad))).isInstanceOf(BhavcopyException.class)
				.hasMessageContaining("2 of 3");
		// exactly half bad is tolerated
		assertThat(BhavcopyParser.parse(bytes(HEADER + "\n" + GOOD + BAD_NUM))).hasSize(1);
	}

	@Test
	void columnOrderDoesNotMatter() {
		String reordered = "ClsPric,TckrSymb,FinInstrmTp,Src,TradDt\n1.5,ABC,STK,BSE,2026-10-09\n";

		List<BhavcopyRow> rows = BhavcopyParser.parse(bytes(reordered));

		assertThat(rows).hasSize(1);
		assertThat(rows.get(0).symbol()).isEqualTo("ABC");
		assertThat(rows.get(0).close()).isEqualByComparingTo("1.5");
		assertThat(rows.get(0).open()).isNull();
	}

	@Test
	void missingRequiredColumnFails() {
		String bad = "TradDt,Src,FinInstrmTp,TckrSymb\n2026-10-09,NSE,STK,ABC\n";

		assertThatThrownBy(() -> BhavcopyParser.parse(bytes(bad)))
				.isInstanceOf(BhavcopyException.class)
				.hasMessageContaining("missing column ClsPric");
	}

	@Test
	void emptyFileFails() {
		assertThatThrownBy(() -> BhavcopyParser.parse(new byte[0])).isInstanceOf(BhavcopyException.class);
	}

	@Test
	void unzipIfNeededExtractsFirstEntry() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(out)) {
			zip.putNextEntry(new ZipEntry("BhavCopy.csv"));
			zip.write(bytes(CSV));
			zip.closeEntry();
		}

		byte[] csv = BhavcopyParser.unzipIfNeeded(out.toByteArray());

		assertThat(csv).isEqualTo(bytes(CSV));
		assertThat(BhavcopyParser.parse(csv)).hasSize(3);
	}

	@Test
	void unzipIfNeededPassesPlainCsvThrough() {
		byte[] plain = bytes(CSV);
		assertThat(BhavcopyParser.unzipIfNeeded(plain)).isSameAs(plain);
	}

	@Test
	void emptyZipFails() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		new ZipOutputStream(out).close();

		assertThatThrownBy(() -> BhavcopyParser.unzipIfNeeded(out.toByteArray()))
				.isInstanceOf(BhavcopyException.class);
	}
}
