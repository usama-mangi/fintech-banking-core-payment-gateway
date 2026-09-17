package com.fintech.ledger.PaymentGateway.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A payment requested by a merchant. Transactions (authorization, capture,
 * refund...) are recorded against it in chronological order.
 */
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "merchant_id", nullable = false)
	private Merchant merchant;

	@NotNull
	@DecimalMin(value = "0.01")
	@Digits(integer = 12, fraction = 2)
	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal amount;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 3)
	private Currency currency;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PaymentStatus status = PaymentStatus.REQUIRES_PAYMENT;

	@NotBlank
	@Size(max = 255)
	@Column(nullable = false)
	private String description;

	@NotBlank
	@Size(max = 64)
	@Column(name = "idempotency_key", nullable = false, unique = true)
	private String idempotencyKey;

	@OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	@OrderBy("recordedAt ASC")
	private List<Transaction> transactions = new ArrayList<>();

	protected Payment() {
		// Required by JPA
	}

	public Payment(Merchant merchant, BigDecimal amount, Currency currency, String description, String idempotencyKey) {
		setMerchant(merchant);
		setAmount(amount);
		setCurrency(currency);
		setDescription(description);
		setIdempotencyKey(idempotencyKey);
	}

	public Long getId() {
		return id;
	}

	public Merchant getMerchant() {
		return merchant;
	}

	private void setMerchant(Merchant merchant) {
		if (merchant == null) {
			throw new IllegalArgumentException("merchant must not be null");
		}
		this.merchant = merchant;
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

	public Currency getCurrency() {
		return currency;
	}

	private void setCurrency(Currency currency) {
		if (currency == null) {
			throw new IllegalArgumentException("currency must not be null");
		}
		this.currency = currency;
	}

	public PaymentStatus getStatus() {
		return status;
	}

	public void setStatus(PaymentStatus status) {
		this.status = status;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		if (description == null || description.isBlank()) {
			throw new IllegalArgumentException("description must not be blank");
		}
		this.description = description;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public void setIdempotencyKey(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new IllegalArgumentException("idempotencyKey must not be blank");
		}
		this.idempotencyKey = idempotencyKey;
	}

	public List<Transaction> getTransactions() {
		return Collections.unmodifiableList(transactions);
	}

	void addTransaction(Transaction transaction) {
		if (transaction == null) {
			throw new IllegalArgumentException("transaction must not be null");
		}
		transactions.add(transaction);
		transaction.setPayment(this);
	}

	void removeTransaction(Transaction transaction) {
		transactions.remove(transaction);
		transaction.setPayment(null);
	}

	/**
	 * Record a transaction against this payment, keeping the ledger
	 * chronological and moving the payment's status accordingly. Rejects
	 * transitions the lifecycle does not allow instead of silently ignoring
	 * them, so the ledger can never imply a state the status field contradicts.
	 */
	public void record(Transaction transaction) {
		if (transaction.getStatus() == TransactionStatus.FAILED) {
			// A declined entry is a fact about the processor, not the ledger's
			// position in the lifecycle; record it and fail the payment.
			addTransaction(transaction);
			status = PaymentStatus.FAILED;
			return;
		}
		TransactionType type = transaction.getType();
		if (type == TransactionType.AUTHORIZATION) {
			requireStatus(status == PaymentStatus.REQUIRES_PAYMENT,
					"cannot authorize a payment in status " + status);
			status = PaymentStatus.AUTHORIZED;
		}
		else if (type == TransactionType.CAPTURE) {
			requireStatus(status == PaymentStatus.AUTHORIZED,
					"cannot capture a payment in status " + status);
			status = PaymentStatus.CAPTURED;
		}
		else if (type == TransactionType.REFUND) {
			requireStatus(status == PaymentStatus.CAPTURED,
					"cannot refund a payment in status " + status);
			status = PaymentStatus.REFUNDED;
		}
		else if (type == TransactionType.CHARGEBACK) {
			requireStatus(status == PaymentStatus.CAPTURED || status == PaymentStatus.AUTHORIZED,
					"cannot charge back a payment in status " + status);
			status = PaymentStatus.CANCELLED;
		}
		else if (type == TransactionType.FEE) {
			// A fee is administrative: it charges against an existing
			// authorization and never moves the lifecycle, but it cannot
			// appear without one or exceed the amount it fees against.
			requireStatus(status == PaymentStatus.AUTHORIZED || status == PaymentStatus.CAPTURED
					|| status == PaymentStatus.REFUNDED,
					"cannot record a fee against a payment in status " + status);
			requireStatus(transaction.getAmount().compareTo(amount) <= 0,
					"fee " + transaction.getAmount().toPlainString() + " exceeds the payment amount "
							+ amount.toPlainString());
		}
		else {
			throw new IllegalStateException("unsupported transaction type for status transition: " + type);
		}
		addTransaction(transaction);
	}

	private static void requireStatus(boolean allowed, String message) {
		if (!allowed) {
			throw new IllegalStateException(message);
		}
	}

	/**
	 * Total amount captured on this payment, i.e. capture transactions net of
	 * refunds and chargebacks, converted to USD using indicative rates.
	 */
	public BigDecimal capturedAmountInUsd() {
		BigDecimal total = BigDecimal.ZERO;
		for (Transaction transaction : transactions) {
			BigDecimal sign = transaction.getType().getSign();
			BigDecimal rate = currency.getUsdRate();
			total = total.add(transaction.getAmount().multiply(sign).multiply(rate));
		}
		return total;
	}
}
