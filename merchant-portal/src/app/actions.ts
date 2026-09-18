"use server";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { revalidatePath } from "next/cache";
import {
	createPayment,
	recordTransaction,
	ApiError,
	type TransactionType,
} from "@/lib/api";
import { SESSION_COOKIE, sessionCookie, verifySession } from "@/lib/session";

export interface FormState {
	error?: string;
	ok?: boolean;
}

async function requireKey(): Promise<string> {
	const store = await cookies();
	const session = verifySession(store.get(SESSION_COOKIE)?.value);
	if (!session) {
		redirect("/login");
	}
	return session.apiKey;
}

export async function signOutAction(): Promise<void> {
	const store = await cookies();
	store.set(SESSION_COOKIE, "", sessionCookie(0));
	redirect("/login");
}

export async function createPaymentAction(
	_prev: FormState | null,
	formData: FormData
): Promise<FormState> {
	const amount = String(formData.get("amount") ?? "").trim();
	const currency = String(formData.get("currency") ?? "").trim().toUpperCase();
	const description = String(formData.get("description") ?? "").trim();

	if (!/^\d+\.\d{2}$/.test(amount)) {
		return { error: "Amount must have exactly two decimal places, e.g. 100.00." };
	}
	if (!/^[A-Z]{3}$/.test(currency)) {
		return { error: "Currency must be a 3-letter ISO 4217 code, e.g. USD." };
	}
	if (!description) {
		return { error: "A description is required." };
	}

	const apiKey = await requireKey();
	try {
		// Idempotency key minted server-side: a double-submit can never
		// create a duplicate payment.
		const idempotencyKey = `portal-${crypto.randomUUID()}`;
		await createPayment(apiKey, { amount, currency, description, idempotencyKey });
	} catch (error) {
		if (error instanceof ApiError) {
			return { error: error.message };
		}
		return { error: "Could not reach the gateway API. Is the backend running?" };
	}
	revalidatePath("/payments");
	revalidatePath("/");
	return { ok: true };
}

const TXN_TYPES: TransactionType[] = ["AUTHORIZATION", "CAPTURE", "REFUND", "FEE"];

export async function recordTransactionAction(
	_prev: FormState | null,
	formData: FormData
): Promise<FormState> {
	const paymentId = Number(formData.get("paymentId"));
	const type = String(formData.get("type") ?? "") as TransactionType;
	const amount = String(formData.get("amount") ?? "").trim();

	if (!Number.isSafeInteger(paymentId) || paymentId <= 0) {
		return { error: "Unknown payment." };
	}
	if (!TXN_TYPES.includes(type)) {
		return { error: "Unknown transaction type." };
	}
	if (!/^\d+\.\d{2}$/.test(amount)) {
		return { error: "Amount must have exactly two decimal places, e.g. 100.00." };
	}

	const apiKey = await requireKey();
	try {
		await recordTransaction(apiKey, paymentId, { type, amount });
	} catch (error) {
		if (error instanceof ApiError) {
			// 409 illegal transitions surface with the domain's message,
			// e.g. "cannot capture a payment in status REQUIRES_PAYMENT".
			return { error: error.message };
		}
		return { error: "Could not reach the gateway API. Is the backend running?" };
	}
	revalidatePath(`/payments/${paymentId}`);
	revalidatePath("/payments");
	revalidatePath("/");
	return { ok: true };
}
