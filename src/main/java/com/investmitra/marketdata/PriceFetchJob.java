package com.investmitra.marketdata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.investmitra.entity.FetchTrigger;
import com.investmitra.entity.JobRun;
import com.investmitra.service.ApiException;

/** Fetches the previous day's closing prices every weekday morning, before the market opens. */
@Component
public class PriceFetchJob {

	private static final Logger log = LoggerFactory.getLogger(PriceFetchJob.class);

	private final MarketDataService marketData;

	public PriceFetchJob(MarketDataService marketData) {
		this.marketData = marketData;
	}

	@Scheduled(cron = "${pricejob.cron}", zone = "Asia/Kolkata")
	public void run() {
		try {
			JobRun run = marketData.refreshTrackedStocks(FetchTrigger.SCHEDULED);
			log.info("Price job {}: {} calls, {} saved, {} failures. {}", run.getStatus(), run.getCallsMade(),
					run.getPricesSaved(), run.getFailures(), run.getNotes());
		} catch (ApiException e) {
			log.warn("Price job skipped: {}", e.getMessage());
		}
	}
}
