package com.fintech.ledger.PaymentGateway.domain;

import java.math.BigDecimal;

/**
 * Currency of an amount, stored as ISO 4217 codes.
 */
public enum Currency {
	USD(BigDecimal.ONE),
	EUR(BigDecimal.ONE),
	GBP(BigDecimal.ONE),
	PKR(new BigDecimal("0.0035"));

	private final BigDecimal usdRate;

	Currency(BigDecimal usdRate) {
		this.usdRate = usdRate;
	}

	/**
	 * Indicative conversion rate of this currency into USD.
	 */
	public BigDecimal getUsdRate() {
		return usdRate;
	}
}
