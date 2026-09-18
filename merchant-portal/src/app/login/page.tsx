import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { currentSession } from "@/lib/session";

export const metadata: Metadata = {
	title: "Sign in",
};

const ERROR_MESSAGES: Record<string, string> = {
	empty: "Enter the API key issued when your business registered.",
	invalid: "That API key was not recognized. Check it and try again.",
	locked: "This key is locked out — your merchant account is suspended or closed.",
	unreachable: "Could not reach the gateway API. Is the backend running?",
};

export default async function LoginPage({
	searchParams,
}: {
	searchParams: Promise<{ next?: string; error?: string }>;
}) {
	// Already signed in: straight to the dashboard.
	if (await currentSession()) {
		redirect("/");
	}
	const { next, error } = await searchParams;

	return (
		<main className="flex min-h-screen items-center justify-center px-6">
			<div className="w-full max-w-sm border border-rule bg-white p-8">
				<h1 className="font-heading text-lg font-semibold">Merchant Portal</h1>
				<p className="mt-1 mb-6 text-sm text-ink-muted">
					Sign in with the API key issued when your business registered with Ledger.
				</p>
				<form action="/api/sign-in" method="post" className="flex flex-col gap-4">
					<input type="hidden" name="next" value={next ?? "/"} />
					<label className="flex flex-col gap-1 text-sm">
						API key
						<input
							name="apiKey"
							type="password"
							required
							autoFocus
							placeholder="sk_..."
							className="min-h-[44px] border border-rule bg-white px-3 py-2 font-ledger text-sm focus:border-accent focus:outline-none"
						/>
					</label>
					{error ? (
						<p role="alert" className="bg-rust-wash px-3 py-2 text-sm text-rust">
							{ERROR_MESSAGES[error] ?? "Sign-in failed. Try again."}
						</p>
					) : null}
					<button
						type="submit"
						className="min-h-[44px] bg-accent px-4 py-2 text-sm font-semibold text-white hover:opacity-90 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
					>
						Sign in
					</button>
				</form>
			</div>
		</main>
	);
}
