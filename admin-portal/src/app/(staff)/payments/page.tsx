import type { Metadata } from "next";
import Link from "next/link";
import { ApiError, listPayments, type PaymentSummary } from "@/lib/api";
import { LedgerRow, LedgerTable, MonoCell, UtcTimestamp } from "@/components/LedgerTable";
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

async function loadPayments(status?: string): Promise<{ payments: PaymentSummary[]; error: string | null }> {
  try {
    return { payments: (await listPayments(status)).content, error: null };
  } catch (error) {
    if (error instanceof ApiError) {
      return { payments: [], error: error.message };
    }
    return { payments: [], error: "Could not reach the gateway API. Is the backend running?" };
  }
}

export default async function PaymentsPage({
  searchParams,
}: {
  searchParams: Promise<{ status?: string }>;
}) {
  const { status } = await searchParams;
  const { payments, error } = await loadPayments(status);

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
            className="min-h-[44px] border border-rule bg-white px-3 py-2 text-sm focus:border-ledger focus:outline-none"
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
          className="min-h-[44px] border border-ledger px-4 py-2 text-sm font-semibold text-ledger hover:bg-ledger-wash focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ledger"
        >
          Apply
        </button>
      </form>

      {error ? (
        <p role="alert" className="bg-rust-wash px-3 py-2 text-sm text-rust">
          The gateway returned: {error}
        </p>
      ) : payments.length === 0 ? (
        <p className="text-sm text-ink-muted">
          No payments match. Merchants submit payments through the API; once one lands it shows up here with its
          full ledger.
        </p>
      ) : (
        <LedgerTable
          caption="Payments with lifecycle status, filtered by status when selected"
          head={["Payment", "Amount", "Currency", "Status", "Description", "Created"]}
        >
          {payments.map((payment, index) => (
            <LedgerRow key={payment.id} index={index}>
              <td className="px-3 py-2">
                <Link
                  href={`/payments/${payment.id}`}
                  className="text-ledger underline underline-offset-4 hover:text-ink"
                >
                  <MonoCell>#{payment.id}</MonoCell>
                </Link>
              </td>
              <td className="px-3 py-2">
                <MonoCell>{payment.amount}</MonoCell>
              </td>
              <td className="px-3 py-2">
                <MonoCell>{payment.currency}</MonoCell>
              </td>
              <td className="px-3 py-2">
                <StatusBadge status={payment.status} />
              </td>
              <td className="px-3 py-2">{payment.description}</td>
              <td className="px-3 py-2">
                <UtcTimestamp value={payment.createdAt} />
              </td>
            </LedgerRow>
          ))}
        </LedgerTable>
      )}
    </section>
  );
}
