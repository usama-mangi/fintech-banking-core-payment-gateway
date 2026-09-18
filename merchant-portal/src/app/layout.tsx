import type { Metadata } from "next";
import { Libre_Franklin, Source_Sans_3, IBM_Plex_Mono } from "next/font/google";
import "./globals.css";

const headingFont = Libre_Franklin({
  subsets: ["latin"],
  variable: "--font-libre-franklin",
});

const bodyFont = Source_Sans_3({
  subsets: ["latin"],
  variable: "--font-source-sans",
});

const monoFont = IBM_Plex_Mono({
  subsets: ["latin"],
  weight: ["400", "500"],
  variable: "--font-plex-mono",
});

export const metadata: Metadata = {
	title: "Merchant Portal — Ledger",
};

export default function RootLayout({
	children,
}: Readonly<{
	children: React.ReactNode;
}>) {
	return (
		<html lang="en">
			<body className={`${headingFont.variable} ${bodyFont.variable} ${monoFont.variable}`}>
				{children}
			</body>
		</html>
	);
}
