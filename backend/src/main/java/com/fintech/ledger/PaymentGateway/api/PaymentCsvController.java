package com.fintech.ledger.PaymentGateway.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fintech.ledger.PaymentGateway.domain.Payment;
import com.fintech.ledger.PaymentGateway.domain.PaymentStatus;
import com.fintech.ledger.PaymentGateway.repository.PaymentRepository;

import jakarta.servlet.http.HttpServletResponse;

/**
 * CSV export of payments, scoped exactly like the JSON list: merchant keys
 * export only their own rows, the internal key any merchant or all. Rows
 * stream through paged repository reads so a large ledger never materializes
 * in memory.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentCsvController {

	private static final int CHUNK = 500;

	private final PaymentRepository paymentRepository;

	public PaymentCsvController(PaymentRepository paymentRepository) {
		this.paymentRepository = paymentRepository;
	}

	@GetMapping("/export")
	public ResponseEntity<Void> export(
			@RequestAttribute(name = ApiKeyAuthFilter.MERCHANT_ATTRIBUTE, required = false) Long merchantId,
			@RequestAttribute(name = ApiKeyAuthFilter.INTERNAL_ATTRIBUTE, required = false) Boolean internal,
			@RequestParam(name = "merchantId", required = false) Long requestedMerchantId,
			@RequestParam(name = "status", required = false) PaymentStatus status,
			HttpServletResponse response) throws IOException {

		final Long scopeMerchantId;
		if (Boolean.TRUE.equals(internal)) {
			scopeMerchantId = requestedMerchantId;
		}
		else {
			if (merchantId == null) {
				throw new ApiExceptions.UnauthorizedException("missing or unknown API key");
			}
			if (requestedMerchantId != null && !requestedMerchantId.equals(merchantId)) {
				throw new ApiExceptions.ForbiddenException("merchantId in query does not match the authenticated merchant");
			}
			scopeMerchantId = merchantId;
		}

		response.setContentType("text/csv;charset=UTF-8");
		response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"payments-export.csv\"");
		var out = response.getWriter();
		out.write("id,merchant_id,amount,currency,status,description,idempotency_key,created_at\r\n");

		int pageNumber = 0;
		Page<Payment> chunk;
		do {
			PageRequest request = PageRequest.of(pageNumber++, CHUNK,
					org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
			chunk = scopeMerchantId != null
					? (status != null
							? paymentRepository.findByMerchantIdAndStatus(scopeMerchantId, status, request)
							: paymentRepository.findByMerchantId(scopeMerchantId, request))
					: (status != null
							? paymentRepository.findByStatus(status, request)
							: paymentRepository.findAll(request));
			for (Payment payment : chunk.getContent()) {
				out.write(toCsvRow(payment));
			}
		} while (chunk.hasNext());

		out.flush();
		return ResponseEntity.ok().build();
	}

	private static String toCsvRow(Payment payment) {
		return String.join(",",
				String.valueOf(payment.getId()),
				String.valueOf(payment.getMerchant().getId()),
				payment.getAmount().toPlainString(),
				payment.getCurrency().name(),
				payment.getStatus().name(),
				csvEscape(payment.getDescription()),
				csvEscape(payment.getIdempotencyKey()),
				payment.getCreatedAt() == null ? "" : payment.getCreatedAt().toString())
				+ "\r\n";
	}

	/** RFC 4180 field escaping: quote when needed, double embedded quotes. */
	private static String csvEscape(String value) {
		if (value == null) {
			return "";
		}
		if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
			return "\"" + value.replace("\"", "\"\"") + "\"";
		}
		return value;
	}
}
