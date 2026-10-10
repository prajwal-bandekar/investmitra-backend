package com.investmitra.web.dto;

/** Envelope used by every endpoint. */
public record ResponseStructure<T>(T data, String message, int statusCode) {

	public static <T> ResponseStructure<T> ok(T data, String message) {
		return new ResponseStructure<>(data, message, 200);
	}

	public static <T> ResponseStructure<T> created(T data, String message) {
		return new ResponseStructure<>(data, message, 201);
	}
}
