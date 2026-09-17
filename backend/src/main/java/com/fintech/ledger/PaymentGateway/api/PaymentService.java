package com.fintech.ledger.PaymentGateway.api;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fintech.ledger.PaymentGateway.domain.Currency;
import com.fintech.ledger.PaymentGateway.domain.Merchant;
import com.fintech.ledger.PaymentGateway.domain.Payment;
import com.fintech.ledger.PaymentGateway.domain.PaymentStatus;
import com.fintech.ledger.PaymentGateway.domain.Transaction;
import com.fintech.ledger.PaymentGateway.domain.TransactionStatus;
import com.fintech.ledger.PaymentGateway.domain.TransactionType;
import com.fintech.ledger.PaymentGateway.repository.MerchantRepository;
import com.fintech.ledger.PaymentGateway.repository.PaymentRepository;
import com.fintech.ledger.PaymentGateway.repository.TransactionRepository;

/**
 * Payment use cases: idempotent creation, lookup, listing and ledger
 * recording. Status transitions stay inside {@link Payment#record}.
 */
@Service
public class PaymentService {

	private final PaymentRepository paymentRepository;
	private final MerchantRepository merchantRepository;
	private final TransactionRepository transactionRepository;

	public PaymentService(PaymentRepository paymentRepository, MerchantRepository merchantRepository,
			TransactionRepository transactionRepository) {
		this.paymentRepository = paymentRepository;
		this.merchantRepository = merchantRepository;
		this.transactionRepository = transactionRepository;
	}

	@Transactional
	public CreatedPayment create(Long authenticatedMerchantId, PaymentDtos.CreatePaymentRequest request) {
		Long merchantId = authenticatedMerchantId;
		if (request.merchantId() != null && !request.merchantId().equals(authenticatedMerchantId)) {
			throw new ApiExceptions.ForbiddenException("merchantId in body does not match the authenticated merchant");
		}
		if (merchantId == null) {
			throw new ApiExceptions.UnauthorizedException("missing or unknown API key");
		}
		Merchant merchant = merchantRepository.findById(merchantId)
				.orElseThrow(() -> new ApiExceptions.NotFoundException("merchant " + merchantId + " not found"));

		return paymentRepository.findByIdempotencyKey(request.idempotencyKey())
				.map(existing -> new CreatedPayment(toSummary(existing), false))
				.orElseGet(() -> {
					Payment payment = new Payment(merchant, parseAmount(request.amount()),
							parseCurrency(request.currency()), request.description().trim(), request.idempotencyKey());
					payment = paymentRepository.save(payment);
					return new CreatedPayment(toSummary(payment), true);
				});
	}

	/**
	 * Detail reads are scoped: a merchant key on another merchant's payment
	 * gets 404 - existence of other merchants' payments is not disclosed.
	 */
	@Transactional(readOnly = true)
	public PaymentDtos.PaymentDetail getDetail(Long id, Long authenticatedMerchantId, boolean internal) {
		Payment payment = loadPayment(id);
		if (!internal && authenticatedMerchantId != null
				&& !payment.getMerchant().getId().equals(authenticatedMerchantId)) {
			throw new ApiExceptions.NotFoundException("payment " + id + " not found");
		}
		return toDetail(payment);
	}

	/**
	 * Listing scoped by auth tier: merchant keys are forced to their own
	 * merchantId (a differing explicit one is a 403); the internal key may
	 * filter by any merchant or list across all.
	 */
	@Transactional(readOnly = true)
	public PageResponse<PaymentDtos.PaymentSummary> list(Long authenticatedMerchantId, boolean internal,
			Long requestedMerchantId, PaymentStatus status, Pageable pageable) {
		Long merchantId;
		if (internal) {
			merchantId = requestedMerchantId;
		}
		else {
			if (authenticatedMerchantId == null) {
				throw new ApiExceptions.UnauthorizedException("missing or unknown API key");
			}
			if (requestedMerchantId != null && !requestedMerchantId.equals(authenticatedMerchantId)) {
				throw new ApiExceptions.ForbiddenException("merchantId in query does not match the authenticated merchant");
			}
			merchantId = authenticatedMerchantId;
		}

		Page<Payment> page;
		if (merchantId != null && status != null) {
			page = paymentRepository.findByMerchantIdAndStatus(merchantId, status, pageable);
		}
		else if (merchantId != null) {
			page = paymentRepository.findByMerchantId(merchantId, pageable);
		}
		else if (status != null) {
			page = paymentRepository.findByStatus(status, pageable);
		}
		else {
			page = paymentRepository.findAll(pageable);
		}
		return PageResponse.from(page, PaymentService::toSummary);
	}

	@Transactional
	public PaymentDtos.TransactionResponse recordTransaction(Long paymentId, PaymentDtos.RecordTransactionRequest request) {
		Payment payment = loadPayment(paymentId);
		TransactionType type = parseEnum(TransactionType.class, request.type(), "type");
		TransactionStatus status = request.status() == null ? TransactionStatus.SUCCEEDED
				: parseEnum(TransactionStatus.class, request.status(), "status");

		Transaction transaction = new Transaction(parseAmount(request.amount()), type, status);
		payment.record(transaction);
		paymentRepository.save(payment);

		return new PaymentDtos.TransactionResponse(
				transaction.getId(),
				transaction.getType().name(),
				transaction.getStatus().name(),
				transaction.getAmount().toPlainString(),
				transaction.getRecordedAt().toString());
	}

	private Payment loadPayment(Long id) {
		return paymentRepository.findById(id)
				.orElseThrow(() -> new ApiExceptions.NotFoundException("payment " + id + " not found"));
	}

	private static BigDecimal parseAmount(String amount) {
		try {
			return new BigDecimal(amount);
		}
		catch (NumberFormatException ex) {
			throw new ApiExceptions.BadRequestException("amount is not a valid decimal: " + amount);
		}
	}

	private static Currency parseCurrency(String currency) {
		return parseEnum(Currency.class, currency, "currency");
	}

	private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
		try {
			return Enum.valueOf(type, value.trim().toUpperCase());
		}
		catch (IllegalArgumentException ex) {
			throw new ApiExceptions.BadRequestException("unknown " + field + ": " + value);
		}
	}

	private static PaymentDtos.PaymentSummary toSummary(Payment payment) {
		return new PaymentDtos.PaymentSummary(
				payment.getId(),
				payment.getMerchant().getId(),
				payment.getAmount().toPlainString(),
				payment.getCurrency().name(),
				payment.getStatus().name(),
				payment.getDescription(),
				payment.getIdempotencyKey(),
				payment.getCreatedAt() == null ? null : payment.getCreatedAt().toString());
	}

	private static PaymentDtos.PaymentDetail toDetail(Payment payment) {
		List<PaymentDtos.TransactionResponse> ledger = payment.getTransactions().stream()
				.map(transaction -> new PaymentDtos.TransactionResponse(
						transaction.getId(),
						transaction.getType().name(),
						transaction.getStatus().name(),
						transaction.getAmount().toPlainString(),
						transaction.getRecordedAt().toString()))
				.toList();
		return new PaymentDtos.PaymentDetail(
				payment.getId(),
				payment.getMerchant().getId(),
				payment.getAmount().toPlainString(),
				payment.getCurrency().name(),
				payment.getStatus().name(),
				payment.getDescription(),
				payment.getIdempotencyKey(),
				payment.capturedAmountInUsd().toPlainString(),
				payment.getCreatedAt() == null ? null : payment.getCreatedAt().toString(),
				ledger);
	}

	/** Result of an idempotent create: the payment plus whether it was new. */
	public record CreatedPayment(PaymentDtos.PaymentSummary payment, boolean created) {
	}
}
