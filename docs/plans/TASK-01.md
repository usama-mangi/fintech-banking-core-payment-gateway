# Plan: TASK-01 — Spring profile config: H2 dev profile with console, datasource and JPA settings

## Objective

`application.properties` contains only the app name, so the app runs on an accident of defaults: open-in-view is on (a warning fires in every test run), the H2 console is off, and tests inherit whatever the main classpath says. This task establishes explicit profile-based configuration: a shared base, a dev profile on in-memory H2 with the console enabled, a prod profile stub that reads Postgres settings from environment variables, and a test profile pinned to H2 so integration tests never depend on the developer's local setup.

## Dependencies

- None (first task on the roadmap).

## Files to modify

| Action | Path | What |
|--------|------|------|
| MODIFY | `backend/src/main/resources/application.properties` | Shared base: app name, open-in-view off, actuator health exposure, JPA `ddl-auto=validate` default posture |
| MODIFY | `backend/pom.xml` | Add `org.springframework.boot:spring-boot-h2console` (required by Spring Boot 4 for console auto-config, discovered during verification) |
| MODIFY | `backend/src/main/resources/application-dev.properties` | Dev profile: H2 in-memory JDBC URL, H2 console at `/h2-console`, `ddl-auto=update`, SQL logging |
| CREATE | `backend/src/main/resources/application-prod.properties` | Prod profile: Postgres URL/username/password from `${PG*_ENV}` env vars, `ddl-auto=validate` |
| CREATE | `backend/src/test/resources/application-test.properties` | Test profile: H2 in-memory, quiet logs, `ddl-auto=create-drop` for tests |
| MODIFY | `backend/src/test/java/com/fintech/ledger/PaymentGateway/PaymentGatewayApplicationTests.java` | Annotate with `@ActiveProfiles("test")` so context loads under the test profile |
| MODIFY | `backend/src/test/java/com/fintech/ledger/PaymentGateway/repository/PaymentGatewayRepositoryTests.java` | Same `@ActiveProfiles("test")` |

## Implementation steps

1. Write the shared `application.properties`: keep `spring.application.name`, add `spring.jpa.open-in-view=false`, expose `health` via `management.endpoints.web.exposure.include`, set `spring.jpa.properties.hibernate.format_sql=false`.
2. Write `application-dev.properties`: `jdbc:h2:mem:paymentgateway;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false`, driver `org.h2.Driver`, `ddl-auto=update`, `spring.h2.console.enabled=true`, `spring.h2.console.path=/h2-console`, show-sql true with comment formatting. (Deviation: explicit `hibernate.dialect` omitted — Boot 4/Hibernate 7 auto-detects and warns on explicit dialect. Deviation: added `spring-boot-h2console` dependency, required in Boot 4 per docs.spring.io SQL reference.)
3. Write `application-prod.properties`: `spring.datasource.url=${PAYMENTGW_DB_URL}`, username `${PAYMENTGW_DB_USER}`, password `${PAYMENTGW_DB_PASSWORD}`, PostgreSQL dialect, `ddl-auto=validate`, Hikari pool sizing (max 10).
4. Write `application-test.properties`: H2 in-memory with create-drop, show-sql false.
5. Add `@ActiveProfiles("test")` to both test classes; run `./mvnw test` and confirm the open-in-view warning is gone and all 7 tests pass.
6. Smoke-verify the dev profile: start the app with `--spring.profiles.active=dev` on a free port, confirm the H2 console line and startup banner in logs, then stop it. Run full gate: `./mvnw test`.

## Out of scope

- Docker/deployment files, Flyway migrations (planned later if schema drift appears), security config, API endpoints.

## Definition of done

- [ ] App starts with `dev` profile and the H2 console banner appears in startup logs
- [ ] All 7 existing tests pass under the `test` profile with no open-in-view warning
- [ ] `prod` profile properties are env-var driven; no secrets in the repo
- [ ] `./mvnw test` exit code 0

## Verification criteria

- [ ] Every DoD checkbox evidenced by command output
- [ ] Tests · Build pass

## Rationale

Profile-per-environment is the mainstream Spring pattern and needs no new dependencies. Test pinning (`@ActiveProfiles("test")`) removes the hidden coupling between a developer's local default profile and CI results. Postgres env-var config keeps secrets out of git now, before the first deployment task forces the issue.
