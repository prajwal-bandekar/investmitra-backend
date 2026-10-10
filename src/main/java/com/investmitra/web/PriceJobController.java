package com.investmitra.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.investmitra.config.AlphaVantageProperties;
import com.investmitra.entity.FetchTrigger;
import com.investmitra.marketdata.MarketDataService;
import com.investmitra.repository.JobRunRepository;
import com.investmitra.web.dto.Dtos.JobRunView;
import com.investmitra.web.dto.Dtos.QuotaView;
import com.investmitra.web.dto.ResponseStructure;

/** Admin endpoints for the daily price job. Unprotected until login is added. */
@RestController
@RequestMapping("/api/admin/price-job")
public class PriceJobController {

	private final MarketDataService marketData;
	private final JobRunRepository jobRuns;
	private final AlphaVantageProperties props;

	public PriceJobController(MarketDataService marketData, JobRunRepository jobRuns, AlphaVantageProperties props) {
		this.marketData = marketData;
		this.jobRuns = jobRuns;
		this.props = props;
	}

	/** Runs the job now. Only stocks without a fresh price are fetched, so re-running is cheap. */
	@PostMapping("/run")
	public ResponseStructure<JobRunView> run() {
		return ResponseStructure.ok(JobRunView.of(marketData.refreshTrackedStocks(FetchTrigger.MANUAL)), "Job finished");
	}

	@GetMapping("/runs")
	public ResponseStructure<List<JobRunView>> runs() {
		return ResponseStructure.ok(jobRuns.findTop20ByOrderByStartedAtDesc().stream().map(JobRunView::of).toList(),
				"Recent runs");
	}

	@GetMapping("/quota")
	public ResponseStructure<QuotaView> quota() {
		return ResponseStructure.ok(new QuotaView(props.dailyBudget(), marketData.remainingCallsToday()),
				"Alpha Vantage quota");
	}
}
