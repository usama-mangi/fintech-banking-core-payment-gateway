import type { Metadata } from "next";
import { ApiError, listMerchants, type Merchant } from "@/lib/api";
import { parsePageParam } from "@/lib/params";
import { LedgerRow, LedgerTable, MonoCell, UtcTimestamp } from "@/components/LedgerTable";
import { Pagination } from "@/components/Pagination";
import { StatusBadge } from "@/components/StatusBadge";
import { MerchantActions } from "@/components/MerchantActions";
import { RegisterMerchantForm } from "@/components/RegisterMerchantForm";

export const metadata: Metadata = {
  title: "Merchants",
};

async function loadMerchants(
  page: number
): Promise<{ merchants: Merchant[]; pageEnvelope: Awaited<ReturnType<typeof listMerchants>> | null; error: string | null }> {
  try {
    const pageEnvelope = await listMerchants(page);
    return { merchants: pageEnvelope.content, pageEnvelope, error: null };
  } catch (error) {
    if (error instanceof ApiError) {
      return { merchants: [], pageEnvelope: null, error: error.message };
    }
    return { merchants: [], pageEnvelope: null, error: "Could not reach the gateway API. Is the backend running?" };
  }
}

export default async function MerchantsPage({
  searchParams,
}: {
  searchParams: Promise<{ page?: string }>;
}) {
  const page = parsePageParam((await searchParams).page);
  const { merchants, pageEnvelope, error } = await loadMerchants(page);

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
      ) : pageEnvelope && pageEnvelope.totalElements > 0 ? (
        <>
          <LedgerTable
            caption="All merchants, newest first, with status, API key and lifecycle actions"
            head={["Merchant", "Email", "Status", "API key", "Registered", "Actions"]}
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
                <td className="px-3 py-2">
                  <MerchantActions merchantId={merchant.id} status={merchant.status} />
                </td>
              </LedgerRow>
            ))}
          </LedgerTable>
          {pageEnvelope ? (
            <Pagination
              page={pageEnvelope.page}
              totalPages={pageEnvelope.totalPages}
              totalElements={pageEnvelope.totalElements}
              label="merchants"
              hrefFor={(p) => (p > 0 ? `/merchants?page=${p}` : "/merchants")}
            />
          ) : null}
        </>
      ) : (
        <p className="text-sm text-ink-muted">
          No merchants registered yet. Register one above; its first payment can follow right after.
        </p>
      )}
    </section>
  );
}
