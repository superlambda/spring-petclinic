# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Spring PetClinic — a single-module Spring Boot 4.1.0 application (Java 17) using Spring MVC, Thymeleaf, Spring Data JPA, and an embedded H2 database by default. Maven is the primary build; a second, minimal Gradle build (`build.gradle`) exists but does not include the formatting, checkstyle, or CSS tooling.

The repository also serves as a cloud-native/SRE learning lab: Kubernetes manifests live in `k8s/`, the experiment log is `docs/setup.md` (Chinese), and the 4-week plan is `docs/cloude-native-sre-plan.md` (Chinese). `docs/week-1-acceptance.md` records the Week 1 acceptance.

## Commands

Use the Maven wrapper (`./mvnw`); Gradle equivalents are noted where they exist.

| Task | Command |
|---|---|
| Run locally (port 8080) | `./mvnw spring-boot:run` (`./gradlew bootRun`) |
| Build jar | `./mvnw package` → `target/spring-petclinic-4.0.0-SNAPSHOT.jar` (`./gradlew build` → `build/libs`) |
| Run built jar | `java -jar target/*.jar` |
| All tests | `./mvnw test` (`./gradlew test`) |
| Single test class | `./mvnw test -Dtest=OwnerControllerTests` |
| Single test method | `./mvnw test -Dtest=OwnerControllerTests#testInitCreationForm` |
| Format Java | `./mvnw spring-javaformat:apply` |
| Validate formatting and URLs only | `./mvnw validate` |
| Rebuild CSS from SCSS | `./mvnw package -Pcss` |
| Run with a database profile | `./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql` (or `postgres`) |
| Build production container | `docker build --pull=false --target production -t petclinic .` |
| Dev app + MySQL, debugger on 8000 | `docker compose -f docker-compose.dev.yml up --build` |
| Start databases only | `docker compose up` (MySQL :3306, PostgreSQL :5432) |

Build gotchas:

- The Maven `validate` phase runs spring-javaformat validation and the nohttp checkstyle rule, so `test`/`package` fail before compiling if Java is unformatted or any file contains a plaintext insecure URL (the HTTP scheme followed by a double slash). Use `https://`; nohttp scans the whole repository, including docs and this file.
- Spring Java Format uses tabs with width 4; `.editorconfig` mirrors this.
- `-Pcss` regenerates `src/main/resources/static/resources/css/petclinic.css` in place from `src/main/scss/`.
- Gradle has no format, checkstyle, or CSS tasks; use Maven when those matter.
- This lab cannot reach Docker Hub or GitHub. Base images and locally built `petclinic:vN` images are already on the machine; build with `--pull=false`. Maven Central is reachable.

## Architecture

- Feature-based packages under `org.springframework.samples.petclinic`: `owner` (Owner, Pet, PetType, Visit plus their controllers and repository), `vet` (Vet, Specialty, controllers, repository), `model` (BaseEntity, Person, NamedEntity base classes), `system` (WelcomeController, CrashController, CacheConfiguration), and `demo`.
- `demo` is the resilience harness. `DownstreamClient` calls the downstream service with `RestClient` and wraps the call in `@CircuitBreaker(name = "downstream")`; `DemoCallController` exposes `/demo/call?mode=...&delayMs=...`; `DownstreamController` exposes `/demo/downstream?mode=ok|slow|error&delayMs=...` and is deployed as the separate `petclinic-downstream` service. The fallback returns HTTP 200 with `status=degraded`, the exception and cause class names, and the current circuit state.
- There is no service layer. Controllers call Spring Data repositories directly, and domain logic lives in entities (e.g. `Owner.addPet`, `Owner.addVisit`, `Owner.getPet`) and controllers. `OwnerRepository` is the single repository for owners, pets, visits, and pet types.
- Persistence is JPA/Hibernate with `spring.jpa.hibernate.ddl-auto=none`. Schema and seed data are SQL scripts at `src/main/resources/db/${database}/schema.sql` and `data.sql`; the `database` property selects the directory — `h2` by default, `mysql` and `postgres` set by their profiles. Entity changes must be reflected in these SQL scripts (at minimum `db/h2/`, used by the default app and tests); keep the MySQL/Postgres scripts in sync because they run on every startup (`spring.sql.init.mode=always` with idempotent SQL).
- Views are Thymeleaf templates in `src/main/resources/templates`, sharing `fragments/layout.html`. Bootstrap and Font Awesome come from webjars. UI strings live in `src/main/resources/messages/messages*.properties`.
- Pagination is manual: controllers build `PageRequest.of(page - 1, 5)`, so the `page` request parameter is 1-based.
- Caching uses the JCache API backed by Caffeine. `system/CacheConfiguration` creates the `vets` cache, and both `VetRepository.findAll()` variants are `@Cacheable("vets")`, so `/vets` (JSON) and `/vets.html` share cached results.
- `/oups` intentionally throws an exception to exercise `templates/error.html`.
- Native image support: `PetClinicRuntimeHints` registers `db/`, `messages/`, and webjar classpath resources; add patterns there when new resources must be visible in a native build.
- Actuator exposes all endpoints (`management.endpoints.web.exposure.include=*`), including `/actuator/circuitbreakers` and `/actuator/circuitbreakerevents/downstream`. The H2 console is not enabled by default even though the README mentions it.
- Resilience4j 2.4.0 (`resilience4j-spring-boot4` + `spring-boot-starter-aspectj`). The `downstream` instance in `application.properties` uses a COUNT_BASED window of 10, minimum 5 calls, 50% failure-rate threshold, 10s open state, 3 half-open probes, and automatic OPEN→HALF_OPEN. `DownstreamClient` sets connect/read timeouts (defaults 500ms/1s) via `JdkClientHttpRequestFactory`; override with `demo.downstream.connect-timeout-ms` and `demo.downstream.read-timeout-ms`.
- CI (`.github/workflows/main.yml`) builds and pushes the Docker image on pushes to `main` only; it does not run tests.

## Kubernetes / SRE lab

- Work in namespace `petclinic`. Manifests: `k8s/petclinic.yml` (app Deployment + NodePort Service, probes, resources, graceful shutdown), `k8s/downstream.yml` (downstream Deployment + ClusterIP Service), `k8s/hpa.yml` (CPU 70%, 1–5 replicas), `k8s/metrics-server.yml`, `k8s/db.yml`.
- The HPA owns `spec.replicas`. Do not apply `k8s/petclinic.yml` while the HPA exists; the manifest's `replicas: 1` competes with autoscaling.
- Resilience4j breaker state is in-process. During breaker experiments the HPA is removed and the app runs as a single replica so state is observable; restore it with `kubectl apply -n petclinic -f k8s/hpa.yml` afterward.
- The app reads `DEMO_DOWNSTREAM_HOST` and `DEMO_DOWNSTREAM_PORT`; in-cluster the downstream is `petclinic-downstream:80`.
- Single-node Docker Desktop: node-level rescheduling and multi-node failure cannot be validated.
- For load tests, send traffic from inside the cluster to the Service, not through `kubectl port-forward`.

## Testing conventions

- Controller tests use `@WebMvcTest(...)` with `MockMvc` and `@MockitoBean` repositories (no database).
- Repository/domain tests use `@DataJpaTest` with `@AutoConfigureTestDatabase(replace = Replace.NONE)` (`ClinicServiceTests`), so they run against the configured datasource (H2 by default) and the seeded `db/h2/data.sql` rows.
- `PetClinicIntegrationTests` uses `@SpringBootTest(webEnvironment = RANDOM_PORT)`; it calls `VetRepository.findAll()` twice to exercise the cache. `PetClinicConcurrencyTests` uses the same setup to verify the duplicate-pet-name race is blocked.
- `MySqlIntegrationTests` / `PostgresIntegrationTests` use Testcontainers and need Docker; the MySQL test skips when Docker is unavailable.
- `ValidatorTests` builds a `LocalValidatorFactoryBean` directly to verify Bean Validation messages.
- Tests use JUnit 5, AssertJ, Hamcrest, and Mockito.

## Documentation

- `docs/setup.md` — environment setup and experiment log, in Chinese; append a `### 4.x` section for each hands-on stage and update the "当前状态" line.
- `docs/cloude-native-sre-plan.md` — 4-week learning plan and Week 2 checklist, in Chinese; tick items as they complete.
- `docs/week-1-acceptance.md` — Week 1 acceptance record.

## Working agreement and conventions

- The user runs cluster and image-build commands. Claude performs read-only diagnosis and provides complete, copy-pasteable commands/code, expected results, and explanations unless the user asks otherwise.
- Do not run `git commit`, `git push`, or change global config without an explicit request. If the user asks for a commit message, provide the message and stop; do not commit.
- Commit messages use English conventional-commit prefixes (`feat`, `fix`, `docs`, `chore`) in the imperative mood, for example `feat(resilience): add client timeouts and record timeout experiment`.
- Do not add AI attribution trailers such as `Co-Authored-By: Claude Code` to commit messages.
- Java files carry the Apache 2.0 license header; match the existing header.
- Keep comments and experiment docs in Chinese where the existing style is Chinese.
- Prefer plain-language explanations: one-sentence takeaway, a concrete analogy, then the mechanism.
