package com.investmitra.marketdata.bhavcopy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.investmitra.entity.FetchTrigger;
import com.investmitra.entity.JobRun;
import com.investmitra.entity.JobStatus;
import com.investmitra.entity.Stock;
import com.investmitra.entity.StockPrice;
import com.investmitra.marketdata.MarketDataService;
import com.investmitra.marketdata.AlphaVantageClient;
import com.investmitra.marketdata.bhavcopy.BhavcopyClient.Exchange;
import com.investmitra.marketdata.bhavcopy.BhavcopyImporter.ImportResult;
import com.investmitra.repository.JobRunRepository;
import com.investmitra.repository.StockPriceRepository;
import com.investmitra.repository.StockRepository;
import com.investmitra.service.ApiException;

/** Bhavcopy import against in-memory H2 with the exchange downloads faked. */
@SpringBootTest
class BhavcopyImporterTest {

	@MockitoBean
	AlphaVantageClient alphaVantage;

	@MockitoBean
	BhavcopyClient client;

	@Autowired BhavcopyImporter importer;
	@Autowired MarketDataService marketData;
	@Autowired StockRepository stockRepo;
	@Autowired StockPriceRepository priceRepo;
	@Autowired JobRunRepository jobRunRepo;

	private static BhavcopyRow row(String exchange, LocalDate date, String symbol, String isin, String name, String close) {
		return new BhavcopyRow(date, exchange, "STK", symbol, "EQ", isin, name, new BigDecimal("10"),
				new BigDecimal("12"), new BigDecimal("9"), new BigDecimal(close), new BigDecimal("11"), 1000L);
	}

	@Test
	void importsNseAndBseCreatesStocksMatchesByIsinAndUpserts() {
		LocalDate day = LocalDate.of(2026, 10, 7);
		// TCS already exists in the catalog with ISIN INE467B01029; its symbol differs in the file on purpose.
		when(client.fetch(Exchange.NSE, day)).thenReturn(List.of(
				row("NSE", day, "TCS", "INE467B01029", "TATA CONSULTANCY SERVICES LTD", "4100.50"),
				row("NSE", day, "ZZNEWCO", "INE000ZZ0001", "ZZ New Co Ltd", "55.25")));
		when(client.fetch(Exchange.BSE, day)).thenReturn(List.of(
				row("BSE", day, "TCS", "INE467B01029", "TCS LTD", "4099.00"),
				row("BSE", day, "ZZBSEONLY", "INE000ZZ0002", "ZZ Bse Only Ltd", "7.10"),
				// BSE ticker clashing with an existing symbol of a different ISIN is skipped.
				row("BSE", day, "TCS", "INE000ZZ0003", "Clash Ltd", "1.00")));

		long before = stockRepo.count();
		ImportResult r = importer.importDay(day, FetchTrigger.MANUAL);

		assertThat(r.status()).isEqualTo(JobStatus.SUCCESS);
		assertThat(r.published()).isTrue();
		assertThat(r.stocksCreated()).isEqualTo(2);
		assertThat(r.pricesSaved()).isEqualTo(3);
		assertThat(r.notes()).contains("1 skipped");
		assertThat(stockRepo.count()).isEqualTo(before + 2);

		Stock created = stockRepo.findBySymbolIgnoreCase("ZZNEWCO").orElseThrow();
		assertThat(created.getSector()).isEqualTo("Other");
		assertThat(created.getAvSymbol()).isNull();
		assertThat(created.getName()).isEqualTo("ZZ New Co Ltd");
		assertThat(stockRepo.findBySymbolIgnoreCase("ZZBSEONLY")).isPresent();

		Stock tcs = stockRepo.findBySymbolIgnoreCase("TCS").orElseThrow();
		StockPrice tcsPrice = priceRepo.findByStockAndTradeDate(tcs, day).orElseThrow();
		assertThat(tcsPrice.getClose()).isEqualByComparingTo("4100.50"); // NSE wins over BSE
		assertThat(tcsPrice.getPreviousClose()).isEqualByComparingTo("11");
		assertThat(tcsPrice.getVolume()).isEqualTo(1000L);

		// Second run for the same day updates rather than duplicating.
		when(client.fetch(Exchange.NSE, day)).thenReturn(List.of(
				row("NSE", day, "ZZNEWCO", "INE000ZZ0001", "ZZ New Co Ltd", "56.00")));
		long prices = priceRepo.count();
		ImportResult again = importer.importDay(day, FetchTrigger.MANUAL);
		assertThat(again.stocksCreated()).isZero();
		assertThat(stockRepo.count()).isEqualTo(before + 2);
		assertThat(priceRepo.count()).isEqualTo(prices);
		assertThat(priceRepo.findByStockAndTradeDate(created, day).orElseThrow().getClose()).isEqualByComparingTo("56.00");

		// A stock without an Alpha Vantage symbol still serves its stored price and never calls the API.
		var view = marketData.getQuote("ZZNEWCO");
		assertThat(view.close()).isEqualByComparingTo("56.00");
		org.mockito.Mockito.verifyNoInteractions(alphaVantage);

		JobRun last = jobRunRepo.findTop20ByOrderByStartedAtDesc().getFirst();
		assertThat(last.getSource()).isEqualTo(JobRun.SOURCE_BHAVCOPY);
		assertThat(last.getPricesSaved()).isEqualTo(3);
	}

	@Test
	void backfillsIsinOnExistingStockMatchedBySymbol() {
		LocalDate day = LocalDate.of(2026, 10, 6);
		Stock s = new Stock();
		s.setSymbol("ZZNOISIN");
		s.setName("No Isin Ltd");
		s.setSector("Energy");
		stockRepo.save(s);
		when(client.fetch(Exchange.NSE, day)).thenReturn(List.of(
				row("NSE", day, "zznoisin", "INE000ZZ0004", "No Isin Limited", "20")));
		when(client.fetch(Exchange.BSE, day)).thenThrow(new BhavcopyException.NotPublished("BSE not posted"));

		ImportResult r = importer.importDay(day, FetchTrigger.SCHEDULED);

		assertThat(r.status()).isEqualTo(JobStatus.PARTIAL);
		assertThat(r.stocksCreated()).isZero();
		Stock after = stockRepo.findBySymbolIgnoreCase("ZZNOISIN").orElseThrow();
		assertThat(after.getIsin()).isEqualTo("INE000ZZ0004");
		assertThat(after.getSector()).isEqualTo("Energy");
		assertThat(priceRepo.findByStockAndTradeDate(after, day)).isPresent();
	}

	@Test
	void bothNotPublishedIsTreatedAsHoliday() {
		LocalDate day = LocalDate.of(2026, 10, 2);
		when(client.fetch(any(), eq(day))).thenThrow(new BhavcopyException.NotPublished("none"));

		ImportResult r = importer.importDay(day, FetchTrigger.MANUAL);

		assertThat(r.published()).isFalse();
		assertThat(r.status()).isEqualTo(JobStatus.SUCCESS);
		assertThat(r.notes()).contains("holiday");
		assertThat(r.pricesSaved()).isZero();
	}

	@Test
	void otherExchangeStillImportedWhenOneFails() {
		LocalDate day = LocalDate.of(2026, 10, 1);
		when(client.fetch(Exchange.NSE, day)).thenThrow(new BhavcopyException("NSE boom"));
		when(client.fetch(Exchange.BSE, day)).thenReturn(List.of(
				row("BSE", day, "ZZFALLBACK", "INE000ZZ0005", "Fallback Ltd", "3")));

		ImportResult r = importer.importDay(day, FetchTrigger.MANUAL);

		assertThat(r.status()).isEqualTo(JobStatus.PARTIAL);
		assertThat(r.failures()).isEqualTo(1);
		assertThat(r.stocksCreated()).isEqualTo(1);
		assertThat(r.pricesSaved()).isEqualTo(1);
	}

	@Test
	void skipsNonPositiveClosesWrongDatesAndReportsMalformedRows() {
		LocalDate day = LocalDate.of(2026, 9, 29);
		BhavcopyParser.ParsedRows nse = new BhavcopyParser.ParsedRows(4);
		nse.add(row("NSE", day, "ZZGOOD", "INE000ZZ0011", "Good Ltd", "10"));
		nse.add(row("NSE", day, "ZZZERO", "INE000ZZ0012", "Zero Ltd", "0"));
		nse.add(row("NSE", day, "ZZNEG", "INE000ZZ0013", "Neg Ltd", "-1"));
		nse.add(row("NSE", day.minusDays(1), "ZZOLD", "INE000ZZ0014", "Old Ltd", "5"));
		when(client.fetch(Exchange.NSE, day)).thenReturn(nse);
		when(client.fetch(Exchange.BSE, day)).thenThrow(new BhavcopyException.NotPublished("x"));

		ImportResult r = importer.importDay(day, FetchTrigger.MANUAL);

		assertThat(r.pricesSaved()).isEqualTo(1);
		assertThat(r.stocksCreated()).isEqualTo(1);
		assertThat(stockRepo.findBySymbolIgnoreCase("ZZZERO")).isEmpty();
		assertThat(stockRepo.findBySymbolIgnoreCase("ZZOLD")).isEmpty();
		assertThat(r.notes()).contains("4 unreadable rows", "1 rows skipped (other trade date)",
				"2 rows skipped (close <= 0)");
	}

	@Test
	void fileForAnotherDateIsTreatedAsNotPublished() {
		LocalDate day = LocalDate.of(2026, 9, 28);
		LocalDate other = day.minusDays(3);
		when(client.fetch(Exchange.NSE, day)).thenReturn(List.of(
				row("NSE", other, "ZZSTALE", "INE000ZZ0015", "Stale Ltd", "5")));
		when(client.fetch(Exchange.BSE, day)).thenReturn(List.of(
				row("BSE", day, "ZZFRESH", "INE000ZZ0016", "Fresh Ltd", "6")));

		ImportResult r = importer.importDay(day, FetchTrigger.MANUAL);

		assertThat(r.status()).isEqualTo(JobStatus.PARTIAL);
		assertThat(r.failures()).isZero();
		assertThat(r.notes()).contains("NSE: file is for " + other);
		assertThat(stockRepo.findBySymbolIgnoreCase("ZZSTALE")).isEmpty();
		assertThat(r.pricesSaved()).isEqualTo(1);

		// Both files for the wrong date: same as a holiday.
		LocalDate day2 = LocalDate.of(2026, 9, 27);
		when(client.fetch(Exchange.NSE, day2)).thenReturn(List.of(
				row("NSE", other, "ZZSTALE", "INE000ZZ0015", "Stale Ltd", "5")));
		when(client.fetch(Exchange.BSE, day2)).thenReturn(List.of(
				row("BSE", other, "ZZSTALE", "INE000ZZ0015", "Stale Ltd", "5")));
		assertThat(importer.importDay(day2, FetchTrigger.MANUAL).published()).isFalse();
	}

	@Test
	void searchListsStocksWithPricesFirst() {
		LocalDate day = LocalDate.of(2026, 9, 30);
		Stock plain = new Stock();
		plain.setSymbol("ZZAAAFIRSTALPHA");
		plain.setName("ZZSEARCH plain");
		plain.setSector("Other");
		stockRepo.save(plain);
		when(client.fetch(Exchange.NSE, day)).thenReturn(List.of(
				row("NSE", day, "ZZZPRICED", "INE000ZZ0006", "ZZSEARCH priced", "9")));
		when(client.fetch(Exchange.BSE, day)).thenThrow(new BhavcopyException.NotPublished("x"));
		importer.importDay(day, FetchTrigger.MANUAL);

		List<String> order = stockRepo.search("ZZSEARCH", null, org.springframework.data.domain.PageRequest.of(0, 10))
				.map(Stock::getSymbol).getContent();
		assertThat(order).containsExactly("ZZZPRICED", "ZZAAAFIRSTALPHA");
	}

	@Test
	void onDemandQuoteForUnpricedUntrackedStockDoesNotCallAlphaVantage() {
		Stock s = new Stock();
		s.setSymbol("ZZNOPRICE");
		s.setName("No price");
		s.setSector("Other");
		stockRepo.save(s);

		assertThatThrownBy(() -> marketData.getQuote("ZZNOPRICE")).isInstanceOf(ApiException.class);
		org.mockito.Mockito.verifyNoInteractions(alphaVantage);
	}
}
