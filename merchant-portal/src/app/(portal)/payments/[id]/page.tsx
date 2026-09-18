import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { currentSession } from "@/lib/session";
import { getPayment, ApiError, type PaymentDetail } from "@/lib/api";
import { StatusBadge } from "@/components/StatusBadge";
import { TransactionForm } from "@/components/TransactionForm";

export const metadata: Metadata = {
	title: "Payment detail",
};

async function load(apiKey: string, id: number): Promise<PaymentDetail | null> {
	try {
		return await getPayment(apiKey, id);
	} catch (error) {
		if (error instanceof ApiError && error.status === 404) {
			return null;
		}
		throw error;
	}
}

export default async function PaymentDetailPage({
	params,
}: {
	params: Promise<{ id: string }>;
}) {
	const session = await currentSession();
	if (!session) {
		return null;
	}
	const { id } = await params;
	const paymentId = Number(id);
	if (!Number.isSafeInteger(paymentId) || paymentId <= 0) {
		notFound();
	}

	let payment: PaymentDetail | null;
	let error: string | null = null;
	try {
		payment = await load(session.apiKey, paymentId);
	} catch (e) {
		payment = null;
		error = e instanceof ApiError ? e.message : "Could not reach the gateway API. Is the backend running?";
	}

	if (error) {
		return (
			<section className="flex flex-col gap-4">
				<Link href="/payments" className="text-sm text-accent underline underline-offset-4">
					← All payments
				</Link>
				<p role="alert" className="bg-rust-wash px-3 py-2 text-sm text-rust">
					The gateway returned: {error}
				</p>
			</section>
		);
	}
	if (!payment) {
		notFound();
	}

	return (
		<section aria-labelledby="detail-heading" className="flex flex-col gap-6">
			<Link href="/payments" className="text-sm text-accent underline underline-offset-4">
				← All payments
			</Link>
			<div className="flex items-baseline justify-between">
				<h1 id="detail-heading" className="font-heading text-xl font-semibold">
					Payment <span className="font-ledger">#{payment.id}</span>
				</h1>
				<StatusBadge status={payment.status} />
			</div>

			<div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
				<div className="border border-rule bg-white p-4">
					<p className="text-xs uppercase tracking-widest text-ink-muted">Amount</p>
					<p className="mt-1 font-ledger text-xl">
						{payment.amount} {payment.currency}
					</p>
				</div>
				<div className="border border-rule bg-white p-4">
					<p className="text-xs uppercase tracking-widest text-ink-muted">Captured (USD)</p>
					<p className="mt-1 font-ledger text-xl">{payment.capturedAmountInUsd}</p>
				</div>
				<div className="border border-rule bg-white p-4">
					<p className="text-xs uppercase tracking-widest text-ink-muted">Idempotency key</p>
					<p className="mt-1 break-all font-ledger text-xs">{payment.idempotencyKey}</p>
				</div>
			</div>

			<p className="text-sm text-ink-muted">{payment.description}</p>

			<h2 className="font-heading text-base font-semibold">Ledger</h2>
			<table className="w-full border-collapse border border-rule bg-white text-sm">
				<caption className="sr-only">Chronological transaction ledger, oldest first</caption>
				<thead>
					<tr>
						{["Type", "Amount", "Status", "Recorded"].map((h) => (
							<th
								key={h}
								className="border-b border-rule px-3 py-2 text-left text-xs font-normal uppercase tracking-widest text-ink-muted"
							>
								{h}
							</th>
						))}
					</tr>
				</thead>
				<tbody>
					{payment.transactions.map((t, i) => (
						<tr key={t.id} className="ledger-row border-b border-rule/50" style={{ animationDelay: `${i * 30}ms` }}>
							<td className="px-3 py-2 font-ledger">{t.type}</td>
							<td className="px-3 py-2 font-ledger">{t.amount}</td>
							<td className="px-3 py-2">
								<StatusBadge status={t.status} />
							</td>
							<td className="px-3 py-2 font-ledger text-xs text-ink-muted">{t.recordedAt}</td>
						</tr>
					))}
				</tbody>
			</table>

			<TransactionForm paymentId={payment.id} amount={payment.amount} />
		</section>
	);
}
