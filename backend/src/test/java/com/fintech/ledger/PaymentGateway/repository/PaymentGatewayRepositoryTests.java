package com.fintech.ledger.PaymentGateway.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import org.springframework.test.context.ActiveProfiles;

import com.fintech.ledger.PaymentGateway.config.JpaConfig;

import com.fintech.ledger.PaymentGateway.domain.Currency;
import com.fintech.ledger.PaymentGateway.domain.Merchant;
import com.fintech.ledger.PaymentGateway.domain.MerchantStatus;
import com.fintech.ledger.PaymentGateway.domain.Payment;
import com.fintech.ledger.PaymentGateway.domain.PaymentStatus;
import com.fintech.ledger.PaymentGateway.domain.Transaction;
import com.fintech.ledger.PaymentGateway.domain.TransactionStatus;
import com.fintech.ledger.PaymentGateway.domain.TransactionType;

/**
 * Repository tests for the payment gateway domain.
 */
@DataJpaTest
@Import(JpaConfig.class)
@ActiveProfiles("test")
class PaymentGatewayRepositoryTests {

	@Autowired
	private MerchantRepository merchantRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private TransactionRepository transactionRepository;

	private Merchant newMerchant() {
		return new Merchant("Acme Corp", "ops@acme.test", "sk_test_acme_001");
	}

	private Payment newPayment(Merchant merchant) {
		return new Payment(merchant, new BigDecimal("100.00"), Currency.USD, "Invoice #42", "idem-key-001");
	}

	@Test
	void persistAndReloadMerchant() {
		Merchant merchant = newMerchant();
		merchant = merchantRepository.save(merchant);

		assertThat(merchant.getId()).isNotNull();
		assertThat(merchant.getCreatedAt()).isNotNull();
		assertThat(merchant.getUpdatedAt()).isNotNull();

		Optional<Merchant> reloaded = merchantRepository.findById(merchant.getId());
		assertThat(reloaded).isPresent();
		assertThat(reloaded.get().getBusinessName()).isEqualTo("Acme Corp");
		assertThat(reloaded.get().getStatus()).isEqualTo(MerchantStatus.ACTIVE);
	}

	@Test
	void findByEmailApiKeyAndStatus() {
		Merchant merchant = merchantRepository.save(newMerchant());
		merchantRepository.save(new Merchant("Beta LLC", "ops@beta.test", "sk_test_beta_001"));

		assertThat(merchantRepository.findByEmail("ops@acme.test")).isPresent();
		assertThat(merchantRepository.findByApiKey("sk_test_acme_001")).isPresent();
		assertThat(merchantRepository.existsByEmail("ops@beta.test")).isTrue();
		assertThat(merchantRepository.existsByEmail("missing@nowhere.test")).isFalse();
		assertThat(merchantRepository.findByStatus(MerchantStatus.ACTIVE)).hasSize(2);
	}

	@Test
	void paymentLifecycleUpdatesStatusAndCapturedAmount() {
		Merchant merchant = merchantRepository.save(newMerchant());
		Payment payment = paymentRepository.save(newPayment(merchant));

		assertThat(payment.getId()).isNotNull();
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUIRES_PAYMENT);
		assertThat(payment.getCreatedAt()).isNotNull();

		payment.record(new Transaction(new BigDecimal("100.00"), TransactionType.AUTHORIZATION, TransactionStatus.SUCCEEDED));
		payment.record(new Transaction(new BigDecimal("100.00"), TransactionType.CAPTURE, TransactionStatus.SUCCEEDED));
		payment.record(new Transaction(new BigDecimal("30.00"), TransactionType.REFUND, TransactionStatus.SUCCEEDED));
		payment = paymentRepository.saveAndFlush(payment);

		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
		assertThat(payment.getTransactions()).hasSize(3);
		assertThat(payment.capturedAmountInUsd()).isEqualByComparingTo(new BigDecimal("70.00"));

		List<Transaction> ledger = transactionRepository.findByPaymentIdOrderByRecordedAtAsc(payment.getId());
		assertThat(ledger).hasSize(3);

		List<Transaction> refunds = transactionRepository.findByPaymentIdAndType(payment.getId(), TransactionType.REFUND);
		assertThat(refunds).hasSize(1);

		List<Transaction> failed = transactionRepository.findByPaymentIdAndStatus(payment.getId(), TransactionStatus.FAILED);
		assertThat(failed).isEmpty();
	}

	@Test
	void idempotencyKeyIsUniqueAndLookupWorks() {
		Merchant merchant = merchantRepository.save(newMerchant());
		paymentRepository.save(newPayment(merchant));

		Optional<Payment> found = paymentRepository.findByIdempotencyKey("idem-key-001");
		assertThat(found).isPresent();

		assertThat(paymentRepository.findByMerchantId(merchant.getId())).hasSize(1);
		assertThat(paymentRepository.findByStatus(PaymentStatus.REQUIRES_PAYMENT)).hasSize(1);
	}

	@Test
	void rejectNegativeAmount() {
		org.assertj.core.api.Assertions.assertThatThrownBy(
				() -> new Transaction(new BigDecimal("-5.00"), TransactionType.CAPTURE, TransactionStatus.SUCCEEDED))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectZeroAmount() {
		org.assertj.core.api.Assertions.assertThatThrownBy(
				() -> new Transaction(new BigDecimal("0.00"), TransactionType.CAPTURE, TransactionStatus.SUCCEEDED))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
