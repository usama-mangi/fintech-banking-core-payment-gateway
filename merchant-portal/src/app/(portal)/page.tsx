import type { Metadata } from "next";
import Link from "next/link";
import { currentSession } from "@/lib/session";
import { listPayments, ApiError, type PaymentSummary } from "@/lib/api";
import { StatusBadge } from "@/components/StatusBadge";
import { NewPaymentForm } from "@/components/NewPaymentForm";

export const metadata: Metadata = {
	title: "Overview",
};

async function load(apiKey: string): Promise<{
	payments: PaymentSummary[];
	error: string | null;
}> {
	try {
		const page = await listPayments(apiKey, undefined, 0);
		return { payments: page.content, error: null };
	} catch (error) {
		if (error instanceof ApiError) {
			return { payments: [], error: error.message };
		}
		return { payments: [], error: "Could not reach the gateway API. Is the backend running?" };
	}
}

export default async function OverviewPage() {
	const session = await currentSession();
	if (!session) {
		return null;
	}
	const { payments, error } = await load(session.apiKey);

	const captured = payments
		.filter((p) => p.status === "CAPTURED")
		.reduce((sum, p) => sum + Number(p.amount), 0);
	const refunded = payments
		.filter((p) => p.status === "REFUNDED")
		.reduce((sum, p) => sum + Number(p.amount), 0);

	return (
		<section aria-labelledby="overview-heading" className="flex flex-col gap-6">
			<h1 id="overview-heading" className="font-heading text-xl font-semibold">
				Overview
			</h1>

			{error ? (
				<p role="alert" className="bg-rust-wash px-3 py-2 text-sm text-rust">
					The gateway returned: {error}
				</p>
			) : (
				<>
					<div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
						<div className="border border-rule bg-white p-4">
							<p className="text-xs uppercase tracking-widest text-ink-muted">Captured</p>
							<p className="mt-1 font-ledger text-2xl">{captured.toFixed(2)}</p>
						</div>
						<div className="border border-rule bg-white p-4">
							<p className="text-xs uppercase tracking-widest text-ink-muted">Refunded</p>
							<p className="mt-1 font-ledger text-2xl">{refunded.toFixed(2)}</p>
						</div>
						<div className="border border-rule bg-white p-4">
							<p className="text-xs uppercase tracking-widest text-ink-muted">Payments</p>
							<p className="mt-1 font-ledger text-2xl">{payments.length}</p>
						</div>
					</div>

					<NewPaymentForm />

					<h2 className="font-heading text-base font-semibold">Recent payments</h2>
					{payments.length > 0 ? (
						<table className="w-full border-collapse border border-rule bg-white text-sm">
							<thead>
								<tr>
									{["ID", "Amount", "Status", "Description"].map((h) => (
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
								{payments.slice(0, 8).map((p, i) => (
									<tr key={p.id} className="ledger-row border-b border-rule/50" style={{ animationDelay: `${i * 30}ms` }}>
										<td className="px-3 py-2">
											<Link href={`/payments/${p.id}`} className="text-accent underline underline-offset-4 hover:text-ink">
												<span className="font-ledger">#{p.id}</span>
											</Link>
										</td>
										<td className="px-3 py-2 font-ledger">
											{p.amount} {p.currency}
										</td>
										<td className="px-3 py-2">
											<StatusBadge status={p.status} />
										</td>
										<td className="px-3 py-2">{p.description}</td>
									</tr>
								))}
							</tbody>
						</table>
					) : (
						<p className="text-sm text-ink-muted">
							No payments yet. Create your first one above.
						</p>
					)}
				</>
			)}
		</section>
	);
}
