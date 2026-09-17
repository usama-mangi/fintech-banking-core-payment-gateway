package com.fintech.ledger.PaymentGateway.api;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Shapes every error as {status, error, message, timestamp} per
 * docs/system-design.md section 4.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
		String message = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.findFirst()
				.orElse("validation failed");
		return errorBody(HttpStatus.BAD_REQUEST, message);
	}

	@ExceptionHandler(ApiExceptions.NotFoundException.class)
	public ResponseEntity<Map<String, Object>> handleNotFound(ApiExceptions.NotFoundException ex) {
		return errorBody(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(ApiExceptions.DuplicateEmailException.class)
	public ResponseEntity<Map<String, Object>> handleDuplicate(ApiExceptions.DuplicateEmailException ex) {
		return errorBody(HttpStatus.CONFLICT, ex.getMessage());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
		log.error("Unhandled exception", ex);
		return errorBody(HttpStatus.INTERNAL_SERVER_ERROR, "unexpected error");
	}

	private ResponseEntity<Map<String, Object>> errorBody(HttpStatus status, String message) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("status", status.value());
		body.put("error", status.getReasonPhrase());
		body.put("message", message);
		body.put("timestamp", Instant.now().toString());
		return ResponseEntity.status(status).body(body);
	}
}
