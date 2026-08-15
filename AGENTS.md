# Code Review Rules — ue6dejunio-api

Spring Boot 4 / Java 25 backend, strict Hexagonal Architecture.
Base package: `bo.edu.univalle.sis.ue6dejunio_api`.

## Architecture (Hexagonal — enforce boundaries)

- Three layers: `domain`, `application`, `infrastructure`.
- `domain/models`: pure records/POJOs and commands. No Spring, JPA, Jackson, or web
  annotations here.
- `domain/ports`: interfaces only. `IXxxDomain` = repository/persistence port,
  `IXxxService` = use-case port.
- `application/services`: use-case implementations. Own the transaction boundary
  (`@Transactional` on each use case). Depend on ports, never on infrastructure classes.
  Controllers must not declare `@Transactional`. Adapters may declare
  `@Transactional(readOnly = true)` (and per-method `@Transactional` on writes) as a
  persistence optimization — this is the established repository-adapter convention here
  and is not a violation.
- `infrastructure`: adapters (implement domain ports), JPA `entities`, Spring Data
  `repositories`, `web` (controllers + DTOs), `mappers`, `security`, `config`, `mail`.
- Flag any import that crosses a boundary the wrong way: JPA/`entities`, `jakarta.*`
  web types, or Spring web types leaking into `domain` or `application`.
- DTOs stay in `infrastructure/web/dto`; do not return JPA entities from controllers.

## Java / style

- No `var` for non-obvious types where it hurts readability; prefer explicit types on
  fields and public signatures.
- Prefer records for immutable data. No Lombok on domain records.
- No `System.out`/`printStackTrace`; use SLF4J.
- No unused imports, fields, or parameters.
- Public service/adapter methods have explicit return types.

## Spring / web

- Controllers are thin: validate input, call a service, map to a DTO. No business logic.
- Authorization: role rules in `SecurityConfig`; object-level ownership via
  `@PreAuthorize("@authz.canXxx(authentication, #id)")`. Every endpoint that touches a
  course/class_group/enrollment/student must enforce ownership (Director bypasses,
  homeroom/technical teacher scoped per domain rules). Flag any new endpoint returning
  another user's data without an ownership guard (IDOR).
- Request DTOs use Bean Validation (`@NotNull`, `@Size`, `@Pattern`, `@Min`, `@Max`).
- Error handling goes through `GlobalExceptionHandler`. Throw the existing domain
  exceptions: `ResourceNotFoundException` (404), `ConflictException` /
  `DuplicateResourceException` (409), `ValidationException` (400),
  `InvalidResetTokenException` (400). Never let a business rule surface as a raw 500
  (e.g. do not throw `IllegalArgumentException` for a business conflict — it maps to 500).

## Persistence / performance

- No N+1: when mapping a list with relations, batch-load (`...IdIn(...)`) or use
  `@EntityGraph`/fetch joins; never query inside a `.map()` per row.
- Null-safe search: never bind a nullable param straight into `LOWER(... LIKE ...)`
  (Postgres `lower(bytea)` crash) — split into a no-filter query and a LIKE query.
- Logical deletes over hard deletes for users/students/subjects; preserve academic
  history.

## Testing

- Test files (`*Test.java`, `*IT.java`) are intentionally OUT OF this review's scope
  (excluded in `.gga`). They exist under `src/test` and are verified by the build. Do NOT
  flag "missing tests" or "no test files in the changeset" — you cannot see them here.
- Strict TDD. Every behavior change ships with tests.
- Pure logic (services, validation, math, mappers) → JUnit 5 + Mockito unit tests, no DB.
- DB-touching behavior → integration tests extending `AbstractIntegrationTest`
  (Testcontainers Postgres). Do not use H2 for anything relying on DB-computed columns
  (`GENERATED ALWAYS AS`), e.g. `academic_scores.total_score`.
- Grade/attendance math must not change silently: refactors need before/after evidence.

## Security

- Never log secrets, tokens, passwords, or reset-token URLs.
- Passwords always through the configured `PasswordEncoder` (bcrypt); never stored plain.
- Public endpoints (`PUBLIC_PATHS`) that accept input must be rate-limited.

## Language of artifacts

- Code identifiers, comments, and commit messages in English (neutral technical register).
- User-facing API messages may be Spanish (neutral), matching existing endpoints.
