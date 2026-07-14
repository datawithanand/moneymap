# Calculators — Test Execution Results

`automated-run-2026-07-08.csv` is the **pre-fix** real execution log against the running app (built
jar, registered/onboarded a test user, drove authenticated HTTP requests with CSRF tokens exactly
like a browser), not a simulation. Manual test cases per calculator (positive, negative, boundary,
security/hacker, and end-user scenarios) live in the sibling `test-cases/calculators/*.csv` files;
this run automated the highest-risk subset of those (crash risks, missing validation, CSRF).

36 checks ran pre-fix: **12 confirmed bugs**, 24 passed.

`automated-run-2026-07-08-postfix.csv` is the **post-fix** re-run: all 12 bug scenarios now return
a clean HTTP 200 with a specific validation message instead of a 500/crash/silent-wrong-answer, and
all 9 reference-value happy-path calculations were re-verified with no regressions. See "Fixes
applied" below.

## Confirmed bugs

### 1. Server crashes (HTTP 500) on Infinity/NaN intermediate math — highest severity
`CalculatorMath.money()` calls `BigDecimal.valueOf(double)`, which throws
`NumberFormatException` if the double is `Infinity` or `NaN`. Any calculation that produces an
infinite or undefined intermediate result crashes the request instead of showing a validation
error:
- **FIRE** — `swrPercent=0` → division by zero → `Infinity` → 500.
- **CAGR** — `startValue=0` (→ `Infinity`), negative `startValue`/`endValue` (→ `Math.pow` of a
  negative base with a fractional exponent = `NaN`), `years=0` (→ `1/years` exponent = `Infinity`)
  → 500 in all three cases.
- **Loan/EMI** — `tenureMonths=0` → EMI's `(1+r)^n − 1` denominator is `0` → `Infinity` → 500.
- **Loan/EMI, PPF, SIP** — extreme-but-not-obviously-invalid inputs (`tenureMonths=2,000,000`,
  `years=2,000,000`, `years=100,000`) cause `double` compounding to overflow past
  `Double.MAX_VALUE` to `Infinity`, hitting the same crash. This is reachable by an honest typo
  (e.g. an extra zero), not just an attacker.

**Fix direction:** guard against zero/negative denominators and non-finite doubles before
constructing a `BigDecimal` (either validate inputs before computing, or check
`Double.isFinite(v)` in `money()` and raise a clean validation error instead).

### 2. Missing null-check crashes FD calculator
`FD-003` — submitting forward mode without `principal` (or reverse mode without
`targetMaturityValue`) throws `NullPointerException: principal is null` in `CalculatorMath.fdForward`
→ HTTP 500. The `@RequestParam(required = false)` fields are optional at the HTTP layer but never
null-checked before use.

### 3. No server-side input validation anywhere in the calculators
Unlike the asset modules (`RecordBinder.bind()` rejects negative `BigDecimal`/`Integer` values),
none of the 3 new calculator controllers validate their inputs at all. Confirmed accepted without
error:
- Negative `annualExpense` (FIRE), `monthlyAmount` (SIP), `monthlyWithdrawal`/`corpus` (SWP),
  `yearsToRetirement` (NPS), `principal` (FD, Loan), `annualContribution` (PPF),
  `oldRegimeDeductions` (Tax regime).
- `annualContribution` above the ₹1,50,000 statutory PPF cap (only a client-side `max=` attribute,
  trivially bypassed with a direct POST).
- SIP `stepUpPercent` with no upper bound (200% accepted).

### 4. NPS lump-sum can go negative
`NPS-003` — `mandatoryAnnuityPercent=150` is accepted (should be capped at 100), producing
`lumpSumWithdrawal = -₹27,14,068.74` — a nonsensical negative result silently displayed to the user.

## Fixes applied

- **`CalculatorMath.money()`** now checks `Double.isFinite(v)` and throws a clear
  `CalculatorValidation.ValidationException` instead of letting `BigDecimal.valueOf()` crash with
  an unhandled `NumberFormatException` on `Infinity`/`NaN`.
- **`CalculatorMath.fdForward`/`fdReverse`** now null-check their required argument and throw a
  clear validation exception instead of an unhandled `NullPointerException`.
- **New `CalculatorValidation` helper** (`positive`, `nonNegative`, `percentRange`, `range`,
  `check`) is used in all three calculator controllers to reject invalid input *before* it reaches
  the math — negative amounts/rates, zero/negative SWR, zero-or-negative years, out-of-range
  percentages, and the PPF statutory contribution cap (₹1,50,000) are now all rejected with a
  specific message shown on the same page (new `${error}` block added to every calculator
  template, matching the pattern already used on `tax-regime.html`).
- **Range caps double as DoS/overflow guards**: SIP tenure capped to 1–100 years, Loan/EMI tenure
  capped to 1–600 months, PPF tenure capped to 1–50 years, NPS horizon capped to 1–100 years — the
  same caps that reject nonsensical input also prevent the `double`-overflow-to-`Infinity` crash
  that extreme (but technically valid) values like `years=2,000,000` triggered.
- **NPS mandatory annuity %** is now validated to the `[0, 100]` range, which also eliminates the
  negative lump-sum-withdrawal bug (no separate clamp needed — the invalid input is rejected
  outright with a message).

## Confirmed correct (no action needed)

- **CSRF protection works.** The first test pass incorrectly flagged CSRF as broken because it
  used a brand-new *unauthenticated* session (which redirects to `/login` before the CSRF check
  ever runs). Retested with a properly authenticated session and no `_csrf` param: **all 9
  calculators correctly return 403**, same as every other form in the app.
- Unauthenticated access to any `/calculators/**` route redirects to `/login`.
- Non-numeric input (e.g. letters in a number field) is rejected with a clean HTTP 400, no stack
  trace leaked.
- `TAX-006` — negative gross income is correctly clamped to ₹0 tax via the controller's
  `.max(BigDecimal.ZERO)` guard.
- `TAX-003` — an unrecognized financial year shows a clean "No tax slabs configured" message
  instead of crashing.
- `CAGR-008`, `SWP-004`, `SIP-004` — zero-value edge cases that don't hit a division/exponent by
  zero compute correctly with no crash.
