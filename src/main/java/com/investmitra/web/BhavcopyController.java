package com.investmitra.web;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.investmitra.marketdata.bhavcopy.BhavcopyClient;
import com.investmitra.marketdata.bhavcopy.BhavcopyClient.Exchange;
import com.investmitra.marketdata.bhavcopy.BhavcopyException;
import com.investmitra.marketdata.bhavcopy.BhavcopyParser;
import com.investmitra.marketdata.bhavcopy.BhavcopyRow;
import com.investmitra.service.ApiException;
import com.investmitra.web.dto.ResponseStructure;

/** Trial, read-only preview of an exchange's daily bhavcopy. Nothing is saved. */
@RestController
@RequestMapping("/api/admin/bhavcopy")
public class BhavcopyController {

	static final int MAX_LIMIT = 200;

	private final BhavcopyClient client;

	public BhavcopyController(BhavcopyClient client) {
		this.client = client;
	}

	public record BhavcopyPreview(String exchange, LocalDate date, int totalRows, int equityRows, List<BhavcopyRow> rows) {
	}

	@GetMapping("/preview/{exchange}/{date}")
	public ResponseStructure<BhavcopyPreview> preview(@PathVariable String exchange, @PathVariable String date,
			@RequestParam(defaultValue = "true") boolean equityOnly,
			@RequestParam(defaultValue = "20") int limit) {
		Exchange ex;
		try {
			ex = Exchange.valueOf(exchange.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw ApiException.badRequest("Unknown exchange '" + exchange + "', use BSE or NSE");
		}
		LocalDate day;
		try {
			day = LocalDate.parse(date);
		} catch (DateTimeParseException e) {
			throw ApiException.badRequest("Date must look like 2026-10-09");
		}
		int capped = Math.max(0, Math.min(limit, MAX_LIMIT));
		List<BhavcopyRow> all;
		try {
			all = client.fetch(ex, day);
		} catch (BhavcopyException.NotPublished e) {
			throw ApiException.notFound(e.getMessage());
		} catch (BhavcopyException e) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, e.getMessage());
		}
		List<BhavcopyRow> equities = BhavcopyParser.equities(all);
		List<BhavcopyRow> source = equityOnly ? equities : all;
		List<BhavcopyRow> rows = source.stream().limit(capped).toList();
		return ResponseStructure.ok(new BhavcopyPreview(ex.name(), day, all.size(), equities.size(), rows),
				"Bhavcopy preview (not saved)");
	}
}
