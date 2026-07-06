# MoneyMap

Self-hosted, privacy-first personal finance manager for Indian families.
One dashboard for every asset, liability, insurance policy and goal — on your own server,
with no bank linking, no cloud dependency, and full export at all times.

Built to the PRD in `../docs/prd/` (start with `00-foundation.md`).

## Stack

Java 21 · Spring Boot 3.3 · Thymeleaf (server-rendered, no SPA) · JSON file storage with
atomic writes + per-collection locking (no database) · BCrypt via `spring-security-crypto`
only (no Spring Security filter chain) · Apache POI (Excel) · Apache PDFBox (PDF) ·
Docker multi-stage build.

## Quick start

```bash
docker compose up -d --build
```

Then open http://localhost:1010 and log in with `admin` / `admin`.
You'll be forced to set a new password immediately, then guided through a short setup wizard.

All data lives in the `moneymap-data` Docker volume — back it up per PRD Section 15 §8.
To enable outbound email (SMTP), set `MONEYMAP_ENCRYPTION_KEY` (min 32 chars) in a
gitignored `.env` file first.

## Development

```bash
mvn spring-boot:run                              # data in ./data, port 1010
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run   # hot reload, data in ./dev-data
```

## Implementation status — all PRD sections implemented

| Area | PRD Sections | Highlights |
|---|---|---|
| Platform | 00, 01, 01B, 02 | Custom HttpSession auth, lockout, remember-me, single-session, setup wizard, admin panel, audited portfolio view, audit log + CSV, system health, SMTP (AES-encrypted password), invite links, CSRF everywhere |
| Family & Vault | 03/04 | Groups, invitations (incl. email-before-account), directional 4-level permission matrix, deny-window emergency access state machine (request → remind → auto-approve → 30-day grant → expire/revoke), consolidated dashboard, contacts directory, full-access drill-down, notification centre |
| Cash & Banking | 05 | Savings, Cash in Hand, FDs (maturity math), RDs (accrual math), iWISH/Flexi RD deposit ledger, Household Members tags |
| Investments | 06 | Mutual funds (category → allocation, hybrid splits, ELSS), stocks/ETFs (allocation override), bonds, crypto, ESOPs/RSUs/ESPP (vested-only, underwater = ₹0) |
| Retirement | 07 | EPF/VPF UAN parent/child rollup, PPF, NPS (allocation split), gratuity, government schemes |
| Insurance | 08 | Term, health, LIC/endowment/ULIP (surrender value → net worth; ULIP allocation split) |
| Liabilities | 09 | Unified loans module incl. credit cards (conditional fields, utilization) — the only subtracting module |
| Real Estate & Physical | 10 | Property, gold (per-purity manual rates / unit NAV), other physical assets |
| Goals & Income | 11, 11A | Dual SIP calculator, other income, salary profiles with dynamic components + tax engine (editable FY 2025-26 slab seeds, 87A, cess) |
| Dashboards | 12 | `PortfolioAggregationService` (single source of truth), main overview (hero, buckets, donut, module cards, goal SIPs, family-member filter), net worth trend + snapshots, allocation targets/drift, tax planning (80C/80D/80CCD(1B)) |
| Export / Import | 13 | JSON round-trip (business-key dedup on merge, type-to-confirm replace), Excel multi-sheet, PDF summary |
| Theming | 14 | 5 themes via CSS custom properties, Fraunces/IBM Plex, live wizard preview |
| Ops | 15, 16 | Docker/compose, TZ-aware daily jobs, atomic writes + locking, malformed-file recovery, purge job across every collection |

### Known v1 simplifications (documented deviations)

- Import merge mode auto-skips duplicates with a report (the PRD's per-conflict Skip/Overwrite
  prompt is simplified to skip-and-report).
- Setup Wizard Step 3 offers "Skip for now" only — create/join a family group from the Family
  section after onboarding instead.
- Permission preview mode (§2.4) is covered by the identical rendering path used for real
  cross-member viewing (family drill-down); a dedicated "preview as" button is not yet wired.
- MF Deep-Dive dashboard is served by the Mutual Funds module list (invested/current/gain-loss
  per folio) rather than a separate page.
- Live NAV/price/gold-rate fetch, PostgreSQL layer, field-level encryption: v2/v3 per Appendix A.
