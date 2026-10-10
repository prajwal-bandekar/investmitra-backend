package com.investmitra.web;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.investmitra.service.WatchlistService;
import com.investmitra.web.dto.Dtos.WatchlistRequest;
import com.investmitra.web.dto.Dtos.WatchlistView;
import com.investmitra.web.dto.ResponseStructure;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/watchlist")
public class WatchlistController {

	private final WatchlistService watchlist;

	public WatchlistController(WatchlistService watchlist) {
		this.watchlist = watchlist;
	}

	@GetMapping
	public ResponseStructure<List<WatchlistView>> list(@RequestHeader("X-User-Id") Long userId) {
		return ResponseStructure.ok(watchlist.list(userId), "Watchlist");
	}

	@PostMapping
	public ResponseStructure<WatchlistView> add(@RequestHeader("X-User-Id") Long userId,
			@Valid @RequestBody WatchlistRequest request) {
		return ResponseStructure.ok(watchlist.add(userId, request), "Added to watchlist");
	}

	@DeleteMapping("/{symbol}")
	public ResponseStructure<Void> remove(@RequestHeader("X-User-Id") Long userId, @PathVariable String symbol) {
		watchlist.remove(userId, symbol);
		return ResponseStructure.ok(null, "Removed from watchlist");
	}
}
