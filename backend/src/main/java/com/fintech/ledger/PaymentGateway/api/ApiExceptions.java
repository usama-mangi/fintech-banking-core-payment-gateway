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

	/** Malformed request content that Bean Validation cannot express; handler maps to 400. */
	public static class BadRequestException extends RuntimeException {
		public BadRequestException(String message) {
			super(message);
		}
	}

	/** Unique constraint violated (e.g. merchant email); handler maps to 409. */
	public static class DuplicateEmailException extends RuntimeException {
		public DuplicateEmailException(String message) {
			super(message);
		}
	}

	/** Illegal state transition or conflicting request state; handler maps to 409. */
	public static class ConflictException extends RuntimeException {
		public ConflictException(String message) {
			super(message);
		}
	}
}
