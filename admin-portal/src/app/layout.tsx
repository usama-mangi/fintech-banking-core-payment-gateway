import type { Metadata } from "next";
import { Libre_Franklin, Source_Sans_3, IBM_Plex_Mono } from "next/font/google";
import Link from "next/link";
import "./globals.css";

const libreFranklin = Libre_Franklin({
  variable: "--font-libre-franklin",
  subsets: ["latin"],
});

const sourceSans = Source_Sans_3({
  variable: "--font-source-sans",
  subsets: ["latin"],
});

const plexMono = IBM_Plex_Mono({
  variable: "--font-plex-mono",
  weight: ["400", "500"],
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: {
    default: "Ledger — payment operations",
    template: "%s — Ledger",
  },
  description:
    "Trace payments and merchant standing straight from the gateway ledger.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body
        className={`${libreFranklin.variable} ${sourceSans.variable} ${plexMono.variable} antialiased min-h-screen`}
      >
        <header className="border-b border-rule">
          <div className="mx-auto flex max-w-6xl items-baseline justify-between px-6 py-4">
            <Link href="/merchants" className="font-heading text-lg font-semibold tracking-tight">
              Ledger
            </Link>
            <nav aria-label="Sections" className="flex gap-6 text-sm">
              <Link href="/merchants" className="hover:text-ledger hover:underline underline-offset-4">
                Merchants
              </Link>
              <Link href="/payments" className="hover:text-ledger hover:underline underline-offset-4">
                Payments
              </Link>
            </nav>
          </div>
        </header>
        <main className="mx-auto max-w-6xl px-6 py-8">{children}</main>
        <footer className="mx-auto max-w-6xl px-6 pb-10 text-xs text-ink-muted">
          Amounts read from the gateway database. Timestamps are UTC.
        </footer>
      </body>
    </html>
  );
}
