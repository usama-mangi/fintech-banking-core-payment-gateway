package com.fintech.ledger.PaymentGateway.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/**
 * A ledger entry (authorization, capture, refund, chargeback or fee)
 * recorded against a payment.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "payment_id", nullable = false)
	private Payment payment;

	@NotNull
	@DecimalMin(value = "0.01")
	@Digits(integer = 12, fraction = 2)
	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal amount;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TransactionType type;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TransactionStatus status;

	@NotNull
	@Column(nullable = false)
	private Instant recordedAt;

	protected Transaction() {
		// Required by JPA
	}

	public Transaction(BigDecimal amount, TransactionType type, TransactionStatus status) {
		setAmount(amount);
		if (type == null) {
			throw new IllegalArgumentException("type must not be null");
		}
		if (status == null) {
			throw new IllegalArgumentException("status must not be null");
		}
		this.type = type;
		this.status = status;
		this.recordedAt = Instant.now();
	}

	public Transaction(BigDecimal amount, TransactionType type, TransactionStatus status, Instant recordedAt) {
		this(amount, type, status);
		if (recordedAt == null) {
			throw new IllegalArgumentException("recordedAt must not be null");
		}
		this.recordedAt = recordedAt;
	}

	public Long getId() {
		return id;
	}

	public Payment getPayment() {
		return payment;
	}

	void setPayment(Payment payment) {
		this.payment = payment;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		if (amount == null || amount.signum() <= 0) {
			throw new IllegalArgumentException("amount must be greater than zero");
		}
		this.amount = amount;
	}

	public TransactionType getType() {
		return type;
	}

	public TransactionStatus getStatus() {
		return status;
	}

	public Instant getRecordedAt() {
		return recordedAt;
	}
}
