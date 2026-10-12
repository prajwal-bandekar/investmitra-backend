package com.investmitra.marketdata;

/** Alpha Vantage replied with something other than a usable quote. */
public class AlphaVantageException extends RuntimeException {

	public AlphaVantageException(String message) {
		super(SecretMasker.mask(message));
	}

	public AlphaVantageException(String message, Throwable cause) {
		super(SecretMasker.mask(message), cause);
	}

	/** Daily or per-second limit hit. Further calls today are pointless. */
	public static class RateLimited extends AlphaVantageException {
		public RateLimited(String message) {
			super(message);
		}
	}

	/** The symbol is unknown to Alpha Vantage. */
	public static class SymbolNotFound extends AlphaVantageException {
		public SymbolNotFound(String message) {
			super(message);
		}
	}
}
