package com.investmitra.config;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.investmitra.marketdata.Pacer;

@Configuration
public class AppConfig {

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	public Pacer pacer() {
		return duration -> Thread.sleep(duration.toMillis());
	}

	/** Lets the React dev server call the API. */
	@Bean
	public WebMvcConfigurer corsConfigurer(@Value("${app.cors.allowed-origins}") String[] origins) {
		return new WebMvcConfigurer() {
			@Override
			public void addCorsMappings(CorsRegistry registry) {
				registry.addMapping("/api/**").allowedOrigins(origins).allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
						.allowedHeaders("Content-Type", "Accept", "X-User-Id", "X-Access-Key", "X-Admin-Key");
			}
		};
	}
}
