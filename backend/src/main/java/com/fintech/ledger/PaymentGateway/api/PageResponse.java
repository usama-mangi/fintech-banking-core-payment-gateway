package com.fintech.ledger.PaymentGateway.api;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * Wire contract for every paginated list endpoint. Wraps the page content
 * with the metadata a client needs to walk the collection.
 */
public record PageResponse<T>(
		List<T> content,
		int page,
		int size,
		long totalElements,
		int totalPages) {

	public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> mapper) {
		return new PageResponse<>(
				page.getContent().stream().map(mapper).toList(),
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages());
	}
}
