package com.investmitra.web;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.investmitra.service.ApiException;
import com.investmitra.web.dto.ResponseStructure;

@RestControllerAdvice
public class ApiExceptionHandler {

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

	private static ResponseEntity<ResponseStructure<Void>> error(HttpStatus status, String message) {
		return ResponseEntity.status(status).body(new ResponseStructure<>(null, message, status.value()));
	}
}
