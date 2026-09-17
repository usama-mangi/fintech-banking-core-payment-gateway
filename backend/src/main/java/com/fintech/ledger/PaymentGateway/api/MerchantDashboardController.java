package com.fintech.ledger.PaymentGateway.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.WebUtils;

import com.fintech.ledger.PaymentGateway.domain.Merchant;
import com.fintech.ledger.PaymentGateway.domain.Payment;
import com.fintech.ledger.PaymentGateway.repository.MerchantRepository;
import com.fintech.ledger.PaymentGateway.repository.PaymentRepository;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Merchant self-service dashboard: a server-rendered page showing the
 * authenticated merchant their own payments and totals. Sign-in uses the
 * merchant's real API key once; it is then held in a short-lived httpOnly
 * cookie so the key never lingers in URLs or page content.
 */
@Controller
public class MerchantDashboardController {

	private static final String KEY_COOKIE = "merchant_dashboard_key";
	private static final String HASH_COOKIE = "merchant_dashboard_hash";

	private final PaymentRepository paymentRepository;
	private final MerchantRepository merchantRepository;

	public MerchantDashboardController(PaymentRepository paymentRepository, MerchantRepository merchantRepository) {
		this.paymentRepository = paymentRepository;
		this.merchantRepository = merchantRepository;
	}

	@GetMapping("/dashboard")
	public String dashboard(@RequestParam(name = "key", required = false) String keyParam,
			@CookieValue(name = KEY_COOKIE, required = false) String keyCookie,
			Model model, HttpServletRequest request, HttpServletResponse response) {
		String key = keyParam != null ? keyParam : keyCookie;
		Merchant merchant = key == null ? null : merchantRepository.findByApiKey(key).orElse(null);
		if (merchant == null) {
			model.addAttribute("error", "Invalid or missing API key. Enter the key issued at registration.");
			return "dashboard-login";
		}
		if (keyParam != null) {
			// Move the key out of the URL into a short-lived cookie.
			Cookie cookie = new Cookie(KEY_COOKIE, key);
			cookie.setHttpOnly(true);
			cookie.setPath("/dashboard");
			cookie.setMaxAge(60 * 60);
			response.addCookie(cookie);
			return "redirect:/dashboard";
		}

		model.addAttribute("merchantName", merchant.getBusinessName());
		model.addAttribute("merchantId", merchant.getId());

		List<Payment> payments = paymentRepository
				.findByMerchantId(merchant.getId(),
						PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "createdAt")))
				.getContent();
		model.addAttribute("payments", payments);

		BigDecimal captured = payments.stream()
				.filter(p -> p.getStatus() == com.fintech.ledger.PaymentGateway.domain.PaymentStatus.CAPTURED)
				.map(Payment::getAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal refunded = payments.stream()
				.filter(p -> p.getStatus() == com.fintech.ledger.PaymentGateway.domain.PaymentStatus.REFUNDED)
				.map(Payment::getAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		model.addAttribute("captured", captured.setScale(2).toPlainString());
		model.addAttribute("refunded", refunded.setScale(2).toPlainString());
		model.addAttribute("paymentCount", payments.size());

		return "dashboard";
	}

	@PostMapping("/dashboard/logout")
	public String logout(HttpServletResponse response) {
		Cookie cookie = new Cookie(KEY_COOKIE, "");
		cookie.setHttpOnly(true);
		cookie.setPath("/dashboard");
		cookie.setMaxAge(0);
		response.addCookie(cookie);
		return "redirect:/dashboard";
	}
}
