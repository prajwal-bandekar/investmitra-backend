package com.investmitra.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

class MarketCalendarTest {

	private static MarketCalendar at(String istDateTime) {
		Instant now = ZonedDateTime.parse(istDateTime + "+05:30[Asia/Kolkata]").toInstant();
		return new MarketCalendar(Clock.fixed(now, MarketCalendar.IST));
	}

	@Test
	void beforeCloseTheLastCompletedDayIsYesterday() {
		// Wednesday 07:00 IST, when the daily job runs
		assertThat(at("2026-10-07T07:00").lastCompletedTradingDay()).isEqualTo(LocalDate.of(2026, 10, 6));
	}

	@Test
	void afterCloseTheLastCompletedDayIsToday() {
		assertThat(at("2026-10-07T17:00").lastCompletedTradingDay()).isEqualTo(LocalDate.of(2026, 10, 7));
	}

	@Test
	void mondayMorningAndWeekendsPointToFriday() {
		LocalDate friday = LocalDate.of(2026, 10, 9);
		assertThat(at("2026-10-12T07:00").lastCompletedTradingDay()).isEqualTo(friday);
		assertThat(at("2026-10-10T12:00").lastCompletedTradingDay()).isEqualTo(friday);
		assertThat(at("2026-10-11T20:00").lastCompletedTradingDay()).isEqualTo(friday);
	}

	@Test
	void fridayPriceStaysFreshAllWeekendSoNoCallsAreWasted() {
		MarketCalendar sunday = at("2026-10-11T10:00");
		Instant fetchedFriday = ZonedDateTime.parse("2026-10-09T18:00+05:30[Asia/Kolkata]").toInstant();
		assertThat(sunday.isFresh(LocalDate.of(2026, 10, 9), fetchedFriday)).isTrue();
	}

	@Test
	void olderPriceIsStaleUnlessFetchedToday() {
		MarketCalendar wednesday = at("2026-10-07T10:00");
		Instant lastWeek = ZonedDateTime.parse("2026-10-01T18:00+05:30[Asia/Kolkata]").toInstant();
		Instant earlierToday = ZonedDateTime.parse("2026-10-07T07:00+05:30[Asia/Kolkata]").toInstant();

		assertThat(wednesday.isFresh(LocalDate.of(2026, 10, 1), lastWeek)).isFalse();
		// e.g. an exchange holiday: Alpha Vantage returned an older day, but we already asked today
		assertThat(wednesday.isFresh(LocalDate.of(2026, 10, 5), earlierToday)).isTrue();
	}
}
