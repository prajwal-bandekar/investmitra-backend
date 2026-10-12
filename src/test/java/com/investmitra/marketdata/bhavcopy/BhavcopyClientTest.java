package com.investmitra.marketdata.bhavcopy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.Test;

class BhavcopyClientTest {

	@Test
	void readsBodiesUpToTheCap() throws Exception {
		assertThat(BhavcopyClient.readCapped(new ByteArrayInputStream(new byte[100]), -1, 100, "NSE")).hasSize(100);
	}

	@Test
	void rejectsBodiesOverTheCapByStreamOrByContentLength() {
		assertThatThrownBy(() -> BhavcopyClient.readCapped(new ByteArrayInputStream(new byte[101]), -1, 100, "NSE"))
				.isInstanceOf(BhavcopyException.class).hasMessageContaining("too large");
		assertThatThrownBy(() -> BhavcopyClient.readCapped(new ByteArrayInputStream(new byte[1]), 500, 100, "BSE"))
				.isInstanceOf(BhavcopyException.class).hasMessageContaining("too large");
		assertThat(BhavcopyClient.MAX_DOWNLOAD_BYTES).isEqualTo(20L * 1024 * 1024);
	}
}
