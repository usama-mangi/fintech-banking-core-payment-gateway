package com.fintech.ledger.PaymentGateway.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fintech.ledger.PaymentGateway.api.ApiKeyAuthFilter;
import com.fintech.ledger.PaymentGateway.repository.MerchantRepository;

/**
 * Registers the API-key filter for payment endpoints only. Merchant and
 * actuator endpoints stay key-exempt for the MVP internal portal.
 */
@Configuration
public class WebConfig {

	@Bean
	public FilterRegistrationBean<ApiKeyAuthFilter> apiKeyAuthFilter(MerchantRepository merchantRepository,
			org.springframework.core.env.Environment env) {
		FilterRegistrationBean<ApiKeyAuthFilter> registration = new FilterRegistrationBean<>(
				new ApiKeyAuthFilter(merchantRepository, env.getProperty("internal.api.key")));
		registration.addUrlPatterns("/api/payments/*", "/api/payments/export", "/api/fees");
		registration.setOrder(1);
		return registration;
	}
}
