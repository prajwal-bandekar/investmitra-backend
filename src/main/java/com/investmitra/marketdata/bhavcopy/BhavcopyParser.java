package com.investmitra.marketdata.bhavcopy;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Static helpers for the common NSE/BSE bhavcopy CSV layout (TradDt, TckrSymb, ClsPric, ...).
 * Columns are looked up by header name so a reordered file still parses.
 */
public final class BhavcopyParser {

	private static final List<String> REQUIRED = List.of("TradDt", "Src", "FinInstrmTp", "TckrSymb", "ClsPric");

	private BhavcopyParser() {
	}

	/** Parsed rows plus how many malformed data lines were skipped. A List so callers can use it as one. */
	public static final class ParsedRows extends ArrayList<BhavcopyRow> {

		private static final long serialVersionUID = 1L;

		private final int skipped;

		public ParsedRows(int skipped) {
			this.skipped = skipped;
		}

		public int skipped() {
			return skipped;
		}
	}

	/** Skipped-row count of a parse result (0 for plain lists). */
	public static int skippedOf(List<BhavcopyRow> rows) {
		return rows instanceof ParsedRows p ? p.skipped() : 0;
	}

	/** Returns the CSV bytes, unzipping the first entry when the download is a ZIP. */
	public static byte[] unzipIfNeeded(byte[] body) {
		boolean zip = body.length > 3 && body[0] == 'P' && body[1] == 'K';
		if (!zip) {
			return body;
		}
		try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(body))) {
			ZipEntry entry = in.getNextEntry();
			if (entry == null) {
				throw new BhavcopyException("ZIP file is empty");
			}
			return in.readAllBytes();
		} catch (IOException e) {
			throw new BhavcopyException("Could not unzip bhavcopy: " + e.getMessage(), e);
		}
	}

	/** Parses every data line. Lines with no close price are skipped; malformed lines are skipped and counted. */
	public static List<BhavcopyRow> parse(byte[] csv) {
		return parse(new ByteArrayInputStream(csv));
	}

	public static List<BhavcopyRow> parse(InputStream csv) {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(csv, StandardCharsets.UTF_8))) {
			String header = reader.readLine();
			if (header == null) {
				throw new BhavcopyException("Bhavcopy is empty");
			}
			Map<String, Integer> col = indexHeader(header);
			List<BhavcopyRow> good = new ArrayList<>();
			int dataRows = 0;
			int bad = 0;
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				dataRows++;
				try {
					BhavcopyRow row = parseLine(line, col);
					if (row != null) {
						good.add(row);
					}
				} catch (NumberFormatException | DateTimeParseException | ArrayIndexOutOfBoundsException
						| NullPointerException e) {
					bad++;
				}
			}
			if (dataRows > 0 && bad * 2 > dataRows) {
				throw new BhavcopyException("Bhavcopy looks corrupt: " + bad + " of " + dataRows + " rows are unreadable");
			}
			ParsedRows rows = new ParsedRows(bad);
			rows.addAll(good);
			return rows;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Null when the line has no close price. Throws on malformed numbers or dates. */
	private static BhavcopyRow parseLine(String line, Map<String, Integer> col) {
		String[] f = line.split(",", -1);
		BigDecimal close = decimal(f, col, "ClsPric");
		if (close == null) {
			return null;
		}
		return new BhavcopyRow(
						LocalDate.parse(text(f, col, "TradDt")),
						text(f, col, "Src"),
						text(f, col, "FinInstrmTp"),
						text(f, col, "TckrSymb"),
						text(f, col, "SctySrs"),
						text(f, col, "ISIN"),
						text(f, col, "FinInstrmNm"),
						decimal(f, col, "OpnPric"),
						decimal(f, col, "HghPric"),
						decimal(f, col, "LwPric"),
						close,
						decimal(f, col, "PrvsClsgPric"),
						whole(f, col, "TtlTradgVol"));
	}

	/** Only plain equity shares. */
	public static List<BhavcopyRow> equities(List<BhavcopyRow> rows) {
		return rows.stream().filter(BhavcopyRow::isEquity).toList();
	}

	private static Map<String, Integer> indexHeader(String header) {
		String[] names = header.replace("﻿", "").split(",", -1);
		Map<String, Integer> col = new HashMap<>();
		for (int i = 0; i < names.length; i++) {
			col.put(names[i].trim(), i);
		}
		for (String needed : REQUIRED) {
			if (!col.containsKey(needed)) {
				throw new BhavcopyException("Unexpected bhavcopy format, missing column " + needed);
			}
		}
		return col;
	}

	private static String text(String[] f, Map<String, Integer> col, String name) {
		Integer i = col.get(name);
		if (i == null || i >= f.length) {
			return null;
		}
		String v = f[i].trim();
		return v.isEmpty() ? null : v;
	}

	private static BigDecimal decimal(String[] f, Map<String, Integer> col, String name) {
		String v = text(f, col, name);
		return v == null ? null : new BigDecimal(v);
	}

	private static Long whole(String[] f, Map<String, Integer> col, String name) {
		String v = text(f, col, name);
		return v == null ? null : new BigDecimal(v).longValue();
	}
}
