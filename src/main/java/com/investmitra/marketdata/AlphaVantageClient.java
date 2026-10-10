package com.investmitra.marketdata;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.investmitra.config.AlphaVantageProperties;
import com.investmitra.marketdata.external.GlobalQuoteResponse;

/** Calls the Alpha Vantage REST API. Every method call costs one request from the daily quota. */
@Component
public class AlphaVantageClient {

	private final RestClient restClient;
	private final AlphaVantageProperties props;
	private final ObjectMapper mapper;

	public AlphaVantageClient(RestClient.Builder builder, AlphaVantageProperties props, ObjectMapper mapper) {
		this.restClient = builder.baseUrl(props.baseUrl()).build();
		this.props = props;
		this.mapper = mapper;
	}

	public boolean isConfigured() {
		return props.apiKey() != null && !props.apiKey().isBlank();
	}

	/**
	 * Latest end-of-day quote for one symbol, e.g. {@code TCS.BSE}.
	 *
	 * @throws AlphaVantageException.RateLimited    when the daily or per-second limit is hit
	 * @throws AlphaVantageException.SymbolNotFound when Alpha Vantage has no data for the symbol
	 */
	public GlobalQuoteResponse.GlobalQuote getGlobalQuote(String avSymbol) {
		if (!isConfigured()) {
			throw new AlphaVantageException("ALPHAVANTAGE_API_KEY is not set");
		}
		String body;
		try {
			body = restClient.get()
					.uri(uri -> uri.queryParam("function", "GLOBAL_QUOTE")
							.queryParam("symbol", avSymbol)
							.queryParam("apikey", props.apiKey())
							.build())
					.retrieve()
					.body(String.class);
		} catch (RestClientException e) {
			throw new AlphaVantageException("Request failed for " + avSymbol + ": " + e.getMessage(), e);
		}
		return toQuote(avSymbol, body);
	}

	/** Validates a raw GLOBAL_QUOTE body. Package-private so tests can feed saved responses. */
	GlobalQuoteResponse.GlobalQuote toQuote(String avSymbol, String body) {
		GlobalQuoteResponse response;
		try {
			response = mapper.readValue(body == null ? "" : body, GlobalQuoteResponse.class);
		} catch (JacksonException e) {
			throw new AlphaVantageException("Unreadable response for " + avSymbol + ": " + e.getOriginalMessage(), e);
		}
		if (response.isRateLimited()) {
			throw new AlphaVantageException.RateLimited(
					response.information() != null ? response.information() : response.note());
		}
		if (response.errorMessage() != null) {
			throw new AlphaVantageException.SymbolNotFound(avSymbol + ": " + response.errorMessage());
		}
		if (!response.hasQuote()) {
			throw new AlphaVantageException.SymbolNotFound(avSymbol + ": no quote returned");
		}
		return response.quote();
	}
}
