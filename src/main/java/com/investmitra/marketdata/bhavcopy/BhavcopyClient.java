package com.investmitra.marketdata.bhavcopy;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Downloads the daily bhavcopy (every stock's end-of-day prices) from NSE or BSE.
 * One request per exchange per day, no API key. Both sites reject requests that don't look like a browser.
 */
@Component
public class BhavcopyClient {

	public enum Exchange {
		BSE("https://www.bseindia.com/download/BhavCopy/Equity/BhavCopy_BSE_CM_0_0_0_%s_F_0000.CSV",
				"https://www.bseindia.com/"),
		NSE("https://nsearchives.nseindia.com/content/cm/BhavCopy_NSE_CM_0_0_0_%s_F_0000.csv.zip",
				"https://www.nseindia.com/");

		private final String urlPattern;
		private final String referer;

		Exchange(String urlPattern, String referer) {
			this.urlPattern = urlPattern;
			this.referer = referer;
		}

		public String urlFor(LocalDate date) {
			return urlPattern.formatted(date.format(DateTimeFormatter.BASIC_ISO_DATE));
		}
	}

	private static final String USER_AGENT =
			"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Safari/537.36";

	/** A real bhavcopy is a few MB; anything above this is rejected rather than held in memory. */
	static final long MAX_DOWNLOAD_BYTES = 20L * 1024 * 1024;

	private final RestClient restClient;

	public BhavcopyClient(RestClient.Builder builder) {
		this.restClient = builder.build();
	}

	/** Raw file bytes as served (CSV for BSE, ZIP for NSE). */
	public byte[] download(Exchange exchange, LocalDate date) {
		String url = exchange.urlFor(date);
		byte[] body;
		try {
			body = restClient.get()
					.uri(url)
					.header(HttpHeaders.USER_AGENT, USER_AGENT)
					.header(HttpHeaders.REFERER, exchange.referer)
					.header(HttpHeaders.ACCEPT, "*/*")
					.exchange((request, response) -> {
						if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
							throw new BhavcopyException.NotPublished(exchange + " has no bhavcopy for " + date
									+ " (holiday, weekend, or not posted yet)");
						}
						if (!response.getStatusCode().is2xxSuccessful()) {
							throw new BhavcopyException(exchange + " refused the download: " + response.getStatusCode());
						}
						return readCapped(response.getBody(), response.getHeaders().getContentLength(), MAX_DOWNLOAD_BYTES,
								exchange.name());
					});
		} catch (RestClientException e) {
			throw new BhavcopyException(exchange + " download failed: " + e.getMessage(), e);
		}
		if (body == null || body.length == 0) {
			throw new BhavcopyException.NotPublished(exchange + " returned an empty file for " + date);
		}
		// On holidays BSE answers 200 with an HTML error page instead of a 404.
		if (!looksLikeBhavcopy(body)) {
			throw new BhavcopyException.NotPublished(exchange + " has no bhavcopy for " + date + " (holiday, weekend, or not posted yet)");
		}
		return body;
	}

	/** Reads the body, rejecting it once it exceeds {@code max} bytes (by Content-Length or while reading). */
	static byte[] readCapped(java.io.InputStream in, long contentLength, long max, String exchange) throws java.io.IOException {
		if (contentLength > max) {
			throw new BhavcopyException(exchange + " file is too large (" + contentLength + " bytes, limit " + max + ")");
		}
		try (in) {
			byte[] data = in.readNBytes((int) max + 1);
			if (data.length > max) {
				throw new BhavcopyException(exchange + " file is too large (limit " + max + " bytes)");
			}
			return data;
		}
	}

	/** A ZIP (starts "PK") or a CSV whose header starts with TradDt, ignoring a UTF-8 BOM. */
	static boolean looksLikeBhavcopy(byte[] body) {
		if (body.length > 1 && body[0] == 'P' && body[1] == 'K') {
			return true;
		}
		String head = new String(body, 0, Math.min(body.length, 16), java.nio.charset.StandardCharsets.UTF_8);
		return head.replace("﻿", "").startsWith("TradDt");
	}

	/** Downloads, unzips if needed and parses every row for the day. */
	public List<BhavcopyRow> fetch(Exchange exchange, LocalDate date) {
		return BhavcopyParser.parse(BhavcopyParser.unzipIfNeeded(download(exchange, date)));
	}
}
