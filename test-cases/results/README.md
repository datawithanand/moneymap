# Automated Test Execution Results

`automated-run-2026-07-08.csv` is a **real execution log**, not a manual test case list. It was
produced by building `moneymap.jar`, running it locally against a fresh JSON data directory, and
driving the actual HTTP endpoints (registration → onboarding wizard → login → CSRF-protected
`/assets/{module}/save` form submissions) with a Python `requests` script — the same flow a
browser would use.

For each of the 22 generic-CRUD modules (all modules except `pf`, `flexi-rds`, `salary`, which use
bespoke controllers/forms and were not driven by this script), the run executed:

- **POS-001** — create a valid record, confirm it renders in the list view, then delete it
- **NEG-001** — omit a required field, confirm the form re-renders with a field error
- **NEG-002** — submit a negative number, confirm the server rejects it
- **NEG-003** — submit an invalid select/enum value, confirm the server rejects it
- **SEC-001** — submit an XSS payload (`<script>alert(1)</script>`) in a free-text field, confirm it's HTML-escaped when rendered back in the list
- **SEC-002** — submit a SQL-injection-style string, confirm it's stored/handled without a server error

149 checks ran; 147 passed, 2 failed (both genuine defects — see below). Results are columns:
`Module, TC_ID, Category, Description, Result, Detail`.

## Defects found

1. **`recurring-deposits` NEG-002** — `tenureMonths` accepts a negative value (e.g. `-5`) without
   error. Root cause: `RecordBinder.bind()` only rejects negative values for `BigDecimal`-typed
   fields (`bd.signum() < 0`); `tenureMonths` is an `Integer`, so the negativity check never runs.
2. **`bonds` NEG-002` — same root cause: `unitsHeld` is `Integer`, so a negative units count is
   silently accepted.

**Fix recommendation:** extend the negativity check in
`src/main/java/com/moneymap/module/RecordBinder.java` to also cover `Integer`/`int` values, not
just `BigDecimal`.

## Not covered by this automated run

`pf` (custom UAN/employer UI), `flexi-rds` (custom deposit-ledger UI), and `salary` (custom dynamic
salary-component UI) all have bespoke controllers rather than the generic `/assets/{path}/save`
endpoint, so this script did not exercise them. They still have full manual test cases in the
sibling CSVs (`test-cases/pf.csv`, `test-cases/flexi-rds.csv`, `test-cases/salary.csv`).
