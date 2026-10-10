package com.investmitra.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.investmitra.config.AlphaVantageProperties;
import com.investmitra.marketdata.external.GlobalQuoteResponse.GlobalQuote;

import tools.jackson.databind.json.JsonMapper;

class AlphaVantageClientTest {

	private final AlphaVantageClient client = new AlphaVantageClient(RestClient.builder(),
			new AlphaVantageProperties("http://localhost", "key", Duration.ZERO, 25, ".BSE"),
			JsonMapper.builder().build());

	@Test
	void mapsGlobalQuoteIntoExternalModel() throws IOException {
		GlobalQuote q = client.toQuote("TCS.BSE", sample("quote-tcs.json"));

		assertThat(q.symbol()).isEqualTo("TCS.BSE");
		assertThat(q.price()).isEqualByComparingTo("3220.1499");
		assertThat(q.open()).isEqualByComparingTo("3198.70");
		assertThat(q.previousClose()).isEqualByComparingTo(new BigDecimal("3191.6001"));
		assertThat(q.volume()).isEqualTo(113769L);
		assertThat(q.latestTradingDay()).isEqualTo(LocalDate.of(2025, 12, 12));
		assertThat(q.changePercent()).isEqualTo("0.8945%");
	}

	@Test
	void informationBodyMeansRateLimited() throws IOException {
		assertThatThrownBy(() -> client.toQuote("RELIANCE.BSE", sample("rate-limited.json")))
				.isInstanceOf(AlphaVantageException.RateLimited.class)
				.hasMessageContaining("1 request per second");
	}

	@Test
	void errorMessageMeansSymbolNotFound() throws IOException {
		assertThatThrownBy(() -> client.toQuote("NOPE.BSE", sample("error.json")))
				.isInstanceOf(AlphaVantageException.SymbolNotFound.class)
				.hasMessageContaining("NOPE.BSE");
	}

	@Test
	void emptyQuoteMeansSymbolNotFound() throws IOException {
		assertThatThrownBy(() -> client.toQuote("NOPE.BSE", sample("empty-quote.json")))
				.isInstanceOf(AlphaVantageException.SymbolNotFound.class);
	}

	@Test
	void garbageBodyIsReportedNotSwallowed() {
		assertThatThrownBy(() -> client.toQuote("TCS.BSE", "<html>maintenance</html>"))
				.isInstanceOf(AlphaVantageException.class)
				.isNotInstanceOf(AlphaVantageException.RateLimited.class);
	}

	private static String sample(String name) throws IOException {
		try (InputStream in = AlphaVantageClientTest.class.getResourceAsStream("/alphavantage/" + name)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
