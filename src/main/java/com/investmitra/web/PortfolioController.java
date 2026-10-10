package com.investmitra.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.investmitra.service.PortfolioService;
import com.investmitra.service.TradingService;
import com.investmitra.web.dto.Dtos.CreatePortfolioRequest;
import com.investmitra.web.dto.Dtos.PortfolioDetail;
import com.investmitra.web.dto.Dtos.PortfolioSummary;
import com.investmitra.web.dto.Dtos.TradeRequest;
import com.investmitra.web.dto.Dtos.TradeResult;
import com.investmitra.web.dto.Dtos.TradeView;
import com.investmitra.web.dto.ResponseStructure;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/portfolios")
public class PortfolioController {

	private final PortfolioService portfolios;
	private final TradingService trading;

	public PortfolioController(PortfolioService portfolios, TradingService trading) {
		this.portfolios = portfolios;
		this.trading = trading;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ResponseStructure<PortfolioSummary> create(@RequestHeader("X-User-Id") Long userId,
			@Valid @RequestBody CreatePortfolioRequest request) {
		return ResponseStructure.created(portfolios.create(userId, request), "Portfolio created");
	}

	@GetMapping
	public ResponseStructure<List<PortfolioSummary>> list(@RequestHeader("X-User-Id") Long userId) {
		return ResponseStructure.ok(portfolios.listManagedBy(userId), "Portfolios");
	}

	@GetMapping("/{id}")
	public ResponseStructure<PortfolioDetail> detail(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
		return ResponseStructure.ok(portfolios.detail(userId, id), "Portfolio");
	}

	@PostMapping("/{id}/trades")
	@ResponseStatus(HttpStatus.CREATED)
	public ResponseStructure<TradeResult> trade(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id,
			@Valid @RequestBody TradeRequest request) {
		TradeResult result = trading.trade(userId, id, request);
		return ResponseStructure.created(result, request.side() + " executed");
	}

	@GetMapping("/{id}/trades")
	public ResponseStructure<List<TradeView>> trades(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
		return ResponseStructure.ok(portfolios.tradeHistory(userId, id), "Trades");
	}
}
