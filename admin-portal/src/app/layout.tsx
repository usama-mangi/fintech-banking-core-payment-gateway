import type { Metadata } from "next";
import { Libre_Franklin, Source_Sans_3, IBM_Plex_Mono } from "next/font/google";
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
  variable: "--font-ibm-plex-mono",
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
        {children}
      </body>
    </html>
  );
}
