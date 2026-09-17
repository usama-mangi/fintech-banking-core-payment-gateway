# Plan: TASK-03 — REST API: payments and transaction ledger endpoints

## Objective

Merchants can register but cannot submit payments or record ledger entries over HTTP. This task adds the core of the gateway: create a payment (idempotent by `idempotencyKey`), list payments by merchant/status, fetch a payment with its full chronological ledger, and record transactions (authorization, capture, refund, chargeback, fee) whose success moves the payment status through `Payment.record()`. The domain already owns transitions; the API exposes them and maps illegal ones to 409.

## Dependencies

- TASK-01 (closed), TASK-02 (closed). Reuses `GlobalExceptionHandler` and the `{status, error, message, timestamp}` contract; adds `IllegalStateException` mapping for invalid transitions.

## Design decisions

- Money over HTTP: request DTOs take `amount` as **string** (`"100.00"`), parsed to `BigDecimal` in the service; avoids float parsing and makes scale explicit.
- Idempotency: `POST /api/payments` first checks `PaymentRepository.findByIdempotencyKey`; a hit returns **200** with the existing payment, a miss returns **201**. Body includes `idempotencyKey` (unique column already enforced by the DB).
- `Merchant-Id` header on payment endpoints identifies the acting merchant (full auth comes in TASK-04). Unknown merchant → 404.
- New exception type `ApiExceptions.ConflictException`; `GlobalExceptionHandler` maps it and bare `IllegalStateException` to 409.
- Transaction recording requires `status=SUCCEEDED` (MVP gateway simulates the processor); the ledger is append-only, payment loaded and `payment.record(tx)` invoked inside one service transaction.
- Ledger access goes through `Payment.getTransactions()` (already `@OrderBy recordedAt ASC`); no extra repository query needed for the detail view.

## Files to modify

| Action | Path | What |
|--------|------|------|
| CREATE | `backend/src/main/java/.../api/PaymentDtos.java` | `CreatePaymentRequest`, `RecordTransactionRequest`, `PaymentSummary`, `PaymentDetail`, `TransactionResponse` |
| CREATE | `backend/src/main/java/.../api/PaymentService.java` | Create (idempotent), get, list, record-transaction use cases |
| CREATE | `backend/src/main/java/.../api/PaymentController.java` | `POST/GET /api/payments`, `GET /api/payments/{id}`, `POST /api/payments/{id}/transactions` |
| MODIFY | `backend/src/main/java/.../api/ApiExceptions.java` | Add `ConflictException` |
| MODIFY | `backend/src/main/java/.../api/GlobalExceptionHandler.java` | Map `ConflictException` and `IllegalStateException` → 409 |
| CREATE | `backend/src/test/java/.../api/PaymentApiTests.java` | Integration tests: lifecycle, idempotency, transitions, filters, errors |

## Implementation steps

1. Add `ConflictException` + handler mappings. Verify: compiles, existing tests green.
2. `PaymentDtos`: `CreatePaymentRequest(merchantId optional, amount String @Pattern \d+\.\d{2}, currency @NotBlank 3 letters, description, idempotencyKey @NotBlank max 64)`; `RecordTransactionRequest(type, amount String, status optional default SUCCEEDED)`; response records. Verify: compiles.
3. `PaymentService`:
   - `create`: resolve merchant (`Merchant-Id` header value or `request.merchantId()`), 404 if unknown; parse amount with `new BigDecimal(str)` catching `NumberFormatException` → 400; check `findByIdempotencyKey` → return existing (idempotent); else build `Payment`, save, 201.
   - `getDetail`: load payment, map summary + transactions chronologically.
   - `list(merchantId, status)`: delegate to existing repository finders.
   - `recordTransaction`: load payment (404), build `Transaction` (default status SUCCEEDED), call `payment.record(tx)` — domain throws `IllegalStateException` for illegal transitions, mapped to 409 — save, return transaction response.
4. `PaymentController`: `POST /api/payments` (`@RequestHeader("Merchant-Id")` optional), `GET /api/payments?merchantId=&status=`, `GET /api/payments/{id}`, `POST /api/payments/{id}/transactions`.
5. Tests (red-first for new behavior): auth-less create → 400 (missing header); create via header → 201 + REQUIRES_PAYMENT; replay same `idempotencyKey` → 200 same id; authorize → capture → refund happy path ends REFUNDED with ledger in order; capture before authorize → 409; refund before capture → 409; unknown merchant → 404; unknown payment → 404 on transaction POST; malformed amount → 400; `?status=REQUIRES_PAYMENT` filter; `?merchantId=` filter; GET detail embeds chronological transactions. Full gate: `./mvnw test`.

## Out of scope

- Partial refunds beyond what the domain accepts (amount checks stay domain-owned), fees auto-calculation, pagination (TASK-07), auth filter (TASK-04), `Merchant-Id` integrity checks beyond existence.

## Definition of done

- [ ] `POST /api/payments` is idempotent: first call 201, replay 200, same payment id both times
- [ ] Payment lifecycle via API: authorize → capture → refund leaves status REFUNDED; ledger ordered by recordedAt
- [ ] Illegal transition (e.g. capture before authorize) → 409 with the standard error body
- [ ] Unknown merchant or payment → 404; malformed amount → 400
- [ ] `GET /api/payments?merchantId=&status=` filters work
- [ ] New tests fail without the new code; all 12 existing tests stay green; `./mvnw test` exit 0

## Verification criteria

- [ ] Every DoD checkbox evidenced by test output
- [ ] Tests · Build pass

## Rationale

The service returns existing-vs-created by idempotency key instead of relying on a DB constraint exception, because a duplicate-key rollback would abort the request transaction and produce a 500, not a clean 200. String-typed amounts keep the API decimal-exact end to end. Routing transitions through `Payment.record()` keeps one source of truth for state: the domain rejects what the DB schema cannot express.
