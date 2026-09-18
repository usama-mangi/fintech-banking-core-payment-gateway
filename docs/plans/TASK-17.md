# Plan: TASK-17 — Merchant portal

## Objective

Merchants today have raw API access plus the backend's server-rendered `/dashboard`.
This task gives them a real portal: a standalone Next.js app (`merchant-portal/`)
where a merchant signs in with their API key and manages payments end to end —
view their ledger, create payments, record transactions. The backend already
provides everything needed (TASK-14's ownership scoping means a merchant key
only ever sees its own data).

## Design

- **New app** `merchant-portal/` — Next.js 15 App Router, React 19, Tailwind 4,
  mirroring the admin portal's structure but with its own visual voice
  (merchant-facing, not ops).
- **Auth = the merchant's API key.** Sign-in form posts the key to a route
  handler that validates it once against the backend (`GET /api/payments` with
  the key), then stores it in an HMAC-signed httpOnly session cookie
  (`merchant_session`, 12 h) — same pattern as the admin portal's passphrase
  cookie, but keyed per merchant. The raw key never reaches the browser.
- **Gate**: middleware redirects every page to `/login?next=…` without a valid
  session. Sign-out clears the cookie.
- **Pages**: dashboard (totals + recent payments), payments list with status
  filter, payment detail (chronological ledger), create-payment form (server
  action), record-transaction form on the detail page (server action).
- **API client**: typed fetch wrapper using the merchant's own key from the
  session cookie server-side; errors rendered via the backend error contract.

## Steps

1. Scaffold `merchant-portal/` (package.json, tsconfig, tailwind, next config,
   app shell) — reuse admin-portal's tooling choices.
2. `lib/api.ts` (typed client, per-request key), `lib/session.ts` (signed
   cookie), `middleware.ts` (gate).
3. `/login` page + route handler; `/logout` POST.
4. `/` dashboard, `/payments` list + filter, `/payments/[id]` detail with
   transaction form.
5. Server actions: create payment (idempotency key generated server-side),
   record AUTHORIZATION / CAPTURE / REFUND / FEE.
6. Docs: merchant-portal README, system-design actor surface, handoff.
7. Verify: build, live walkthrough — sign in with a real merchant key, create
   payment, authorize, capture, refund path, wrong key rejected, gate holds.

## DoD

- Portal builds; middleware redirects unauthenticated requests.
- Full payment lifecycle drivable from the UI against the live backend.
- Wrong key shows an inline error; session cookie is signed + httpOnly.
- Docs updated; walkthrough evidenced.
