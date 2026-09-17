import type { Metadata } from "next";
import { headers } from "next/headers";
import Link from "next/link";
import { redirect } from "next/navigation";
import { isAuthenticated } from "@/lib/session";

export const metadata: Metadata = {};

export default async function StaffLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const authed = await isAuthenticated();
  if (!authed) {
    const path = (await headers()).get("x-portal-path") ?? "/merchants";
    redirect(`/login?next=${encodeURIComponent(path)}`);
  }

  return (
    <>
      <header className="border-b border-rule">
        <div className="mx-auto flex max-w-6xl items-baseline justify-between px-6 py-4">
          <Link href="/merchants" className="font-heading text-lg font-semibold tracking-tight">
            Ledger
          </Link>
          <nav aria-label="Sections" className="flex items-center gap-6 text-sm">
            <Link href="/merchants" className="hover:text-ledger hover:underline underline-offset-4">
              Merchants
            </Link>
            <Link href="/payments" className="hover:text-ledger hover:underline underline-offset-4">
              Payments
            </Link>
            <Link href="/fees" className="hover:text-ledger hover:underline underline-offset-4">
              Fees
            </Link>
            <form action="/logout" method="post">
              <button
                type="submit"
                className="border border-rule px-3 py-1.5 text-sm text-ink-muted hover:border-ledger hover:text-ledger focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ledger"
              >
                Sign out
              </button>
            </form>
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-6 py-8">{children}</main>
      <footer className="mx-auto max-w-6xl px-6 pb-10 text-xs text-ink-muted">
        Amounts read from the gateway database. Timestamps are UTC.
      </footer>
    </>
  );
}
