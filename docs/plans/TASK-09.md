# Plan: TASK-09 — Admin portal authentication

## Objective

The portal's staff views currently trust the network: anyone who can reach port 3100 reads every merchant, API key and ledger row. This task puts a sign-in gate in front of all staff views, keeping the operations surface behind credentials while the backend keeps serving merchant API clients as before.

## Design decisions

- **Model**: one shared staff passphrase from `PORTAL_PASSCODE` (env, server-side only). No user table, no roles — matches the MVP single-tenant ops reality and the backend's own static-key model. Rotation is an env change + restart.
- **Session**: HMAC-signed cookie `ledger_session` (`<expiryMs>.<hmac>`), HMAC-SHA256 keyed with SHA-256 of the passphrase. Stateless (no server session store), passes inherently when the passphrase rotates, 12-hour expiry, `httpOnly`, `sameSite=lax`, `secure` outside dev. Verification is timing-safe (`timingSafeEqual` on recomputed digests).
- **Guard**: route-group layout `app/(staff)/layout.tsx` checks the session server-side before rendering any staff page and redirects to `/login?next=<path>` when absent/expired. Header nav and footer move into this layout; the login route gets a minimal standalone shell.
- **Login**: server action `loginAction` compares the passphrase (timing-safe), sets the cookie, and redirects to `next` (only same-root paths honored). Wrong passphrase re-renders with a plain error. `logoutAction` clears the cookie.
- **Not in scope**: per-user accounts, rate limiting (single internal surface; revisit with per-user model), CSRF tokens beyond SameSite=Lax + POST-only actions.

## Steps

1. `src/lib/session.ts` — sign/verify/cookie helpers (node crypto, no new deps).
2. `src/app/actions.ts` — `loginAction`, `logoutAction` alongside the existing register action.
3. `app/(staff)/` route group: move `merchants/`, `payments/`, `payments/[id]/`; new `(staff)/layout.tsx` guard + header + sign-out.
4. `app/login/page.tsx` + `components/LoginForm.tsx`; strip header/footer from root `layout.tsx`.
5. Docs: README (PORTAL_PASSCODE, sign-in flow), `docs/system-design.md` access-model line, `design.yaml` (LoginScreen, log), `docs/handoff.md` soft-edge note.
6. Verify: `npm run build` green; live walkthrough — unauthenticated `/merchants` redirects, wrong passphrase errors, correct passphrase renders merchants and payments, sign-out returns to login.
7. Review scan, commit, record-progress.

## DoD

- Every staff route redirects to `/login` without a valid session; `curl` of `/merchants` cannot see ledger data.
- Signed cookie fails closed on tampering and expiry.
- Build + lint green; walkthrough evidenced end-to-end with the backend running.
- design.yaml intent gates re-run (all four pass) after the new screen.
