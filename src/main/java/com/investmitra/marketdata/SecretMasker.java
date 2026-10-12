package com.investmitra.marketdata;

import java.util.regex.Pattern;

/** Hides the Alpha Vantage API key in text that may be stored, logged or shown to users. */
public final class SecretMasker {

	public static final String MASK = "****";

	private static final Pattern KEY_PHRASE = Pattern.compile("(?i)API key as\\s+\\S+");
	private static final Pattern KEY_PARAM = Pattern.compile("(?i)apikey=[^\\s&\"']+");

	private static volatile String configuredKey;

	private SecretMasker() {
	}

	/** Remembers the configured key so its exact value is masked wherever it appears. */
	public static void register(String key) {
		// Very short values would mask ordinary words, so only real-looking keys are matched literally.
		configuredKey = key == null || key.trim().length() < 8 ? null : key.trim();
	}

	public static String mask(String text) {
		if (text == null || text.isEmpty()) {
			return text;
		}
		String out = text;
		String key = configuredKey;
		if (key != null) {
			out = out.replace(key, MASK);
		}
		out = KEY_PHRASE.matcher(out).replaceAll("API key as " + MASK);
		return KEY_PARAM.matcher(out).replaceAll("apikey=" + MASK);
	}
}
