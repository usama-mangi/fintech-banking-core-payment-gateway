const STATUS_STYLES: Record<string, string> = {
	CAPTURED: "bg-accent-wash text-accent",
	AUTHORIZED: "bg-amber-wash text-amber",
	REQUIRES_PAYMENT: "bg-stamp-wash text-stamp",
	REFUNDED: "bg-stamp-wash text-stamp",
	FAILED: "bg-rust-wash text-rust",
	DECLINED: "bg-rust-wash text-rust",
	CANCELLED: "bg-rust-wash text-rust",
};

export function StatusBadge({ status }: { status: string }) {
	const style = STATUS_STYLES[status] ?? "bg-rule/40 text-ink-muted";
	return (
		<span
			className={`inline-block border border-current px-2 py-0.5 font-ledger text-xs tracking-wider ${style}`}
		>
			{status}
		</span>
	);
}
