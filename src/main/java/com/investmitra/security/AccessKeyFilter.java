package com.investmitra.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Simple shared-secret gate used until real login exists. When {@code app.access-key} is set, every
 * {@code /api/**} request except {@code /api/health} and CORS preflight must send it in {@code X-Access-Key}.
 * When {@code app.admin-key} is set, {@code /api/admin/**} additionally needs {@code X-Admin-Key}.
 * Blank properties (local dev, tests) disable the corresponding check.
 */
@Component
public class AccessKeyFilter extends OncePerRequestFilter {

	public static final String ACCESS_HEADER = "X-Access-Key";
	public static final String ADMIN_HEADER = "X-Admin-Key";

	private final String accessKey;
	private final String adminKey;
	private final List<String> allowedOrigins;

	public AccessKeyFilter(@Value("${app.access-key:}") String accessKey,
			@Value("${app.admin-key:}") String adminKey,
			@Value("${app.cors.allowed-origins:}") String[] allowedOrigins) {
		this.accessKey = accessKey == null ? "" : accessKey;
		this.adminKey = adminKey == null ? "" : adminKey;
		this.allowedOrigins = List.of(allowedOrigins);
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		return !path.startsWith("/api/") || path.equals("/api/health") || "OPTIONS".equalsIgnoreCase(request.getMethod());
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (!accessKey.isBlank() && !matches(accessKey, request.getHeader(ACCESS_HEADER))) {
			reject(request, response, 401, "Access key required");
			return;
		}
		if (!adminKey.isBlank() && isAdminPath(request.getRequestURI())
				&& !matches(adminKey, request.getHeader(ADMIN_HEADER))) {
			reject(request, response, 403, "Admin key required");
			return;
		}
		chain.doFilter(request, response);
	}

	private static boolean isAdminPath(String path) {
		return path.equals("/api/admin") || path.startsWith("/api/admin/");
	}

	/** Constant-time comparison (digests first so the length does not leak either). */
	private static boolean matches(String expected, String actual) {
		if (actual == null) {
			return false;
		}
		try {
			MessageDigest sha = MessageDigest.getInstance("SHA-256");
			byte[] a = sha.digest(expected.getBytes(StandardCharsets.UTF_8));
			byte[] b = sha.digest(actual.getBytes(StandardCharsets.UTF_8));
			return MessageDigest.isEqual(a, b);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private void reject(HttpServletRequest request, HttpServletResponse response, int status, String message)
			throws IOException {
		// This filter runs before Spring's CORS handling, so let the browser read the error.
		String origin = request.getHeader("Origin");
		if (origin != null && allowedOrigins.contains(origin)) {
			response.setHeader("Access-Control-Allow-Origin", origin);
			response.addHeader("Vary", "Origin");
		}
		response.setStatus(status);
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write("{\"data\":null,\"message\":\"" + message + "\",\"statusCode\":" + status + "}");
	}
}
