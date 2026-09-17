/**
 * Typed client for the payment gateway API. Server-side only: the base URL
 * and API key come from environment variables and never reach the browser.
 */

export const API_BASE = process.env.PAYMENT_API_BASE ?? "http://localhost:8080";
const API_KEY = process.env.PAYMENT_API_KEY ?? "";

/** Error shape produced by the backend's GlobalExceptionHandler. */
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

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      ...init,
      headers: {
        Accept: "application/json",
        ...(API_KEY ? { "X-API-Key": API_KEY } : {}),
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

export interface Merchant {
  id: number;
  businessName: string;
  email: string;
  apiKey: string;
  status: string;
  createdAt: string | null;
}

export interface Transaction {
  id: number;
  type: string;
  status: string;
  amount: string;
  recordedAt: string;
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

export function listMerchants(page = 0): Promise<Page<Merchant>> {
  const query = page > 0 ? `?page=${page}` : "";
  return request<Page<Merchant>>(`/api/merchants${query}`);
}

export function registerMerchant(businessName: string, email: string): Promise<Merchant> {
  return request<Merchant>("/api/merchants", {
    method: "POST",
    body: JSON.stringify({ businessName, email }),
  });
}

export type MerchantLifecycleAction = "suspend" | "reactivate" | "close";

export function transitionMerchant(id: number, action: MerchantLifecycleAction): Promise<Merchant> {
  return request<Merchant>(`/api/merchants/${id}/${action}`, { method: "POST" });
}

export function listPayments(status?: string, page = 0): Promise<Page<PaymentSummary>> {
  const params = new URLSearchParams();
  if (status) {
    params.set("status", status);
  }
  if (page > 0) {
    params.set("page", String(page));
  }
  const query = params.size > 0 ? `?${params}` : "";
  return request<Page<PaymentSummary>>(`/api/payments${query}`);
}

export function getPayment(id: number): Promise<PaymentDetail> {
  return request<PaymentDetail>(`/api/payments/${id}`);
}

export interface MerchantFees {
  merchantId: number;
  businessName: string;
  currency: string;
  totalFees: string;
  feeCount: number;
}

export interface FeeReport {
  merchants: MerchantFees[];
  totals: { currency: string; totalFees: string; feeCount: number }[];
}

export function getFeeReport(): Promise<FeeReport> {
  return request<FeeReport>("/api/fees");
}

export type { ApiErrorBody as ApiErrorBodyShape };
