package com.investmitra.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.investmitra.entity.JobRun;
import com.investmitra.entity.JobStatus;
import com.investmitra.web.dto.Dtos.JobRunView;

class SecretMaskerTest {

	private static final String KEY = "TESTKEY12345678";

	@AfterEach
	void reset() {
		SecretMasker.register(null);
	}

	@Test
	void masksAlphaVantageSentenceParamAndConfiguredValue() {
		SecretMasker.register(KEY);
		String text = "We have detected your API key as ABCD1234XYZ and our standard API rate limit is 25 requests per day. "
				+ "GET /query?function=X&apikey=SECRET99&symbol=A raw " + KEY;
		String out = SecretMasker.mask(text);
		assertThat(out).doesNotContain("ABCD1234XYZ", "SECRET99", KEY);
		assertThat(out).contains("API key as ****", "apikey=****&symbol=A");
	}

	@Test
	void nullAndPlainTextPassThrough() {
		assertThat(SecretMasker.mask(null)).isNull();
		assertThat(SecretMasker.mask("no secrets here")).isEqualTo("no secrets here");
	}

	@Test
	void exceptionMessageIsMasked() {
		var e = new AlphaVantageException.RateLimited("We have detected your API key as LEAKED123 and more");
		assertThat(e.getMessage()).doesNotContain("LEAKED123");
	}

	@Test
	void jobRunViewMasksAlreadyStoredNotes() {
		JobRun r = new JobRun();
		r.setStatus(JobStatus.PARTIAL);
		r.setNotes("Rate limited: We have detected your API key as OLDROWKEY1 and ...");
		assertThat(JobRunView.of(r).notes()).doesNotContain("OLDROWKEY1").contains("****");
	}
}
