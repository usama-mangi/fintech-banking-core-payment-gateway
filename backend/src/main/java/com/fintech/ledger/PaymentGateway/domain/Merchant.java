package com.fintech.ledger.PaymentGateway.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A merchant that processes payments through the gateway.
 */
@Entity
@Table(name = "merchants")
public class Merchant extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotBlank
	@Size(max = 255)
	@Column(nullable = false)
	private String businessName;

	@NotBlank
	@jakarta.validation.constraints.Email
	@Size(max = 255)
	@Column(nullable = false, unique = true)
	private String email;

	@NotBlank
	@Size(max = 64)
	@Column(nullable = false, unique = true, updatable = false)
	private String apiKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MerchantStatus status = MerchantStatus.ACTIVE;

	protected Merchant() {
		// Required by JPA
	}

	public Merchant(String businessName, String email, String apiKey) {
		setBusinessName(businessName);
		setEmail(email);
		setApiKey(apiKey);
	}

	public Long getId() {
		return id;
	}

	public String getBusinessName() {
		return businessName;
	}

	public void setBusinessName(String businessName) {
		if (businessName == null || businessName.isBlank()) {
			throw new IllegalArgumentException("businessName must not be blank");
		}
		this.businessName = businessName;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		if (email == null || email.isBlank()) {
			throw new IllegalArgumentException("email must not be blank");
		}
		this.email = email;
	}

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalArgumentException("apiKey must not be blank");
		}
		this.apiKey = apiKey;
	}

	public MerchantStatus getStatus() {
		return status;
	}

	public void setStatus(MerchantStatus status) {
		this.status = status;
	}
}
