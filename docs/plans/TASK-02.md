# Plan: TASK-02 — REST API: merchants endpoints with validation and error handling

## Objective

The backend has entities and repositories but no HTTP layer, so the admin portal and merchant clients have nothing to call. This task adds the first slice of the REST API: create and read merchants, Bean Validation on request bodies, the project-wide error response format from `docs/system-design.md` (`{status, error, message, timestamp}`), and integration tests that prove the endpoints work against the real Spring context.

## Dependencies

- TASK-01 (closed): profiles and test config in place.

## Design decisions

- DTO records (`MerchantDtos.java` holding `CreateMerchantRequest`, `MerchantResponse`) keep entities out of the API contract; the entity's email/apiKey columns are unique, so duplicate email returns 409 with the error format.
- `MerchantService` owns use cases and `@Transactional` boundaries; controller stays thin (map → call → 201/200).
- Exceptions: `NotFoundException` (404) and `DuplicateEmailException` (409) as small runtime exceptions in the `api` package, handled by one `@RestControllerAdvice`.
- List endpoint supports `?status=` filter using the existing `MerchantRepository.findByStatus`.
- API-key generation: `SecureRandom` + Base64URL, 32 bytes, `sk_` prefix, in the service. Keys are returned once at creation; GET responses include the key for the MVP admin view (no auth yet — TASK-04 adds it).

## Files to modify

| Action | Path | What |
|--------|------|------|
| CREATE | `backend/src/main/java/.../api/MerchantDtos.java` | Request/response records with validation annotations |
| CREATE | `backend/src/main/java/.../api/MerchantController.java` | `POST /api/merchants`, `GET /api/merchants`, `GET /api/merchants/{id}` |
| CREATE | `backend/src/main/java/.../api/MerchantService.java` | Use cases, transaction boundaries, API-key generation |
| CREATE | `backend/src/main/java/.../api/ApiExceptions.java` | `NotFoundException`, `DuplicateEmailException` |
| CREATE | `backend/src/main/java/.../api/GlobalExceptionHandler.java` | `@RestControllerAdvice`: 400/404/409 with the standard error body |
| CREATE | `backend/src/test/java/.../api/MerchantApiTests.java` | `@SpringBootTest` + MockMvc integration tests |

## Implementation steps

1. DTOs + exceptions + error handler (`GlobalExceptionHandler` covers `MethodArgumentNotValidException` → 400, `NotFoundException` → 404, `DuplicateEmailException` → 409, plus a fallback 500 handler). Verify: compiles.
2. `MerchantService` with `create(businessName, email)` and `getById`, `list(status)`. API-key generated with `SecureRandom`; duplicate email maps to `DuplicateEmailException`. Verify: compiles.
3. `MerchantController` wired to the service; `@Valid` on request bodies. Verify: compiles.
4. Integration tests (red-first where behavior is new): POST creates and returns 201 with generated `sk_` key; POST duplicate email → 409 with error body shape; GET unknown id → 404 with error body; GET list + `?status=ACTIVE` filter; POST blank name/invalid email → 400. Run `./mvnw test` to see new tests pass and existing 7 stay green.
5. Full gate: `./mvnw test`, then boot the dev profile once and `curl POST /api/smoke` sanity check? No — integration tests already exercise the HTTP layer via MockMvc against the real context; skip the manual curl.

## Out of scope

- Payments/transactions endpoints (TASK-03), authentication (TASK-04), pagination (deferred; note added to backlog), PATCH/status changes for merchants.

## Definition of done

- [ ] `POST /api/merchants` returns 201 with a `sk_`-prefixed API key; body validated (400 on bad input)
- [ ] Duplicate email → 409; unknown id → 404; both in the `{status, error, message, timestamp}` shape
- [ ] `GET /api/merchants` and `GET /api/merchants/{id}` work; `?status=` filters
- [ ] New integration tests fail without the new code and pass with it; all 7 existing tests stay green
- [ ] `./mvnw test` exit 0

## Verification criteria

- [ ] Every DoD checkbox evidenced by test output
- [ ] Tests · Build pass

## Rationale

DTO records over entity exposure keep the API contract stable while the domain evolves, and they give validation a natural home. `@RestControllerAdvice` centralizes error shaping once so TASK-03 inherits it free. MockMvc against the full context exercises mapping, validation, serialization and persistence together, which is where integration bugs actually live at this scale.
