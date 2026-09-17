package com.fintech.ledger.PaymentGateway.api;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Builds the server-fixed {@link PageRequest} for list endpoints: newest
 * first, page/size validated before Spring Data sees them.
 */
public final class PageRequests {

	public static final int DEFAULT_SIZE = 20;
	public static final int MAX_SIZE = 100;

	private PageRequests() {
	}

	public static PageRequest of(Integer page, Integer size) {
		int pageNumber = page == null ? 0 : page;
		int sizeNumber = size == null ? DEFAULT_SIZE : size;
		if (pageNumber < 0) {
			throw new ApiExceptions.BadRequestException("page must be 0 or greater");
		}
		if (sizeNumber < 1) {
			throw new ApiExceptions.BadRequestException("size must be at least 1");
		}
		if (sizeNumber > MAX_SIZE) {
			throw new ApiExceptions.BadRequestException("size must not exceed " + MAX_SIZE);
		}
		return PageRequest.of(pageNumber, sizeNumber, Sort.by(Sort.Direction.DESC, "createdAt"));
	}
}
