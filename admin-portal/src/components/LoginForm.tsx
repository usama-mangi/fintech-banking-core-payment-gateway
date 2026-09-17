"use client";

import { useActionState } from "react";
import { loginAction, type FormState } from "@/app/actions";

export function LoginForm({ next }: { next?: string }) {
  const [state, formAction, pending] = useActionState<FormState | null, FormData>(
    loginAction,
    null
  );

  return (
    <form action={formAction} className="flex flex-col gap-4">
      <input type="hidden" name="next" value={next ?? ""} />
      <label className="flex flex-col gap-1 text-sm">
        Passphrase
        <input
          name="passphrase"
          type="password"
          required
          autoFocus
          className="border border-rule bg-white px-3 py-2 text-sm focus:border-ledger focus:outline-none"
        />
        <span className="text-xs text-ink-muted">
          Sessions last 12 hours; rotating the passphrase signs everyone out.
        </span>
      </label>
      <button
        type="submit"
        disabled={pending}
        className="min-h-[44px] bg-ledger px-5 py-2 text-sm font-semibold text-white hover:bg-ledger/90 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ledger disabled:opacity-60"
      >
        {pending ? "Checking…" : "Sign in"}
      </button>
      {state?.error ? (
        <p role="alert" className="bg-rust-wash px-3 py-2 text-sm text-rust">
          {state.error}
        </p>
      ) : null}
    </form>
  );
}
