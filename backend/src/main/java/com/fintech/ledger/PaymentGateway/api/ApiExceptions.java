package com.fintech.ledger.PaymentGateway.api;

/**
 * Runtime exceptions carrying the HTTP status the error handler should map
 * them to.
 */
public final class ApiExceptions {

	private ApiExceptions() {
	}

	/** Resource does not exist; handler maps to 404. */
	public static class NotFoundException extends RuntimeException {
		public NotFoundException(String message) {
			super(message);
		}
	}

	/** Unique constraint violated (e.g. merchant email); handler maps to 409. */
	public static class DuplicateEmailException extends RuntimeException {
		public DuplicateEmailException(String message) {
			super(message);
		}
	}
}
