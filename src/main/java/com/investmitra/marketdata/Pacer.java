package com.investmitra.marketdata;

import java.time.Duration;

/** Waits between API calls. Swapped for a no-op in tests. */
@FunctionalInterface
public interface Pacer {

	void pause(Duration duration) throws InterruptedException;
}
