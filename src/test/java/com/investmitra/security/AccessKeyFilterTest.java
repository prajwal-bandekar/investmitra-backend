package com.investmitra.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.investmitra.web.HealthController;

class AccessKeyFilterTest {

	private static final String[] ORIGINS = { "http://localhost:3000" };

	@RestController
	static class Probe {
		@GetMapping("/api/stocks")
		String stocks() {
			return "ok";
		}

		@GetMapping("/api/admin/ping")
		String admin() {
			return "ok";
		}
	}

	private MockMvc mvc(String accessKey, String adminKey) {
		return MockMvcBuilders.standaloneSetup(new HealthController(), new Probe())
				.addFilters(new AccessKeyFilter(accessKey, adminKey, ORIGINS)).build();
	}

	@Test
	void blankKeysChangeNothing() throws Exception {
		MockMvc mvc = mvc("", "");
		mvc.perform(get("/api/stocks")).andExpect(status().isOk());
		mvc.perform(get("/api/admin/ping")).andExpect(status().isOk());
	}

	@Test
	void missingOrWrongAccessKeyIsRejected() throws Exception {
		MockMvc mvc = mvc("secret", "");
		mvc.perform(get("/api/stocks")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Access key required"))
				.andExpect(jsonPath("$.statusCode").value(401));
		mvc.perform(get("/api/stocks").header("X-Access-Key", "wrong")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/stocks").header("X-Access-Key", "secret")).andExpect(status().isOk());
	}

	@Test
	void healthAndPreflightAreOpen() throws Exception {
		MockMvc mvc = mvc("secret", "adm");
		mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
		// Preflight must reach the CORS handling untouched
		org.springframework.mock.web.MockHttpServletRequest req = new org.springframework.mock.web.MockHttpServletRequest(
				"OPTIONS", "/api/stocks");
		org.springframework.mock.web.MockHttpServletResponse res = new org.springframework.mock.web.MockHttpServletResponse();
		org.springframework.mock.web.MockFilterChain chain = new org.springframework.mock.web.MockFilterChain();
		new AccessKeyFilter("secret", "adm", ORIGINS).doFilter(req, res, chain);
		org.junit.jupiter.api.Assertions.assertEquals(200, res.getStatus());
		org.junit.jupiter.api.Assertions.assertNotNull(chain.getRequest());
	}

	@Test
	void adminPathsNeedAdminKey() throws Exception {
		MockMvc mvc = mvc("secret", "adm");
		mvc.perform(get("/api/admin/ping").header("X-Access-Key", "secret")).andExpect(status().isForbidden());
		mvc.perform(get("/api/admin/ping").header("X-Access-Key", "secret").header("X-Admin-Key", "bad"))
				.andExpect(status().isForbidden());
		mvc.perform(get("/api/admin/ping").header("X-Access-Key", "secret").header("X-Admin-Key", "adm"))
				.andExpect(status().isOk());
		mvc.perform(get("/api/admin/ping").header("X-Admin-Key", "adm")).andExpect(status().isUnauthorized());
		// Non-admin paths do not need the admin key
		mvc.perform(get("/api/stocks").header("X-Access-Key", "secret")).andExpect(status().isOk());
	}

	@Test
	void rejectionCarriesCorsHeaderForAllowedOrigin() throws Exception {
		mvc("secret", "").perform(get("/api/stocks").header("Origin", "http://localhost:3000"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
		mvc("secret", "").perform(get("/api/stocks").header("Origin", "http://evil.example"))
				.andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}
}
