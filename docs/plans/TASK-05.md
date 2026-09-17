# Plan: TASK-05 — Admin portal: connect to backend API (merchants and payments views)

## Objective

The admin portal is the untouched create-next-app template: Geist fonts, Vercel CTAs, zero product. This task replaces it with two working data views — merchants and payments — backed by the Spring Boot API, styled by the just-initialized `design.yaml` system (field-notes/ledger direction, all four intent gates already green). The portal becomes the ops surface it exists to be: staff trace a payment's full ledger and scan merchant standing.

## Dependencies

- TASK-01..04 closed (API + auth live). Design system initialized this session (`design.yaml`, intent gates PASS).

## Design decisions

- **Server Components fetch from the backend directly** (Node runtime, `PAYMENT_API_BASE` env, default `http://localhost:8080`). No client-side fetching for these views: less JS, no CORS dance in the portal, secrets-free. Payment API calls send `X-API-Key` from `PAYMENT_API_KEY` env; merchant endpoints are key-exempt.
- **Fonts**: Libre Franklin (headings), Source Sans 3 (body), IBM Plex Mono (amounts/ids/keys) via `next/font/google` — slop list (Geist/Inter) fully evicted.
- **Tokens land in Tailwind 4 `@theme`**: the primitives/semantics from design.yaml become CSS variables (`--paper`, `--ink`, `--ledger-green`, …) mapped to Tailwind color names; type + spacing follow `@theme` tokens. No raw hex in components.
- **Status badge component**: wash + fg token pairs per status family, status text always printed (never color-only), role-free (not a fake button), 6.9:1 / 5.4:1 contrast verified in design.yaml.
- **Ledger table**: rows rule-separated (1px paper-rule), amounts in mono tabular figures with 2 decimals, UTC timestamps, oldest-first ledger, `<caption>` + th scope for a11y, hover highlight on rows only.
- **Motion**: productive personality — 150ms opacity/2px translateY on detail rows, staggered 40ms per row via `animation-delay`, `prefers-reduced-motion` collapses to opacity only. Uniform-timing sibling rule respected via the stagger; no bounce, no fade carpet.
- **States**: loading = "Fetching…" row with aria-live; empty = instructive ("No payments yet for this merchant…"); error = verbatim API error body quoted in alert-rust. Delivery gate requires them all.

## Files to modify

| Action | Path | What |
|--------|------|------|
| MODIFY | `admin-portal/src/app/globals.css` | Tailwind 4 `@theme` tokens from design.yaml (colors, fonts, spacing, motion) |
| MODIFY | `admin-portal/src/app/layout.tsx` | Fonts (Libre Franklin/Source Sans 3/IBM Plex Mono), metadata, page shell |
| DELETE+CREATE | `admin-portal/src/app/page.tsx` | Redirect to `/merchants` (no marketing home) |
| CREATE | `admin-portal/src/app/merchants/page.tsx` | Merchants view: register form (server action) + table |
| CREATE | `admin-portal/src/app/payments/page.tsx` | Payments view: filter by status via searchParams + table |
| CREATE | `admin-portal/src/app/payments/[id]/page.tsx` | Payment detail: lifecycle summary + chronological ledger |
| CREATE | `admin-portal/src/lib/api.ts` | Typed backend client (fetch wrappers, ApiError carrying the error body) |
| CREATE | `admin-portal/src/components/StatusBadge.tsx` | Shared badge per design.yaml component spec |
| CREATE | `admin-portal/src/components/LedgerTable.tsx` | Shared table primitives (table, row, mono cell) |
| CREATE | `admin-portal/src/components/RegisterMerchantForm.tsx` | Client component + server action for merchant registration |
| MODIFY | `admin-portal/README.md` | Env vars and run instructions (human-facing text, unslop applied) |

## Implementation steps

1. `globals.css` tokens + `layout.tsx` fonts/shell. Verify: `npm run build` compiles.
2. `lib/api.ts` typed client + `StatusBadge` + `LedgerTable`. Verify: build compiles.
3. Merchants view + register form (server action POSTs to `/api/merchants`, revalidates). Verify: build.
4. Payments list + detail views with status filter and ledger reveal. Verify: build.
5. Delete template page; page.tsx becomes a redirect. Lint gate: `npm run lint`; Typecheck/build gate: `npm run build`.
6. Manual UI gate: boot backend dev profile + portal dev server, register a merchant, create a payment via curl with the issued key, walk merchant list → payments → detail ledger in the browser.

## Out of scope

- Payment creation from the portal (staff trace; merchants submit via API), auth on the portal itself (internal trust model), pagination (TASK-07), merchant status change UI.

## Definition of done

- [ ] `/merchants` lists real merchants from the backend; register form creates one and the table reflects it
- [ ] `/payments` lists payments with a working status filter; detail page shows the full chronological ledger with exact amounts
- [ ] Loading, empty and error states render (backend down produces the quoted error, not a crash)
- [ ] Fonts are Libre Franklin / Source Sans 3 / IBM Plex Mono; zero slop-scan findings (`intent.py scan` clean on portal source)
- [ ] `npm run lint` and `npm run build` exit 0; manual walkthrough evidenced

## Verification criteria

- [ ] Every DoD checkbox evidenced (build output, scan output, walkthrough)
- [ ] Lint · Typecheck · Build all pass
- [ ] Intent gates still PASS after build (`intent.py gates` exit 0)
