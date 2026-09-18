import type { Metadata } from "next";
import Link from "next/link";
import { currentSession } from "@/lib/session";
import { listPayments, ApiError, type PaymentSummary, type Page } from "@/lib/api";
import { StatusBadge } from "@/components/StatusBadge";

export const metadata: Metadata = {
	title: "Payments",
};

const STATUS_OPTIONS = [
	"REQUIRES_PAYMENT",
	"AUTHORIZED",
	"CAPTURED",
	"REFUNDED",
	"FAILED",
	"CANCELLED",
] as const;

async function load(
	apiKey: string,
	status?: string,
	page = 0
): Promise<{ payments: PaymentSummary[]; envelope: Page<PaymentSummary> | null; error: string | null }> {
	try {
		const envelope = await listPayments(apiKey, status, page);
		return { payments: envelope.content, envelope, error: null };
	} catch (error) {
		if (error instanceof ApiError) {
			return { payments: [], envelope: null, error: error.message };
		}
		return { payments: [], envelope: null, error: "Could not reach the gateway API. Is the backend running?" };
	}
}

export default async function PaymentsPage({
	searchParams,
}: {
	searchParams: Promise<{ status?: string; page?: string }>;
}) {
	const session = await currentSession();
	if (!session) {
		return null;
	}
	const params = await searchParams;
	const status = params.status || undefined;
	const page = Math.max(0, Number(params.page ?? "0") || 0);
	const { payments, envelope, error } = await load(session.apiKey, status, page);

	const hrefFor = (p: number) => {
		const qs = new URLSearchParams();
		if (status) {
			qs.set("status", status);
		}
		if (p > 0) {
			qs.set("page", String(p));
		}
		const query = qs.size > 0 ? `?${qs}` : "";
		return `/payments${query}`;
	};

	return (
		<section aria-labelledby="payments-heading" className="flex flex-col gap-6">
			<h1 id="payments-heading" className="font-heading text-xl font-semibold">
				Payments
			</h1>

			<form method="get" className="flex items-end gap-3 text-sm">
				<label className="flex flex-col gap-1">
					Status
					<select
						name="status"
						defaultValue={status ?? ""}
						className="min-h-[44px] border border-rule bg-white px-3 py-2 text-sm focus:border-accent focus:outline-none"
					>
						<option value="">All statuses</option>
						{STATUS_OPTIONS.map((option) => (
							<option key={option} value={option}>
								{option}
							</option>
						))}
					</select>
				</label>
				<button
					type="submit"
					className="min-h-[44px] border border-accent px-4 py-2 text-sm font-semibold text-accent hover:bg-accent-wash focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
				>
					Apply
				</button>
			</form>

			{error ? (
				<p role="alert" className="bg-rust-wash px-3 py-2 text-sm text-rust">
					The gateway returned: {error}
				</p>
			) : envelope && envelope.totalElements > 0 ? (
				<>
					<table className="w-full border-collapse border border-rule bg-white text-sm">
						<caption className="sr-only">
							Your payments with lifecycle status, filtered by status when selected
						</caption>
						<thead>
							<tr>
								{["ID", "Amount", "Status", "Description", "Created"].map((h) => (
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
							{payments.map((p, i) => (
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
									<td className="px-3 py-2 font-ledger text-xs text-ink-muted">{p.createdAt}</td>
								</tr>
							))}
						</tbody>
					</table>
					{envelope.totalPages > 1 ? (
						<nav aria-label="Pagination" className="flex items-center justify-between text-sm">
							{envelope.page > 0 ? (
								<Link href={hrefFor(envelope.page - 1)} className="text-accent underline underline-offset-4">
									← Previous
								</Link>
							) : (
								<span className="text-ink-muted">← Previous</span>
							)}
							<span className="text-ink-muted">
								Page {envelope.page + 1} of {envelope.totalPages} · {envelope.totalElements} payments
							</span>
							{envelope.page < envelope.totalPages - 1 ? (
								<Link href={hrefFor(envelope.page + 1)} className="text-accent underline underline-offset-4">
									Next →
								</Link>
							) : (
								<span className="text-ink-muted">Next →</span>
							)}
						</nav>
					) : null}
				</>
			) : (
				<p className="text-sm text-ink-muted">
					No payments match. Create one from the Overview page.
				</p>
			)}
		</section>
	);
}
