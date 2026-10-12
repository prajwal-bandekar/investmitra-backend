package com.investmitra.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.investmitra.web.dto.Dtos.TradeRequest;

class ApiExceptionHandlerTest {

	@RestController
	static class Stub {
		@GetMapping("/boom")
		String boom() {
			throw new IllegalStateException("secret internal detail: jdbc:mysql://host");
		}

		@GetMapping("/num/{n}")
		String num(@PathVariable Long n) {
			return "ok";
		}

		@GetMapping("/dup")
		String dup() {
			throw new org.springframework.dao.DataIntegrityViolationException("x",
					new java.sql.SQLException("Unique index violation", "23505"));
		}

		@GetMapping("/mysqldup")
		String mysqlDup() {
			throw new org.springframework.dao.DataIntegrityViolationException("x",
					new RuntimeException(new java.sql.SQLException("Duplicate entry a for key k", "23000", 1062)));
		}

		@GetMapping("/toolong")
		String tooLong() {
			throw new org.springframework.dao.DataIntegrityViolationException("x",
					new java.sql.SQLException("Data too long for column", "22001", 1406));
		}

		@PostMapping("/trade")
		String trade(@RequestBody TradeRequest r) {
			return "ok";
		}
	}

	private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Stub())
			.setControllerAdvice(new ApiExceptionHandler()).build();

	@Test
	void unexpectedErrorsBecomeAGeneric500WithoutLeakingDetails() throws Exception {
		mvc.perform(get("/boom")).andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.message").value("Something went wrong. Please try again later."));
	}

	@Test
	void wrongTypedPathVariableIs400() throws Exception {
		mvc.perform(get("/num/abc")).andExpect(status().isBadRequest());
	}

	@Test
	void malformedOrOverflowingBodyIs400() throws Exception {
		mvc.perform(post("/trade").contentType(MediaType.APPLICATION_JSON).content("{not json"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/trade").contentType(MediaType.APPLICATION_JSON)
				.content("{\"symbol\":\"TCS\",\"side\":\"BUY\",\"quantity\":99999999999999999999999}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void springClientErrorsKeepTheirStatusDetailAndHeaders() throws Exception {
		mvc.perform(post("/num/1")).andExpect(status().isMethodNotAllowed())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Allow",
						org.hamcrest.Matchers.containsString("GET")))
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("POST")));
	}

	@Test
	void onlyUniqueViolationsAreConflicts() throws Exception {
		mvc.perform(get("/dup")).andExpect(status().isConflict());
		mvc.perform(get("/mysqldup")).andExpect(status().isConflict());
		mvc.perform(get("/toolong")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Invalid data"));
	}
}
