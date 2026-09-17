import type { Metadata } from "next";
import { ApiError, listMerchants, type Merchant } from "@/lib/api";
import { LedgerRow, LedgerTable, MonoCell, UtcTimestamp } from "@/components/LedgerTable";
import { StatusBadge } from "@/components/StatusBadge";
import { RegisterMerchantForm } from "@/components/RegisterMerchantForm";

export const metadata: Metadata = {
  title: "Merchants",
};

async function loadMerchants(): Promise<{ merchants: Merchant[]; error: string | null }> {
  try {
    return { merchants: await listMerchants(), error: null };
  } catch (error) {
    if (error instanceof ApiError) {
      return { merchants: [], error: error.message };
    }
    return { merchants: [], error: "Could not reach the gateway API. Is the backend running?" };
  }
}

export default async function MerchantsPage() {
  const { merchants, error } = await loadMerchants();

  return (
    <section aria-labelledby="merchants-heading" className="flex flex-col gap-6">
      <h1 id="merchants-heading" className="font-heading text-xl font-semibold">
        Merchants
      </h1>

      <RegisterMerchantForm />

      {error ? (
        <p role="alert" className="bg-rust-wash px-3 py-2 text-sm text-rust">
          The gateway returned: {error}
        </p>
      ) : merchants.length === 0 ? (
        <p className="text-sm text-ink-muted">
          No merchants registered yet. Register one above; its first payment can follow right after.
        </p>
      ) : (
        <LedgerTable
          caption="All merchants, newest last, with status and API key"
          head={["Merchant", "Email", "Status", "API key", "Registered"]}
        >
          {merchants.map((merchant, index) => (
            <LedgerRow key={merchant.id} index={index}>
              <td className="px-3 py-2 font-medium">{merchant.businessName}</td>
              <td className="px-3 py-2">{merchant.email}</td>
              <td className="px-3 py-2">
                <StatusBadge status={merchant.status} />
              </td>
              <td className="px-3 py-2">
                <MonoCell>{merchant.apiKey}</MonoCell>
              </td>
              <td className="px-3 py-2">
                <UtcTimestamp value={merchant.createdAt} />
              </td>
            </LedgerRow>
          ))}
        </LedgerTable>
      )}
    </section>
  );
}
