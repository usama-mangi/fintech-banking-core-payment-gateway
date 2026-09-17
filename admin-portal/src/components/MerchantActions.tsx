"use client";

import { useActionState } from "react";
import { merchantLifecycleAction, type FormState } from "@/app/actions";

/** Actions a given status may take; CLOSED is terminal. */
const ACTIONS_BY_STATUS: Record<string, { action: "suspend" | "reactivate" | "close"; label: string }[]> = {
  ACTIVE: [
    { action: "suspend", label: "Suspend" },
    { action: "close", label: "Close" },
  ],
  SUSPENDED: [
    { action: "reactivate", label: "Reactivate" },
    { action: "close", label: "Close" },
  ],
  PENDING: [{ action: "close", label: "Close" }],
  CLOSED: [],
};

export function MerchantActions({ merchantId, status }: { merchantId: number; status: string }) {
  const [state, formAction, pending] = useActionState<FormState | null, FormData>(
    merchantLifecycleAction,
    null
  );
  const actions = ACTIONS_BY_STATUS[status] ?? [];

  return (
    <div className="flex flex-col gap-1">
      <div className="flex gap-2">
        {actions.length === 0 ? (
          <span className="text-xs text-ink-muted">—</span>
        ) : (
          actions.map(({ action, label }) => (
            <form action={formAction} key={action}>
              <input type="hidden" name="merchantId" value={merchantId} />
              <input type="hidden" name="action" value={action} />
              <button
                type="submit"
                disabled={pending}
                className={
                  action === "close"
                    ? "min-h-[36px] border border-rust px-3 py-1 text-xs font-semibold text-rust hover:bg-rust-wash focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-rust disabled:opacity-60"
                    : "min-h-[36px] border border-ledger px-3 py-1 text-xs font-semibold text-ledger hover:bg-ledger-wash focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ledger disabled:opacity-60"
                }
              >
                {pending ? "Working…" : label}
              </button>
            </form>
          ))
        )}
      </div>
      {state?.error ? (
        <p role="alert" className="text-xs text-rust">
          {state.error}
        </p>
      ) : null}
    </div>
  );
}
