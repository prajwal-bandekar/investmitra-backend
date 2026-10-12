package com.investmitra.web;

import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.investmitra.service.ApiException;
import com.investmitra.web.dto.ResponseStructure;

@RestControllerAdvice
public class ApiExceptionHandler {

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ResponseStructure<Void>> handle(ApiException e) {
		return error(e.getStatus(), e.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ResponseStructure<Void>> handle(MethodArgumentNotValidException e) {
		String message = e.getBindingResult().getFieldErrors().stream()
				.map(f -> f.getField() + " " + f.getDefaultMessage())
				.collect(Collectors.joining(", "));
		return error(HttpStatus.BAD_REQUEST, message);
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	public ResponseEntity<ResponseStructure<Void>> handle(MissingRequestHeaderException e) {
		return error(HttpStatus.UNAUTHORIZED, "Missing header " + e.getHeaderName());
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ResponseStructure<Void>> handle(HttpMessageNotReadableException e) {
		return error(HttpStatus.BAD_REQUEST, "Malformed request body");
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ResponseStructure<Void>> handle(MethodArgumentTypeMismatchException e) {
		return error(HttpStatus.BAD_REQUEST, "Invalid value for " + e.getName());
	}

	/** Failed @Size etc. on a request parameter. */
	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ResponseStructure<Void>> handle(HandlerMethodValidationException e) {
		return error(HttpStatus.BAD_REQUEST, "Invalid request parameter");
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ResponseStructure<Void>> handle(DataIntegrityViolationException e) {
		log.warn("Data integrity violation", e);
		if (isUniqueViolation(e)) {
			return error(HttpStatus.CONFLICT, "Conflicting request; please retry");
		}
		return error(HttpStatus.BAD_REQUEST, "Invalid data");
	}

	/** Duplicate key: SQLState 23505 (H2, standard) or 23000 with MySQL's 1062 / "Duplicate entry". */
	static boolean isUniqueViolation(Throwable e) {
		for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
			if (t instanceof java.sql.SQLException sql) {
				String state = sql.getSQLState();
				if ("23505".equals(state)) {
					return true;
				}
				if ("23000".equals(state) && (sql.getErrorCode() == 1062
						|| (sql.getMessage() != null && sql.getMessage().contains("Duplicate")))) {
					return true;
				}
			}
		}
		return false;
	}

	/** Last resort: log the cause, tell the client nothing about internals. */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ResponseStructure<Void>> handleUnexpected(Exception e) {
		if (e instanceof org.springframework.web.ErrorResponse er && er.getStatusCode().is4xxClientError()) {
			// Spring MVC's own client errors (405, 415, missing parameter...) keep their status.
			HttpStatus status = HttpStatus.valueOf(er.getStatusCode().value());
			String detail = er.getBody().getDetail();
			ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
			builder.headers(er.getHeaders()); // e.g. Allow on 405
			return builder.body(new ResponseStructure<>(null, detail == null || detail.isBlank()
					? status.getReasonPhrase() : detail, status.value()));
		}
		log.error("Unhandled exception", e);
		return error(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again later.");
	}

	private static ResponseEntity<ResponseStructure<Void>> error(HttpStatus status, String message) {
		return ResponseEntity.status(status).body(new ResponseStructure<>(null, message, status.value()));
	}
}
