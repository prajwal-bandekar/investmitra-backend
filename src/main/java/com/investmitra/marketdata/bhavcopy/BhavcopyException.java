package com.investmitra.marketdata.bhavcopy;

/** The bhavcopy could not be downloaded or read. */
public class BhavcopyException extends RuntimeException {

	public BhavcopyException(String message) {
		super(message);
	}

	public BhavcopyException(String message, Throwable cause) {
		super(message, cause);
	}

	/** No file for that date, usually a weekend, holiday, or a file not posted yet. */
	public static class NotPublished extends BhavcopyException {
		public NotPublished(String message) {
			super(message);
		}
	}
}
