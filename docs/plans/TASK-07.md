# Plan: TASK-07 — Pagination for the merchant and payment list endpoints

## Objective

`GET /api/merchants` and `GET /api/payments` currently return every matching row as a bare array. Any list endpoint that grows without bound is a latency and memory incident waiting for data to arrive. This task wraps both endpoints in a page envelope backed by Spring Data's `Pageable`, with validated query parameters, a deterministic sort, and the admin portal updated to the new contract.

## Design decisions

- **Envelope**: `{ "content": [...], "page": 0, "size": 20, "totalElements": 3, "totalPages": 2 }`. One generic record `PageResponse<T>` in the `api` package, shared by both controllers. Element shapes stay the existing `MerchantResponse` / `PaymentSummary`.
- **Query parameters**: `page` (0-based, default 0), `size` (default 20, max 100, min 1), existing `status` (and `merchantId` for payments) unchanged.
- **Sort**: fixed server-side `createdAt DESC` (newest first). Never accept a client sort parameter — property names become an injection surface and no consumer needs custom sorts yet.
- **Validation**: `size=0`, negative page/size, `size > 100` → 400 via `ApiExceptions.BadRequestException`. Non-numeric `page`/`size` → Spring's `MethodArgumentTypeMismatchException`, which currently falls into the catch-all 500 handler; add a dedicated handler mapping it to 400 with the standard error shape.
- **Repositories**: add `Page`-returning `Pageable` overloads alongside the existing `List` overloads (Spring Data resolves overloads by signature). The `List` variants stay: repository tests and internal aggregate use keep them honest.
- **Callers of the changed contract** (verified by grep): `MerchantService`, `PaymentService`, both controllers, `MerchantApiTests` + `PaymentApiTests` list assertions, and the portal (`api.ts` `listMerchants`/`listPayments`, `merchants/page.tsx`, `payments/page.tsx`). The benchmark never lists — no change there.

## Steps

1. `api/PageResponse.java` — generic envelope record with a `from(Page<S>, Function<S,T>)` mapper.
2. `GlobalExceptionHandler` — map `MethodArgumentTypeMismatchException` → 400, standard error shape.
3. `MerchantRepository` + `PaymentRepository` — `Page`-returning overloads for the four filter combinations.
4. `MerchantService.list` / `PaymentService.list` — return `PageResponse<...>`; clamp and validate page/size before building the `PageRequest` (fixed `createdAt` DESC sort).
5. Controllers — accept `page`/`size` params, return the envelope.
6. Tests (new, fail on unmodified code):
   - Merchants: seed 3, `size=2&page=0` → 2 items / totalElements 3 / totalPages 2; `page=1` → last item; newest-first ordering.
   - Payments: status filter combined with paging across 3 payments in 2 pages.
   - 400s: `size=0`, `page=-1`, `size=101`, `page=abc`.
   - Update the two existing list tests (`getMerchantsListAndStatusFilter`, `listFiltersByStatus`) to the envelope.
7. Portal: `api.ts` envelope types (`Page<T>`), `listMerchants`/`listPayments` return `data.content` of the envelope or the full envelope as needed; both list pages updated; `npm run build` green.
8. `docs/system-design.md` section 4 — list contract lines updated to the envelope.

## DoD

- New pagination tests fail on the unmodified codebase.
- `./mvnw test` fully green (26 existing + new tests).
- Portal `npm run build` green after the contract change.
- Review scan clean; error contract unchanged for existing failure modes.
