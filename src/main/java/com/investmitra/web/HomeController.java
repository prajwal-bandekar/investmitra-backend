package com.investmitra.web;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The backend serves only /api; opening it in a browser forwards to the React site. */
@RestController
public class HomeController {

	private final URI frontend;

	public HomeController(@Value("${app.frontend-url}") String frontendUrl) {
		this.frontend = URI.create(frontendUrl);
	}

	@GetMapping("/")
	public ResponseEntity<Void> home() {
		return ResponseEntity.status(HttpStatus.FOUND).location(frontend).build();
	}
}
