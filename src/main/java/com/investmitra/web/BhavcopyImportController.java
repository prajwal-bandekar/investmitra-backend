package com.investmitra.web;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.investmitra.entity.FetchTrigger;
import com.investmitra.marketdata.MarketCalendar;
import com.investmitra.marketdata.bhavcopy.BhavcopyImporter;
import com.investmitra.marketdata.bhavcopy.BhavcopyImporter.ImportResult;
import com.investmitra.service.ApiException;
import com.investmitra.web.dto.ResponseStructure;

/** Admin endpoint to import (or backfill) a day's bhavcopy. Unprotected until login is added. */
@RestController
@RequestMapping("/api/admin/bhavcopy")
public class BhavcopyImportController {

	private final BhavcopyImporter importer;
	private final MarketCalendar calendar;

	public BhavcopyImportController(BhavcopyImporter importer, MarketCalendar calendar) {
		this.importer = importer;
		this.calendar = calendar;
	}

	/** Imports the given date (default: last completed trading day). Re-running a date updates, never duplicates. */
	@PostMapping("/run")
	public ResponseStructure<ImportResult> run(@RequestParam(required = false) String date) {
		LocalDate day;
		try {
			day = date == null || date.isBlank() ? calendar.lastCompletedTradingDay() : LocalDate.parse(date);
		} catch (DateTimeParseException e) {
			throw ApiException.badRequest("Date must look like 2026-10-09");
		}
		if (day.isAfter(calendar.today())) {
			throw ApiException.badRequest("Date is in the future");
		}
		return ResponseStructure.ok(importer.importDay(day, FetchTrigger.MANUAL), "Bhavcopy import finished");
	}
}
