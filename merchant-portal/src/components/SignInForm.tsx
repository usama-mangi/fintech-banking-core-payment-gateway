"use client";

import { useActionState } from "react";
import { signInAction, type FormState } from "@/app/actions";

export function SignInForm({ next }: { next: string }) {
	const [state, formAction, pending] = useActionState<FormState | null, FormData>(
		signInAction,
		null
	);

	return (
		<form action={formAction} className="flex flex-col gap-4">
			<input type="hidden" name="next" value={next} />
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
			{state?.error ? (
				<p role="alert" className="bg-rust-wash px-3 py-2 text-sm text-rust">
					{state.error}
				</p>
			) : null}
			<button
				type="submit"
				disabled={pending}
				className="min-h-[44px] bg-accent px-4 py-2 text-sm font-semibold text-white hover:opacity-90 disabled:opacity-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
			>
				{pending ? "Signing in…" : "Sign in"}
			</button>
		</form>
	);
}
