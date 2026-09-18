/**
 * Typed client for the payment gateway API, called server-side only with the
 * signed-in merchant's own API key (from the session). TASK-14's ownership
 * scoping means this key sees only its merchant's data — no extra params.
 */

export const API_BASE = process.env.PAYMENT_API_BASE ?? "http://localhost:8080";

export interface ApiErrorBody {
	status: number;
	error: string;
	message: string;
	timestamp: string;
}

export class ApiError extends Error {
	readonly status: number;
	readonly body: ApiErrorBody | null;

	constructor(status: number, body: ApiErrorBody | null, options?: { cause?: unknown }) {
		super(body?.message ?? `Request failed with status ${status}`, options);
		this.name = "ApiError";
		this.status = status;
		this.body = body;
	}
}

async function request<T>(apiKey: string, path: string, init?: RequestInit): Promise<T> {
	let response: Response;
	try {
		response = await fetch(`${API_BASE}${path}`, {
			...init,
			headers: {
				Accept: "application/json",
				"X-API-Key": apiKey,
				...(init?.body ? { "Content-Type": "application/json" } : {}),
				...init?.headers,
			},
			cache: "no-store",
		});
	} catch (cause) {
		throw new ApiError(0, null, { cause });
	}
	if (!response.ok) {
		let body: ApiErrorBody | null = null;
		try {
			body = (await response.json()) as ApiErrorBody;
		} catch {
			body = null;
		}
		throw new ApiError(response.status, body);
	}
	return (await response.json()) as T;
}

export interface PaymentSummary {
	id: number;
	merchantId: number;
	amount: string;
	currency: string;
	status: string;
	description: string;
	idempotencyKey: string;
	createdAt: string | null;
}

export interface Transaction {
	id: number;
	type: string;
	status: string;
	amount: string;
	recordedAt: string;
}

export interface PaymentDetail extends PaymentSummary {
	capturedAmountInUsd: string;
	transactions: Transaction[];
}

export interface Page<T> {
	content: T[];
	page: number;
	size: number;
	totalElements: number;
	totalPages: number;
}

export function listPayments(apiKey: string, status?: string, page = 0): Promise<Page<PaymentSummary>> {
	const params = new URLSearchParams();
	if (status) {
		params.set("status", status);
	}
	if (page > 0) {
		params.set("page", String(page));
	}
	const query = params.size > 0 ? `?${params}` : "";
	return request<Page<PaymentSummary>>(apiKey, `/api/payments${query}`);
}

export function getPayment(apiKey: string, id: number): Promise<PaymentDetail> {
	return request<PaymentDetail>(apiKey, `/api/payments/${id}`);
}

export function createPayment(
	apiKey: string,
	body: { amount: string; currency: string; description: string; idempotencyKey: string },
): Promise<PaymentSummary> {
	return request<PaymentSummary>(apiKey, "/api/payments", {
		method: "POST",
		body: JSON.stringify(body),
	});
}

export type TransactionType = "AUTHORIZATION" | "CAPTURE" | "REFUND" | "FEE";

export function recordTransaction(
	apiKey: string,
	paymentId: number,
	body: { type: TransactionType; amount: string },
): Promise<Transaction> {
	return request<Transaction>(apiKey, `/api/payments/${paymentId}/transactions`, {
		method: "POST",
		body: JSON.stringify(body),
	});
}
