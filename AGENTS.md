# AGENTS.md — Engineering Instructions

## Mission and source of truth
Build a production-quality, industry-agnostic inventory management system, initially for a single company's internal operations. Read `REQUIREMENTS.md` before planning or coding. The current scope in that file is binding; future scope is architectural guidance, NOT authorization to implement features. Do not infer a headshop-specific business model from examples or mockups.

## Working principles
- Act as a senior software engineer: prioritize correctness, maintainability, security, and verifiable delivery over speed or speculative abstractions.
- Inspect the existing repository, frontend artifacts, tests, and configuration before making changes. Preserve working behavior and avoid unnecessary rewrites.
- Plan small, reviewable changes. Explain important design choices and tradeoffs. Do not claim tests ran unless executed.
- Ask for clarification when a business rule is consequential and unspecified. Document reasonable assumptions explicitly.
- Never commit credentials, tokens, customer data, or secrets. Use environment variables and safe example configurations.

## Architecture and stack
- Backend default: Java 21 LTS, Spring Boot 3.x, Spring Web, Spring Validation, Spring Security, Spring Data JPA or carefully justified SQL access. Verify compatibility with the project's existing versions before upgrades.
- Database: PostgreSQL, migrations with Flyway, schema constraints and appropriate indexes. No automatic destructive schema updates in production.
- Frontend: integrate the existing Stitch-defined UI. Preserve its layout, components, responsive behavior, and design language; adapt mock data to real APIs. Do not redesign without a requirement.
- Organize backend as a modular monolith by domain/capability (catalog, inventory, authentication, reporting, integrations). Maintain clear boundaries between API, application services, domain rules, and persistence.
- The core must be independent of shop type, sales channel, device, and notification provider. Use configurable categories, units, and product attributes instead of industry-specific hardcoding.
- This is single-company software in V1. Do not build tenant isolation, subscriptions, licensing, SaaS billing, customer carts, or public storefronts unless requirements change. Avoid architectural decisions that would make later separation prohibitively difficult.
- RabbitMQ is NOT required for V1. Do not introduce a broker, distributed microservices, or a generic event bus without a demonstrated need. Design application events behind interfaces; add durable outbox and queue consumers when async integrations actually arrive.

## Inventory invariants
- Define and document quantities: on-hand physical quantity, reserved quantity (when reservations exist), and available quantity. In V1, do not silently treat an unimplemented reservation concept as active.
- All stock changes must pass through a central inventory application service. Controllers, imports, future RFID, and future WhatsApp integrations must not directly mutate balances.
- Model each stock adjustment as an immutable, auditable movement with product, quantity, movement type, timestamp, actor, source, reason/reference, and idempotency key when relevant. Corrections require compensating movements, not silent history edits.
- Store quantities in a precise numeric type suitable for the product's unit (e.g. integer smallest units or NUMERIC), never floating-point approximations. Specify unit conversion rules before introducing them.
- Never permit negative available/on-hand stock by default. Any override requires an explicit approved business rule and audit trail.
- Use database transactions to commit stock movement and resulting balance atomically. Protect concurrent updates using conditional SQL updates or row locks, and test races. Never rely only on a frontend quantity check.
- Distinguish stock count observations from confirmed adjustments. Physical counts do not directly overwrite balances without a reconciliation/approval operation.
- For a given business operation, exactly one stock effect may be committed, even after retries.

## Idempotency and delivery semantics
- Require a stable idempotency key for retriable stock-changing API commands and integrations. Persist it with operation scope, request fingerprint, result/status, and appropriate uniqueness constraints.
- Retrying an identical command must return the original outcome without a second movement. Reusing a key with a different payload must fail clearly.
- Enforce uniqueness in PostgreSQL, not only in memory. Handle concurrent duplicate requests in a transaction-safe way.
- Distinguish duplicate RFID reads from duplicate inventory operations: repeated tag observations are expected; deduplicate within an inventory session by tag identity, and require explicit reconciliation before stock changes.
- Future queues/webhooks are at-least-once unless proven otherwise. Consumers must be idempotent; never promise end-to-end exactly-once delivery.

## Data integrity and security
- Use foreign keys, NOT NULL, CHECK, unique constraints, optimistic versioning where appropriate, and migrations with rollback strategy.
- Authenticate internal staff; implement least-privilege authorization for viewing, adjusting, and administering inventory. Audit privileged actions.
- Validate and normalize inputs at API boundaries. Never trust client-calculated balances or prices. Use parameterized queries and safe serialization.
- Protect secrets and personal data. Apply rate limits and reasonable request size limits. Keep useful operational logs without leaking sensitive information.
- Document backups, restore drills, and migration procedures before production use.

## APIs and UI integration
- Use stable, documented REST APIs, consistent error envelopes, pagination/filtering/sorting, and OpenAPI documentation.
- Define contracts before wiring Stitch frontend screens. Provide loading, empty, validation, conflict, and retry states. Do not display fabricated inventory data as live data.
- Preserve keyboard usability, accessible labels, responsive layouts, and the frontend's theme support.
- The frontend must not be the source of truth for stock availability or authorization.

## Testing and definition of done
- Unit-test domain rules and validation; integration-test PostgreSQL constraints and transactions (prefer Testcontainers); API-test authorization, idempotency, and failure paths.
- Mandatory concurrency tests: two simultaneous stock deductions, duplicate command retries, insufficient stock, transaction rollback, and retry after ambiguous network failure.
- Test frontend against API contracts and cover key workflows with automated end-to-end tests where feasible.
- A change is complete only when code, migrations, tests, relevant docs, and operational implications are addressed. Run lint/build/tests and report exact commands/results, or explain what could not be run.
- Prefer simple observable operations: health checks, structured logs, correlation IDs, and actionable error messages.

## Future integration boundaries — do not implement in V1
- WhatsApp: future customer catalog, carts, order events, inventory-change alerts, and payment verification. Cart contents do not reserve stock unless explicitly designed. Confirmed payment events must be idempotent; outgoing notifications should use durable outbox/queue patterns and approved messaging templates/consent rules.
- RFID: future UHF readers, scan sessions, tag identity registry, read deduplication, and stock reconciliation. Raw reads are evidence, not direct stock mutations.
- Marketplace/storefront/SaaS: possible future direction only. Do not add multi-tenancy or sales-channel coupling now.

## Delivery workflow
1. Read `REQUIREMENTS.md` and inspect Stitch frontend deliverables and repository structure.
2. Map UI screens to actual entities, APIs, and states; list ambiguities and missing screens.
3. Propose the smallest vertical slice and database migrations.
4. Implement backend and frontend integration with tests.
5. Verify critical inventory invariants under concurrency and retries.
6. Provide setup instructions, env.example, migration commands, test results, and known limitations.

When requirements conflict, flag the conflict instead of silently choosing. Current-scope requirements outrank speculative future features.
