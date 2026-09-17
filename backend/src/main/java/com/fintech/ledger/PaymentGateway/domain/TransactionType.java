package com.fintech.ledger.PaymentGateway.domain;

import java.math.BigDecimal;

/**
 * Type of a transaction against a payment.
 */
public enum TransactionType {
	AUTHORIZATION(BigDecimal.ZERO),
	CAPTURE(BigDecimal.ONE),
	REFUND(new BigDecimal("-1")),
	CHARGEBACK(new BigDecimal("-1")),
	FEE(BigDecimal.ZERO);

	private final BigDecimal sign;

	TransactionType(BigDecimal sign) {
		this.sign = sign;
	}

	TransactionType() {
		this.sign = BigDecimal.ONE;
	}

	/**
	 * Sign applied to the transaction amount when computing the net captured
	 * amount of a payment (+1 for inflows, -1 for outflows, 0 for entries
	 * that do not affect the captured total, such as authorizations and fees).
	 */
	public BigDecimal getSign() {
		return sign;
	}
}
