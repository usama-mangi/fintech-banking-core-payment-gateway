# Ledger — admin portal

The internal operations surface for the payment gateway. Staff trace a payment's full
transaction ledger and check merchant standing; every amount is read live from the
gateway database, never cached or summarized. Staff views sit behind a shared
passphrase sign-in; sessions are signed cookies that last 12 hours.

## Stack

Next.js 15 (App Router), React 19, Tailwind 4. Design tokens and component rules live in
`design.yaml` at the repo root (field-notes/ledger direction).

## Run it

The portal needs the backend running (see `backend/`). Both values below are read at
server start, so restart `npm run dev` after changing them.

```bash
PAYMENT_API_BASE=http://localhost:8080   # backend base URL, defaults to this
PAYMENT_API_KEY=sk_...                   # a merchant API key; payment pages 401 without it
PORTAL_PASSCODE=...                      # staff passphrase; sign-in fails closed without it
npm run dev
```

The API key is any ACTIVE merchant's key from `GET /api/merchants` and authorizes the
portal's calls to the gateway. The passphrase is for people: every staff view redirects
to `/login` without a valid session, and rotating `PORTAL_PASSCODE` signs out every
existing session on restart because cookie signatures stop verifying.## Routes

- `/login` — passphrase sign-in; the only page reachable without a session.
- `/merchants` — all merchants, plus the registration form and per-row lifecycle actions
  (suspend/reactivate/close, contextual to status). New keys appear in the table. Paginated
  with `?page=N` (20 per page).
- `/payments` — all payments, filterable by lifecycle status; the filter composes with
  `?page=N` pagination.
- `/payments/[id]` — one payment's chronological ledger with exact amounts and UTC
timestamps.
- `/fees` — fee revenue report: totals per merchant and per currency, exact decimals,
no indicative USD conversion. Uses the backend's internal API key tier.

> Note: since the internal API key tier landed in the backend, `PAYMENT_API_KEY` above
> should be the *internal* key (`INTERNAL_API_KEY` on the backend) rather than a
> merchant key — the fees page is internal-only and a merchant key will 403 on it.

## Checks

```bash
npm run lint
npm run build
```
