# Ledger — merchant portal

The merchant-facing surface for the payment gateway. A merchant signs in with
the API key issued at registration and manages their payments end to end:
create, authorize, capture, refund, fees. The portal is scoped to the signed-in
merchant's own data — the gateway enforces this (ownership rules on the API
key); the portal never sees another merchant's rows.

## Stack

Next.js 15 (App Router), React 19, Tailwind 4. Same field-notes/ledger design
family as the admin portal, shifted warmer (teal accent) for the merchant's
own books.

## Run it

The portal needs the backend running (see `backend/`).

```bash
PAYMENT_API_BASE=http://localhost:8080        # backend base URL (default)
MERCHANT_SESSION_SECRET=<long-random-string>  # seals session cookies; REQUIRED in prod
PORT=3200
npm run dev        # or: npm run build && npm start
```

## How sign-in works

1. Merchant submits their API key (`sk_...`) at `/login` — a plain form POST to
   `/api/sign-in` (a route handler; works without JavaScript).
2. The handler validates the key once against the gateway.
3. The key is sealed into an httpOnly session cookie with AES-256-GCM
   (`MERCHANT_SESSION_SECRET`); the raw key never reaches the browser and the
   cookie is unreadable without the secret. Sessions last 12 hours.
4. Every page request passes a middleware shape-gate, then the layout unseals
   the cookie. Rotating `MERCHANT_SESSION_SECRET` invalidates every session.

Because the session is self-contained (no server-side store), sign-in works
across multiple instances — relevant on platforms like Render.

## Routes

- `/login` — API-key sign-in; the only page reachable without a session.
- `/` — overview: captured/refunded totals, create-payment form, recent payments.
- `/payments` — the merchant's payments, filterable by lifecycle status.
- `/payments/[id]` — chronological ledger plus a form to record
  AUTHORIZATION / CAPTURE / REFUND / FEE entries. Illegal transitions are
  rejected by the gateway and surfaced inline.

## Env vars

| Variable | Required | Purpose |
|---|---|---|
| `PAYMENT_API_BASE` | no (default `http://localhost:8080`) | Backend base URL |
| `MERCHANT_SESSION_SECRET` | **yes in prod** | AES key for session cookies; sign-in fails closed to a dev default if unset |

## Checks

```bash
npm run lint
npm run build
```
