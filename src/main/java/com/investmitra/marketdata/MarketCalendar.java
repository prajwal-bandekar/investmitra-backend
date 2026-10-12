package com.investmitra.marketdata;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Component;

/**
 * Indian market dates. Exchange holidays are not modelled: on a holiday the app makes at most
 * one extra call, because a price fetched today always counts as fresh.
 */
@Component
public class MarketCalendar {

	public static final ZoneId IST = ZoneId.of("Asia/Kolkata");

	/** BSE closes at 15:30; allow time for the end-of-day price to be published. */
	private static final LocalTime EOD_AVAILABLE = LocalTime.of(16, 0);

	private final Clock clock;

	public MarketCalendar(Clock clock) {
		this.clock = clock;
	}

	public LocalDate today() {
		return LocalDate.now(clock.withZone(IST));
	}

	/** Weekdays only; exchange holidays are not modelled. */
	public boolean isTradingDay(LocalDate day) {
		return !isWeekend(day);
	}

	public Instant startOfToday() {
		return today().atStartOfDay(IST).toInstant();
	}

	/** Most recent trading day whose closing price should already be available. */
	public LocalDate lastCompletedTradingDay() {
		ZonedDateTime now = ZonedDateTime.now(clock.withZone(IST));
		LocalDate day = now.toLocalTime().isBefore(EOD_AVAILABLE) ? now.toLocalDate().minusDays(1) : now.toLocalDate();
		while (isWeekend(day)) {
			day = day.minusDays(1);
		}
		return day;
	}

	/**
	 * A stored price is fresh, and needs no API call, when it already covers the last completed
	 * trading day or was fetched earlier today.
	 */
	public boolean isFresh(LocalDate tradeDate, Instant fetchedAt) {
		return !tradeDate.isBefore(lastCompletedTradingDay()) || !fetchedAt.isBefore(startOfToday());
	}

	private static boolean isWeekend(LocalDate day) {
		return day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY;
	}
}
