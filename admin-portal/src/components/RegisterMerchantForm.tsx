"use client";

import { useActionState } from "react";
import { registerMerchantAction, type FormState } from "@/app/actions";

export function RegisterMerchantForm() {
  const [state, formAction, pending] = useActionState<FormState | null, FormData>(
    registerMerchantAction,
    null
  );

  return (
    <form action={formAction} className="flex flex-wrap items-end gap-3">
      <label className="flex flex-col gap-1 text-sm">
        Business name
        <input
          name="businessName"
          required
          maxLength={255}
          className="w-56 border border-rule bg-white px-3 py-2 text-sm focus:border-ledger focus:outline-none"
        />
      </label>
      <label className="flex flex-col gap-1 text-sm">
        Email
        <input
          name="email"
          type="email"
          required
          maxLength={255}
          className="w-64 border border-rule bg-white px-3 py-2 text-sm focus:border-ledger focus:outline-none"
        />
      </label>
      <button
        type="submit"
        disabled={pending}
        className="min-h-[44px] bg-ledger px-5 py-2 text-sm font-semibold text-white hover:bg-ledger/90 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ledger disabled:opacity-60"
      >
        {pending ? "Registering…" : "Register merchant"}
      </button>
      {state?.error ? (
        <p role="alert" className="basis-full bg-rust-wash px-3 py-2 text-sm text-rust">
          {state.error}
          {state.ok === false ? null : null}
        </p>
        ) : state?.ok ? (
          <p role="status" className="basis-full bg-ledger-wash px-3 py-2 text-sm text-ledger">
            Merchant registered. Its API key appears in the table below; copy it now, it is shown once per row.
          </p>
        ) : null}
    </form>
  );
}
