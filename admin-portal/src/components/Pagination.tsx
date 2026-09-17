import Link from "next/link";

/**
 * Prev/next pager for list screens, driven entirely by the backend's page
 * envelope (TASK-07). Server-rendered links: no client JS, and edge pages
 * render their link disabled rather than hidden so the control keeps its
 * shape. Hidden entirely when there is nothing to page through.
 */
export function Pagination({
  page,
  totalPages,
  totalElements,
  label,
  hrefFor,
}: {
  page: number;
  totalPages: number;
  totalElements: number;
  /** Unit noun for the indicator, e.g. "merchants" or "payments". */
  label: string;
  /** Builds the link target for a page number, preserving filters. */
  hrefFor: (page: number) => string;
}) {
  if (totalElements === 0) {
    return null;
  }
  const hasPrev = page > 0;
  const hasNext = page < totalPages - 1;
  // An out-of-range page (stale bookmark, manual URL) recovers in one
  // click: Previous lands on the last real page, not page - 1.
  const prevTarget = Math.min(page - 1, totalPages - 1);
  const linkClass =
    "min-h-[44px] border px-4 py-2 text-sm font-semibold focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ledger";
  const enabled = "border-ledger text-ledger hover:bg-ledger-wash";
  const disabled = "border-rule text-ink-muted pointer-events-none";

  return (
    <nav aria-label="Pagination" className="flex items-center justify-between">
      {hasPrev ? (
        <Link href={hrefFor(prevTarget)} className={`${linkClass} ${enabled}`}>
          ← Previous
        </Link>
      ) : (
        <span aria-disabled className={`${linkClass} ${disabled}`}>
          ← Previous
        </span>
      )}
      <p className="text-sm text-ink-muted">
        Page {page + 1} of {totalPages} · {totalElements} {label}
      </p>
      {hasNext ? (
        <Link href={hrefFor(page + 1)} className={`${linkClass} ${enabled}`}>
          Next →
        </Link>
      ) : (
        <span aria-disabled className={`${linkClass} ${disabled}`}>
          Next →
        </span>
      )}
    </nav>
  );
}
