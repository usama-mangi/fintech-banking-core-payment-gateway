import type { Metadata } from "next";
import { notFound } from "next/navigation";
import Link from "next/link";
import { ApiError, getPayment, type PaymentDetail } from "@/lib/api";
import { LedgerRow, LedgerTable, MonoCell, UtcTimestamp } from "@/components/LedgerTable";
import { StatusBadge } from "@/components/StatusBadge";

export const metadata: Metadata = {
  title: "Payment detail",
};

async function loadPayment(id: number): Promise<{ payment: PaymentDetail | null; error: string | null }> {
  try {
    return { payment: await getPayment(id), error: null };
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return { payment: null, error: null };
    }
    if (error instanceof ApiError) {
      return { payment: null, error: error.message };
    }
    return { payment: null, error: "Could not reach the gateway API. Is the backend running?" };
  }
}

export default async function PaymentDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const paymentId = Number(id);
  if (!Number.isInteger(paymentId) || paymentId <= 0) {
    notFound();
  }

  const { payment, error } = await loadPayment(paymentId);

  if (!payment) {
    return (
      <section aria-labelledby="payment-heading" className="flex flex-col gap-4">
        <h1 id="payment-heading" className="font-heading text-xl font-semibold">
          Payment #{id}
        </h1>
        {error ? (
          <p role="alert" className="bg-rust-wash px-3 py-2 text-sm text-rust">
            The gateway returned: {error}
          </p>
        ) : (
          <p className="text-sm text-ink-muted">
            No payment with that id exists. Check the number or {" "}
            <Link href="/payments" className="text-ledger underline underline-offset-4">
              go back to the list
            </Link>
            .
          </p>
        )}
      </section>
    );
  }

  return (
    <section aria-labelledby="payment-heading" className="flex flex-col gap-6">
      <div>
        <h1 id="payment-heading" className="font-heading text-xl font-semibold">
          Payment <MonoCell>#{payment.id}</MonoCell>
        </h1>
        <p className="text-sm text-ink-muted">{payment.description}</p>
      </div>

      <dl className="grid grid-cols-2 gap-x-8 gap-y-2 text-sm sm:grid-cols-4">
        <div>
          <dt className="text-ink-muted">Amount</dt>
          <dd>
            <MonoCell>
              {payment.amount} {payment.currency}
            </MonoCell>
          </dd>
        </div>
        <div>
          <dt className="text-ink-muted">Status</dt>
          <dd>
            <StatusBadge status={payment.status} />
          </dd>
        </div>
        <div>
          <dt className="text-ink-muted">Net captured (USD)</dt>
          <dd>
            <MonoCell>{payment.capturedAmountInUsd}</MonoCell>
          </dd>
        </div>
        <div>
          <dt className="text-ink-muted">Created</dt>
          <dd>
            <UtcTimestamp value={payment.createdAt} />
          </dd>
        </div>
      </dl>

      <LedgerTable
        caption={`Transaction ledger for payment ${payment.id}, oldest first, amounts exact`}
        head={["Entry", "Type", "Amount", "Recorded"]}
      >
        {payment.transactions.map((transaction, index) => (
          <LedgerRow key={transaction.id} index={index}>
            <td className="px-3 py-2">
              <MonoCell>{index + 1}</MonoCell>
            </td>
            <td className="px-3 py-2">
              {transaction.type}
              {transaction.status === "FAILED" ? (
                <span className="ml-2 text-xs text-rust">declined</span>
              ) : null}
            </td>
            <td className="px-3 py-2">
              <MonoCell>{transaction.amount}</MonoCell>
            </td>
            <td className="px-3 py-2">
              <UtcTimestamp value={transaction.recordedAt} />
            </td>
          </LedgerRow>
        ))}
      </LedgerTable>
    </section>
  );
}
