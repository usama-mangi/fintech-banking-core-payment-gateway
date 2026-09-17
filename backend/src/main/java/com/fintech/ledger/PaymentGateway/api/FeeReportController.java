package com.fintech.ledger.PaymentGateway.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fee revenue reporting for the internal finance surface. Scoped in TASK-14:
 * merchant keys are rejected by the auth filter registration for this path.
 */
@RestController
public class FeeReportController {

	private final FeeReportService feeReportService;

	public FeeReportController(FeeReportService feeReportService) {
		this.feeReportService = feeReportService;
	}

	@GetMapping("/api/fees")
	public FeeReportService.FeeReport fees() {
		return feeReportService.report();
	}
}
