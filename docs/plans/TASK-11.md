# Plan: TASK-11 — Merchant lifecycle endpoints (suspend / reactivate / close)

## Objective

`MerchantStatus` has four values and the auth filter already 403s non-ACTIVE merchants, but nothing can ever change a status after registration: every merchant stays ACTIVE forever. This task moves status transitions into the `Merchant` domain entity (where they belong per the non-negotiables), exposes three lifecycle endpoints, and wires action buttons into the portal's merchants view.

## Design decisions

- **Transition map** (owned by `Merchant`, mirroring `Payment.record()` style — illegal transitions throw `IllegalStateException` → 409):
  - `suspend()`: ACTIVE → SUSPENDED
  - `reactivate()`: SUSPENDED → ACTIVE
  - `close()`: ACTIVE, SUSPENDED, or PENDING → CLOSED (terminal; closes a never-onboarded account too)
  - `activate()`: PENDING → ACTIVE (domain-complete; see below)
- **No double-transition idempotency**: suspending a suspended merchant returns 409, consistent with the payments lifecycle — an accidental double action signals a race an ops surface should surface, not swallow.
- **`PENDING` today is unreachable**: registration creates ACTIVE directly, so an activate endpoint would be dead API. Decision: `activate()` exists in the domain (covered by a domain unit test) so the model is complete, but only suspend/reactivate/close are exposed over HTTP. The revisit trigger is a merchant approval flow.
- **Public `setStatus` removed** (package-private for JPA); the one test caller moves to the domain methods. Status can no longer change outside the transition rules.
- **Endpoints**: `POST /api/merchants/{id}/suspend`, `/reactivate`, `/close` → 200 `MerchantResponse`; 404 unknown id; 409 illegal transition. Dedicated endpoints (not a PATCH-with-action) so each maps to exactly one domain method.
- **Authz**: none beyond the existing model — the merchants surface is the ops surface (TASK-02 decision), and the portal gating it is TASK-09. Consistent; system-design already names per-user accounts as the revisit trigger.
- **Portal**: per-row contextual actions in the merchants table (ACTIVE → Suspend/Close, SUSPENDED → Reactivate/Close, CLOSED → none) via a server action calling the backend, then revalidate. Live enforcement is already proven end-to-end by the filter's 403.

## Steps

1. `Merchant` — four transition methods, status setter package-private.
2. `MerchantLifecycleTest` (domain unit test): full legal/illegal transition table including `activate()`.
3. `MerchantService.suspend/reactivate/close` + `MerchantController` endpoints.
4. `MerchantApiTests` additions: suspend → 200 SUSPENDED and their payment POST → 403; reactivate → 200 ACTIVE and payments work again; close → 200 CLOSED and suspend/reactivate/close on it → 409; suspend on unknown id → 404.
5. Portal: `merchantLifecycleAction` in `actions.ts`, `MerchantActions.tsx` client component, Actions column in the merchants table; `design.yaml` MerchantsOverview update + log; intent gates re-run.
6. Docs: `docs/system-design.md` flows + access note, README routes line, handoff suggestion list.
7. Verify: full backend suite green, portal build green, walkthrough through the UI (register → suspend → payment 403 → reactivate → close).
8. Review scan, commit, record-progress.

## DoD

- New domain and API tests fail on the unmodified codebase; full suite green after.
- Suspended merchants are locked out of the payment API immediately (E2E through the filter); reactivation restores access.
- CLOSED is terminal and enforced with 409 everywhere.
- Portal can perform all three actions against a live backend; build + gates green.
