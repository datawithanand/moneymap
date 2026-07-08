# MoneyMap — Module Test Cases

This folder contains functional QA test cases for every asset/liability module registered in
`src/main/java/com/moneymap/module/ModuleRegistry.java`. Each module has its own CSV file
(`test-cases/<module-path>.csv`, matching the module's URL path), covering:

- **Positive** — valid happy-path create/edit/delete
- **Negative** — missing required fields, wrong types, invalid enum values
- **Boundary** — zero/negative amounts, min/max dates, oversized/empty input
- **Conditional** — `visibleIf` field show/hide and validation logic
- **Cross-field / business-rule** — date ordering, computed-value math, allocation-class splits, currency conversion
- **Security** — SQL-injection and XSS payloads in free-text fields
- **Dedup / permissions** — import dedup keys (folio/ISIN), family-tier field visibility, masking

Each CSV has 8 columns: `TC_ID, Category, Priority, Field/Scenario, Preconditions, Test Steps, Test Data, Expected Result`.

See the module-coverage table in the project conversation/PR description for the full per-module
test case counts and key findings.
