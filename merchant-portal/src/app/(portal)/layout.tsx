import type { Metadata } from "next";
import Link from "next/link";
import { headers } from "next/headers";
import { redirect } from "next/navigation";
import { currentSession } from "@/lib/session";
import { signOutAction } from "@/app/actions";

export const metadata: Metadata = {};

export default async function PortalLayout({
	children,
}: Readonly<{
	children: React.ReactNode;
}>) {
	const session = await currentSession();
	if (!session) {
		const path = (await headers()).get("x-portal-path") ?? "/";
		redirect(`/login?next=${encodeURIComponent(path)}`);
	}

	return (
		<>
			<header className="border-b border-rule">
				<div className="mx-auto flex max-w-5xl items-baseline justify-between px-6 py-4">
					<Link href="/" className="font-heading text-lg font-semibold tracking-tight">
						Ledger <span className="text-ink-muted font-normal text-sm">— Merchant Portal</span>
					</Link>
					<nav aria-label="Sections" className="flex items-center gap-6 text-sm">
						<Link href="/" className="hover:text-accent hover:underline underline-offset-4">
							Overview
						</Link>
						<Link href="/payments" className="hover:text-accent hover:underline underline-offset-4">
							Payments
						</Link>
						<form action={signOutAction}>
							<button
								type="submit"
								className="border border-rule px-3 py-1.5 text-sm text-ink-muted hover:border-accent hover:text-accent focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
							>
								Sign out
							</button>
						</form>
					</nav>
				</div>
			</header>
			<main className="mx-auto max-w-5xl px-6 py-8">{children}</main>
			<footer className="mx-auto max-w-5xl px-6 pb-10 text-xs text-ink-muted">
				Your data only — this portal is scoped to your merchant account. Timestamps are UTC.
			</footer>
		</>
	);
}
