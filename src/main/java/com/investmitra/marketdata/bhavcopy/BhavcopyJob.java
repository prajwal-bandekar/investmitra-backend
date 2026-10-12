package com.investmitra.marketdata.bhavcopy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.investmitra.entity.FetchTrigger;
import com.investmitra.marketdata.MarketCalendar;
import com.investmitra.marketdata.bhavcopy.BhavcopyImporter.ImportResult;
import com.investmitra.service.ApiException;

/**
 * Imports today's bhavcopy after the market closes (19:30 IST), with two retries 30 minutes apart
 * in case an exchange posts late. A retry does nothing once an import has already succeeded today.
 */
@Component
@ConditionalOnProperty(name = "bhavcopy.enabled", havingValue = "true", matchIfMissing = true)
public class BhavcopyJob {

	private static final Logger log = LoggerFactory.getLogger(BhavcopyJob.class);

	private final BhavcopyImporter importer;
	private final MarketCalendar calendar;

	public BhavcopyJob(BhavcopyImporter importer, MarketCalendar calendar) {
		this.importer = importer;
		this.calendar = calendar;
	}

	@Scheduled(cron = "${bhavcopy.cron}", zone = "Asia/Kolkata")
	public void run() {
		execute(false);
	}

	@Scheduled(cron = "${bhavcopy.retry-cron}", zone = "Asia/Kolkata")
	public void retry() {
		execute(true);
	}

	void execute(boolean isRetry) {
		var today = calendar.today();
		if (!calendar.isTradingDay(today)) {
			return;
		}
		if (isRetry && importer.importedSince(calendar.startOfToday())) {
			return;
		}
		try {
			ImportResult r = importer.importDay(today, FetchTrigger.SCHEDULED);
			log.info("Bhavcopy {} {}: {} stocks created, {} prices saved. {}", today, r.status(), r.stocksCreated(),
					r.pricesSaved(), r.notes());
		} catch (ApiException e) {
			log.warn("Bhavcopy import skipped: {}", e.getMessage());
		}
	}
}
