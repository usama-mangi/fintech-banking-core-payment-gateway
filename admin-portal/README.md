# Ledger — admin portal

The internal operations surface for the payment gateway. Staff trace a payment's full
transaction ledger and check merchant standing; every amount is read live from the
gateway database, never cached or summarized.

## Stack

Next.js 15 (App Router), React 19, Tailwind 4. Design tokens and component rules live in
`design.yaml` at the repo root (field-notes/ledger direction).

## Run it

The portal needs the backend running (see `backend/`). Both values below are read at
server start, so restart `npm run dev` after changing them.

```bash
PAYMENT_API_BASE=http://localhost:8080   # backend base URL, defaults to this
PAYMENT_API_KEY=sk_...                   # a merchant API key; payment pages 401 without it
npm run dev
```

The key is any ACTIVE merchant's key from `GET /api/merchants`. Merchant pages work
without a key; payment pages need one because the gateway rejects unauthenticated calls
with 401.

## Routes

- `/merchants` — all merchants, plus the registration form. New keys appear in the table.
- `/payments` — all payments, filterable by lifecycle status.
- `/payments/[id]` — one payment's chronological ledger with exact amounts and UTC
  timestamps.

## Checks

```bash
npm run lint
npm run build
```
