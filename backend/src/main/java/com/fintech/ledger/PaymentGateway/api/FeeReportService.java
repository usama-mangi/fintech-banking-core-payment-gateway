package com.fintech.ledger.PaymentGateway.api;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fintech.ledger.PaymentGateway.repository.TransactionRepository;

/**
 * Fee revenue reporting: gateway fees recorded against payments, summed per
 * merchant and currency. Amounts stay exact decimal strings per currency —
 * no indicative USD conversion in a revenue report.
 */
@Service
public class FeeReportService {

	public record MerchantFees(
			Long merchantId,
			String businessName,
			String currency,
			String totalFees,
			long feeCount) {
	}

	public record FeeReport(
			List<MerchantFees> merchants,
			List<CurrencyTotal> totals) {
	}

	public record CurrencyTotal(String currency, String totalFees, long feeCount) {
	}

	private final TransactionRepository transactionRepository;

	public FeeReportService(TransactionRepository transactionRepository) {
		this.transactionRepository = transactionRepository;
	}

	@Transactional(readOnly = true)
	public FeeReport report() {
		List<Object[]> rows = transactionRepository.sumFeesByMerchant();

		List<MerchantFees> merchants = rows.stream()
				.map(row -> new MerchantFees(
						(Long) row[0],
						(String) row[1],
						row[2].toString(),
						row[3].toString(),
						(Long) row[4]))
				.toList();

		List<CurrencyTotal> totals = merchants.stream()
				.collect(java.util.stream.Collectors.groupingBy(
						MerchantFees::currency,
						java.util.LinkedHashMap::new,
						java.util.stream.Collectors.toList()))
				.entrySet().stream()
				.map(entry -> new CurrencyTotal(
						entry.getKey(),
						entry.getValue().stream()
								.map(MerchantFees::totalFees)
								.map(java.math.BigDecimal::new)
								.reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
								.toPlainString(),
						entry.getValue().stream().mapToLong(MerchantFees::feeCount).sum()))
				.toList();

		return new FeeReport(merchants, totals);
	}
}
