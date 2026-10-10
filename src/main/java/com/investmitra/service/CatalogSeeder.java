package com.investmitra.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.investmitra.config.AlphaVantageProperties;
import com.investmitra.entity.Stock;
import com.investmitra.repository.StockRepository;

/**
 * Loads the NIFTY 500 list on first start, and on every start marks the configured
 * symbols as tracked so the daily job fetches their prices.
 */
@Component
public class CatalogSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(CatalogSeeder.class);
	private static final String CATALOG = "data/nse-500-stocks.json";

	private final StockRepository stocks;
	private final ObjectMapper mapper;
	private final AlphaVantageProperties avProps;
	private final List<String> trackedSymbols;

	public CatalogSeeder(StockRepository stocks, ObjectMapper mapper, AlphaVantageProperties avProps,
			@Value("${catalog.tracked}") List<String> trackedSymbols) {
		this.stocks = stocks;
		this.mapper = mapper;
		this.avProps = avProps;
		this.trackedSymbols = trackedSymbols;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record CatalogEntry(
			@JsonProperty("Company Name") String name,
			@JsonProperty("Industry") String industry,
			@JsonProperty("Symbol") String symbol,
			@JsonProperty("ISIN Code") String isin) {
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) throws IOException {
		if (stocks.count() == 0) {
			try (InputStream in = new ClassPathResource(CATALOG).getInputStream()) {
				List<CatalogEntry> entries = mapper.readValue(in, new TypeReference<>() {
				});
				stocks.saveAll(entries.stream().map(CatalogSeeder::toStock).toList());
				log.info("Loaded {} stocks from {}", entries.size(), CATALOG);
			}
		}
		syncTracked();
	}

	private void syncTracked() {
		Set<String> wanted = trackedSymbols.stream()
				.map(s -> s.trim().toUpperCase(Locale.ROOT))
				.filter(s -> !s.isEmpty())
				.collect(Collectors.toSet());
		int tracked = 0;
		for (Stock stock : stocks.findAll()) {
			String avSymbol = wanted.contains(stock.getSymbol()) ? stock.getSymbol() + avProps.exchangeSuffix() : null;
			if (avSymbol != null) {
				tracked++;
			}
			if (!java.util.Objects.equals(avSymbol, stock.getAvSymbol())) {
				stock.setAvSymbol(avSymbol);
			}
		}
		if (tracked < wanted.size()) {
			log.warn("{} of {} tracked symbols are not in the catalog", wanted.size() - tracked, wanted.size());
		}
		log.info("{} stocks tracked for daily prices", tracked);
	}

	private static Stock toStock(CatalogEntry e) {
		Stock s = new Stock();
		s.setSymbol(e.symbol().trim().toUpperCase(Locale.ROOT));
		s.setName(e.name().trim());
		s.setSector(e.industry().trim());
		s.setIsin(e.isin());
		return s;
	}
}
