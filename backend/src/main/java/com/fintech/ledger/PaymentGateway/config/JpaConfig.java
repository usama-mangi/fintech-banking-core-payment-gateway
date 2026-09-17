package com.fintech.ledger.PaymentGateway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables JPA auditing for @CreatedDate / @LastModifiedDate fields.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
