package com.fintech.ledger.PaymentGateway.domain;

/**
 * Lifecycle status of a payment.
 */
public enum PaymentStatus {
	REQUIRES_PAYMENT, AUTHORIZED, CAPTURED, FAILED, REFUNDED, CANCELLED
}
