"use client";

import { useActionState } from "react";
import { createPaymentAction, type FormState } from "@/app/actions";

export function NewPaymentForm() {
	const [state, formAction, pending] = useActionState<FormState | null, FormData>(
		createPaymentAction,
		null
	);

	return (
		<form action={formAction} className="border border-rule bg-white p-4">
			<h2 className="font-heading text-base font-semibold">New payment</h2>
			<p className="mb-4 mt-1 text-sm text-ink-muted">
				Exact amounts, two decimal places. Duplicate submits are safe — the
				gateway deduplicates by idempotency key.
			</p>
			<div className="flex flex-wrap items-end gap-3">
				<label className="flex flex-col gap-1 text-sm">
					Amount
					<input
						name="amount"
						required
						placeholder="100.00"
						pattern="\d+\.\d{2}"
						className="min-h-[44px] w-32 border border-rule bg-white px-3 py-2 font-ledger text-sm focus:border-accent focus:outline-none"
					/>
				</label>
				<label className="flex flex-col gap-1 text-sm">
					Currency
					<input
						name="currency"
						required
						placeholder="USD"
						pattern="[A-Za-z]{3}"
						maxLength={3}
						className="min-h-[44px] w-24 border border-rule bg-white px-3 py-2 font-ledger text-sm uppercase focus:border-accent focus:outline-none"
					/>
				</label>
				<label className="flex flex-1 flex-col gap-1 text-sm">
					Description
					<input
						name="description"
						required
						placeholder="Order #1234"
						className="min-h-[44px] min-w-48 border border-rule bg-white px-3 py-2 text-sm focus:border-accent focus:outline-none"
					/>
				</label>
				<button
					type="submit"
					disabled={pending}
					className="min-h-[44px] bg-accent px-4 py-2 text-sm font-semibold text-white hover:opacity-90 disabled:opacity-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
				>
					{pending ? "Creating…" : "Create payment"}
				</button>
			</div>
			{state?.error ? (
				<p role="alert" className="mt-3 bg-rust-wash px-3 py-2 text-sm text-rust">
					{state.error}
				</p>
			) : null}
			{state?.ok ? (
				<p role="status" className="mt-3 bg-accent-wash px-3 py-2 text-sm text-accent">
					Payment created — find it in the list below.
				</p>
			) : null}
		</form>
	);
}
