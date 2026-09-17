# Plan: TASK-08 — FEE transactions in the payment ledger

## Objective

`TransactionType.FEE` exists and is correctly weighted (sign 0, excluded from `capturedAmountInUsd`), but `Payment.record()` falls through to its rejection branch, so every fee attempt returns 409. This task gives fees a real rule: they are administrative ledger entries that never move the payment's lifecycle, but they cannot appear out of nowhere or exceed the amount they fee against.

## Design decisions

- **Status rule**: a FEE is accepted only when the payment has an authorization to fee against — status must be `AUTHORIZED`, `CAPTURED`, or `REFUNDED` (settlement fees can post after a refund). `REQUIRES_PAYMENT` (nothing to charge against), `FAILED` and `CANCELLED` (the payment's story is closed) → 409. The payment's status never changes when a fee is recorded.
- **Amount rule**: fee amount must satisfy `0 < fee <= payment.amount` (a fee may equal but not exceed the transacted amount). The lower bound is already enforced by the entity; the upper bound is new in `record()`.
- **Multiple fees** are allowed — processors post fees in batches; no uniqueness constraint.
- **Aggregate untouched**: `capturedAmountInUsd()` keeps excluding fees (sign 0) — it measures merchant proceeds, not gateway revenue. Exposing a fees total is out of scope.
- **API layer**: no changes. `RecordTransactionRequest` already parses any enum value; the 409 path via `IllegalStateException` exists; the failed-fee (status=FAILED) generic path already fails the payment and is intentionally left as is.
- **Portal**: no changes — the ledger detail renders transaction types as plain text, so FEE rows appear automatically.
- **Docs**: `docs/system-design.md` section 1 gains the fee-recording flow.

## Steps

1. `Payment.record()` — add the `FEE` branch with the two guards above; comment the rule. All five enum values now have explicit branches; the defensive `else` stays for future values.
2. Tests in `PaymentApiTests` (fail on unmodified code):
   - authorize → capture → FEE 2.50 → 201, payment still `CAPTURED`, ledger has 3 rows, `capturedAmountInUsd` unchanged.
   - FEE on a fresh payment (`REQUIRES_PAYMENT`) → 409.
   - FEE 100.01 on a 100.00 payment → 409 (exceeds); FEE 100.00 → 201 (boundary allowed).
   - authorize → capture → refund → FEE → 201, payment stays `REFUNDED`.
3. `docs/system-design.md` — add flow "record processing fees against an authorized payment".

## DoD

- New fee tests fail on the unmodified codebase; full suite green after.
- Fee rows persist and appear in `GET /api/payments/{id}` ledger with status untouched.
- Review scan clean; commit + record-progress.
