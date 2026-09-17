# Plan: TASK-04 — API-key auth filter for merchant endpoints (X-API-Key)

## Objective

Every payment endpoint currently trusts a bare `Merchant-Id` header, so any caller can impersonate any merchant. This task adds the first real auth layer: requests to `/api/payments/**` must present a valid merchant API key in `X-API-Key`; the authenticated merchant is resolved server-side from that key, and the `Merchant-Id` spoofing vector disappears from the payment flow. Admin endpoints (`/api/merchants/**`, actuator) stay key-exempt for the MVP internal portal, per the backlog note.

## Dependencies

- TASK-01, TASK-02, TASK-03 (all closed).

## Design decisions

- A servlet `Filter` (plain `jakarta.servlet.Filter` in a `FilterRegistrationBean`, highest precedence) rather than Spring Security: the MVP has one rule and no sessions; adding the Security filter chain now buys complexity the backlog does not ask for. Revisit trigger: role-based admin auth.
- Key lookup uses the existing `MerchantRepository.findByApiKey`; a suspended/closed merchant is rejected 403 even with a valid key; unknown key → 401; missing header → 401.
- The authenticated merchant id is passed down via a request attribute (`authenticatedMerchantId`); `PaymentService.create` takes it as the authoritative merchant id. The `merchantId` body field on create is removed from use (403 if it disagrees — explicit rejection beats silent override).
- Timing-attack hardening: lookup by exact key equality on a unique indexed column; constant-time comparison is unnecessary at this storage layer because the DB index does the matching, but the error response never says whether the key existed.
- `GET /api/payments/{id}` and list endpoints require a valid key too; cross-merchant reads: MVP allows any valid key (admin-portal style reads stay possible); enforced per-merchant reads are a revisit trigger. This is documented, not hidden.

## Files to modify

| Action | Path | What |
|--------|------|------|
| CREATE | `backend/src/main/java/.../api/ApiKeyAuthFilter.java` | Filter: extract `X-API-Key`, resolve merchant, set request attribute, or short-circuit 401/403 |
| CREATE | `backend/src/main/java/.../config/WebConfig.java` | `FilterRegistrationBean` wiring the filter to `/api/payments/*` only |
| MODIFY | `backend/src/main/java/.../api/PaymentService.java` | `create` uses authenticated merchant id; body `merchantId` must be null or equal (403) |
| MODIFY | `backend/src/main/java/.../api/PaymentController.java` | Read authenticated merchant attribute; drop `Merchant-Id` header |
| MODIFY | `backend/src/main/java/.../api/GlobalExceptionHandler.java` | (no change expected — filter writes 401/403 JSON directly using the same body shape) |
| MODIFY | `backend/src/test/java/.../api/PaymentApiTests.java` | Tests obtain a real key (via repository or POST /api/merchants) and send `X-API-Key`; new tests: 401 no key, 401 bad key, 403 suspended merchant, 403 mismatched body merchantId |
| MODIFY | `backend/src/main/java/.../domain/Merchant.java` | (none — status checks read existing `MerchantStatus`) |

## Implementation steps

1. `ApiKeyAuthFilter` + `WebConfig` registration. Filter behavior: missing/unknown key → 401 JSON `{status,error,message,timestamp}`; known key but merchant not ACTIVE → 403; else set attribute and chain. Verify: compiles.
2. `PaymentService`/`PaymentController` switch to attribute-sourced merchant id; `Merchant-Id` header removed. Verify: compiles, existing tests updated in step 3.
3. Update `PaymentApiTests`: helper `createMerchantAndGetKey()` via `POST /api/merchants`; all payment requests send `X-API-Key`. New tests: no key → 401; unknown key → 401; suspended merchant key → 403; `merchantId` in body belonging to another merchant → 403. Verify red for auth tests before implementation? The filter already exists from step 1; assert new tests pass with it and that removing the header path breaks none of them.
4. Full gate: `./mvnw test` — all 21+ tests green, plus boot smoke test hitting `/api/payments` without a key expecting 401 JSON.

## Out of scope

- Rate limiting, key rotation/revocation endpoints, admin-role auth for `/api/merchants/**`, hash-at-rest keys (plaintext MVP per system-design; revisit with external partners).

## Definition of done

- [ ] Payment endpoints without `X-API-Key` → 401 with standard error body; unknown key → 401
- [ ] Suspended merchant with valid key → 403
- [ ] Valid key: create/list/get payments work; body `merchantId` from a different merchant → 403
- [ ] Merchant and actuator endpoints remain open (MVP admin trust model)
- [ ] All tests green; `./mvnw test` exit 0; smoke run shows 401 JSON on unauthenticated POST

## Verification criteria

- [ ] Every DoD checkbox evidenced by test output and smoke run
- [ ] Tests · Build pass

## Rationale

A single servlet filter matches the threat model (one credential type, no roles) without dragging in Spring Security's chain. Server-side merchant resolution from the key removes the impersonation vector entirely instead of validating a client-claimed id. Explicit 403 on a disagreeing body `merchantId` prevents a silent privilege change if the field is later repurposed.
