/**
 * Status badge per design.yaml: wash + fg token pair per status family,
 * status text always printed, never color-only.
 */
const STATUS_STYLES: Record<string, { wash: string; fg: string }> = {
  ok: { wash: "bg-ledger-wash", fg: "text-ledger" },
  bad: { wash: "bg-rust-wash", fg: "text-rust" },
  info: { wash: "bg-stamp-wash", fg: "text-stamp" },
};

const STATUS_FAMILY: Record<string, keyof typeof STATUS_STYLES> = {
  SUCCEEDED: "ok",
  CAPTURED: "ok",
  AUTHORIZED: "info",
  REQUIRES_PAYMENT: "info",
  ACTIVE: "ok",
  PENDING: "info",
  SUSPENDED: "bad",
  CLOSED: "bad",
  FAILED: "bad",
  CANCELLED: "bad",
  REFUNDED: "bad",
  CHARGEBACK: "bad",
};

export function StatusBadge({ status }: { status: string }) {
  const family = STATUS_FAMILY[status] ?? "info";
  const style = STATUS_STYLES[family];
  return (
    <span
      className={`inline-flex items-center px-2 py-0.5 font-ledger text-xs ${style.wash} ${style.fg}`}
    >
      {status}
    </span>
  );
}
