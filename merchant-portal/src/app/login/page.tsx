import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { currentSession } from "@/lib/session";
import { SignInForm } from "@/components/SignInForm";

export const metadata: Metadata = {
  title: "Sign in",
};

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ next?: string }>;
}) {
  // Already signed in: straight to the dashboard.
  if (await currentSession()) {
    redirect("/");
  }
  const { next } = await searchParams;

	return (
		<main className="flex min-h-screen items-center justify-center px-6">
			<div className="w-full max-w-sm border border-rule bg-white p-8">
				<h1 className="font-heading text-lg font-semibold">Merchant Portal</h1>
				<p className="mt-1 mb-6 text-sm text-ink-muted">
					Sign in with the API key issued when your business registered with Ledger.
				</p>
				<SignInForm next={next ?? "/"} />
			</div>
		</main>
	);
}
