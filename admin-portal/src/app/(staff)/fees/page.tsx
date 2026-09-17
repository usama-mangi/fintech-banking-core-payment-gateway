import type { Metadata } from "next";
import { ApiError, getFeeReport, type FeeReport } from "@/lib/api";
import { LedgerRow, LedgerTable, MonoCell } from "@/components/LedgerTable";

export const metadata: Metadata = {
  title: "Fees",
};

async function loadFees(): Promise<{ report: FeeReport | null; error: string | null }> {
  try {
    return { report: await getFeeReport(), error: null };
  } catch (error) {
    if (error instanceof ApiError) {
      return { report: null, error: error.message };
    }
    return { report: null, error: "Could not reach the gateway API. Is the backend running?" };
  }
}

export default async function FeesPage() {
  const { report, error } = await loadFees();

  return (
    <section aria-labelledby="fees-heading" className="flex flex-col gap-6">
      <h1 id="fees-heading" className="font-heading text-xl font-semibold">
        Fees
      </h1>

      {error ? (
        <p role="alert" className="bg-rust-wash px-3 py-2 text-sm text-rust">
          The gateway returned: {error}
        </p>
      ) : report && report.merchants.length > 0 ? (
        <>
          <LedgerTable
            caption="Gateway fees summed per merchant and currency, exact decimals, no indicative conversion"
            head={["Merchant", "Currency", "Total fees", "Fee entries"]}
          >
            {report.merchants.map((row, index) => (
              <LedgerRow key={`${row.merchantId}-${row.currency}`} index={index}>
                <td className="px-3 py-2">{row.businessName}</td>
                <td className="px-3 py-2">
                  <MonoCell>{row.currency}</MonoCell>
                </td>
                <td className="px-3 py-2">
                  <MonoCell>{row.totalFees}</MonoCell>
                </td>
                <td className="px-3 py-2">
                  <MonoCell>{row.feeCount}</MonoCell>
                </td>
              </LedgerRow>
            ))}
          </LedgerTable>

          <h2 className="font-heading text-base font-semibold">Totals by currency</h2>
          <LedgerTable
            caption="Fee totals across all merchants per currency"
            head={["Currency", "Total fees", "Fee entries"]}
          >
            {report.totals.map((total, index) => (
              <LedgerRow key={total.currency} index={index}>
                <td className="px-3 py-2">
                  <MonoCell>{total.currency}</MonoCell>
                </td>
                <td className="px-3 py-2">
                  <MonoCell>{total.totalFees}</MonoCell>
                </td>
                <td className="px-3 py-2">
                  <MonoCell>{total.feeCount}</MonoCell>
                </td>
              </LedgerRow>
            ))}
          </LedgerTable>
        </>
      ) : (
        <p className="text-sm text-ink-muted">
          No fees recorded yet. FEE entries appear here once merchants are charged against authorized payments.
        </p>
      )}
    </section>
  );
}
