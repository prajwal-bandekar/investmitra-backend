package com.investmitra.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.investmitra.marketdata.bhavcopy.BhavcopyClient;
import com.investmitra.marketdata.bhavcopy.BhavcopyClient.Exchange;

class BhavcopyControllerTest {

	@Test
	void previewLivesUnderAdminPath() throws Exception {
		BhavcopyClient client = mock(BhavcopyClient.class);
		when(client.fetch(Exchange.NSE, LocalDate.of(2026, 10, 9))).thenReturn(List.of());
		MockMvc mvc = MockMvcBuilders.standaloneSetup(new BhavcopyController(client))
				.setControllerAdvice(new ApiExceptionHandler()).build();

		mvc.perform(get("/api/admin/bhavcopy/preview/nse/2026-10-09")).andExpect(status().isOk());
		mvc.perform(get("/api/bhavcopy/nse/2026-10-09")).andExpect(status().isNotFound());
	}
}
