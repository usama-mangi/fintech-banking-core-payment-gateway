import type { ReactNode } from "react";

/**
 * Ledger table primitives: ruled rows, mono money cells, a11y headers.
 * Rows receive a stagger delay so entries settle like written lines.
 */
export function LedgerTable({
  caption,
  head,
  children,
}: {
  caption: string;
  head: string[];
  children: ReactNode;
}) {
  return (
    <table className="w-full border-collapse text-sm">
      <caption className="sr-only">{caption}</caption>
      <thead>
        <tr className="border-b border-rule text-left">
          {head.map((label) => (
            <th key={label} scope="col" className="px-3 py-2 font-heading font-semibold">
              {label}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>{children}</tbody>
    </table>
  );
}

export function LedgerRow({
  index,
  children,
}: {
  index: number;
  children: ReactNode;
}) {
  return (
    <tr
      className="ledger-row border-b border-rule/70 hover:bg-ledger-wash/40"
      style={{ animationDelay: `${Math.min(index, 12) * 40}ms` }}
    >
      {children}
    </tr>
  );
}

export function MonoCell({ children }: { children: ReactNode }) {
  return <span className="font-ledger tabular-nums">{children}</span>;
}

export function UtcTimestamp({ value }: { value: string | null }) {
  if (!value) {
    return <span>—</span>;
  }
  return (
    <time dateTime={value} className="font-ledger tabular-nums text-xs">
      {value.replace("T", " ").replace("Z", " UTC")}
    </time>
  );
}
