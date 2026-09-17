# Plan: TASK-12 — Pagination controls in the admin portal lists

## Objective

Since TASK-07 the backend returns `{content, page, size, totalElements, totalPages}`, but the portal renders page 0 and discards the metadata. Staff cannot reach rows beyond the first 20. This task adds URL-driven pagination (links, not client JS) to both list screens.

## Design decisions

- **URL-driven**: `?page=N` in searchParams, server-rendered `<Link>` prev/next. No client state, works without JS, matches the existing GET-form status filter. Page 0 omits the param for clean URLs; payments hrefs preserve the `status` filter.
- **Guarded parsing**: non-numeric or negative `page` falls back to 0 in the portal (`parsePageParam` in a tiny `lib/params.ts`) so staff never see a raw 400 for a typo'd URL. Beyond-last-page renders the backend's empty page plus controls.
- **Controls**: `Pagination` server component — Previous/Next as links (rendered inert at the edges), "Page X of Y · N merchants" indicator from the envelope totals, hidden entirely when the list is empty.
- **Scope**: page size stays the backend default 20 on both screens; a size selector is a follow-up if staff ask. Backend unchanged.
- **design.yaml**: screens' component lists gain `Pagination`; log entry; intent gates re-run (token set unchanged).

## Steps

1. `lib/params.ts` (`parsePageParam`), `api.ts` list functions accept `page`, return full envelope.
2. `components/Pagination.tsx` server component.
3. Merchants page: parse, pass page, render controls; payments page: same, preserving status in hrefs.
4. design.yaml (screens + log) + gates; README routes line; handoff rows.
5. Verify: stop portal → `npm run build` (dev-server .next clobber trap from TASK-11 memory) → restart → seed 25 merchants + payments via curl → walkthrough prev/next, filter+paging, totals through the preview.
6. Review scan, commit, record-progress.

## DoD

- Both screens page through all rows; totals render from the envelope; filter+page compose on payments.
- Build green; walkthrough evidenced; gates pass; 44 backend tests untouched.
