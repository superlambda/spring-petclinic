# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Spring PetClinic — a single-module Spring Boot 3.0.4 application (Java 17) using Spring MVC, Thymeleaf, Spring Data JPA, and an embedded H2 database by default. Maven is the primary build; a second, minimal Gradle build (`build.gradle`) exists but does not include the formatting, checkstyle, or CSS tooling. `docs/cloude-native-sre-plan.md` is a personal cloud-native/SRE learning plan (in Chinese), not application documentation.

## Commands

Use the Maven wrapper (`./mvnw`); Gradle equivalents are noted where they exist.

| Task | Command |
|---|---|
| Run locally (port 8080) | `./mvnw spring-boot:run` (`./gradlew bootRun`) |
| Build jar | `./mvnw package` → `target/spring-petclinic-3.0.0-SNAPSHOT.jar` (`./gradlew build` → `build/libs`) |
| Run built jar | `java -jar target/*.jar` |
| All tests | `./mvnw test` (`./gradlew test`) |
| Single test class | `./mvnw test -Dtest=OwnerControllerTests` |
| Single test method | `./mvnw test -Dtest=OwnerControllerTests#testInitCreationForm` |
| Format Java | `./mvnw spring-javaformat:apply` |
| Validate formatting and URLs only | `./mvnw validate` |
| Rebuild CSS from SCSS | `./mvnw package -Pcss` |
| Run with a database profile | `./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql` (or `postgres`) |
| Build production container | `docker build --target production -t petclinic .` |
| Dev app + MySQL, debugger on 8000 | `docker compose -f docker-compose.dev.yml up --build` |
| Start databases only | `docker compose up` (MySQL :3306, PostgreSQL :5432) |

Build gotchas:

- The Maven `validate` phase runs spring-javaformat validation and the nohttp checkstyle rule, so `test`/`package` fail before compiling if Java is unformatted or any file contains an `http://` URL. Use `https://`; nohttp scans the whole repository, including docs.
- Spring Java Format uses tabs with width 4; `.editorconfig` mirrors this.
- `-Pcss` regenerates `src/main/resources/static/resources/css/petclinic.css` in place from `src/main/scss/`.
- Gradle has no format, checkstyle, or CSS tasks; use Maven when those matter.

## Architecture

- Feature-based packages under `org.springframework.samples.petclinic`: `owner` (Owner, Pet, PetType, Visit plus their controllers and repository), `vet` (Vet, Specialty, controllers, repository), `model` (BaseEntity, Person, NamedEntity base classes), and `system` (WelcomeController, CrashController, CacheConfiguration).
- There is no service layer. Controllers call Spring Data repositories directly, and domain logic lives in entities (e.g. `Owner.addPet`, `Owner.addVisit`, `Owner.getPet`) and controllers. `OwnerRepository` is the single repository for owners, pets, visits, and pet types.
- Persistence is JPA/Hibernate with `spring.jpa.hibernate.ddl-auto=none`. Schema and seed data are SQL scripts at `src/main/resources/db/${database}/schema.sql` and `data.sql`; the `database` property selects the directory — `h2` by default, `mysql` and `postgres` set by their profiles. Entity changes must be reflected in these SQL scripts (at minimum `db/h2/`, used by the default app and tests); keep the MySQL/Postgres scripts in sync because they run on every startup (`spring.sql.init.mode=always` with idempotent SQL).
- Views are Thymeleaf templates in `src/main/resources/templates`, sharing `fragments/layout.html`. Bootstrap and Font Awesome come from webjars. UI strings live in `src/main/resources/messages/messages*.properties`.
- Pagination is manual: controllers build `PageRequest.of(page - 1, 5)`, so the `page` request parameter is 1-based.
- Caching uses the JCache API backed by Caffeine. `system/CacheConfiguration` creates the `vets` cache, and both `VetRepository.findAll()` variants are `@Cacheable("vets")`, so `/vets` (JSON) and `/vets.html` share cached results.
- `/oups` intentionally throws an exception to exercise `templates/error.html`.
- Native image support: `PetClinicRuntimeHints` registers `db/`, `messages/`, and webjar classpath resources; add patterns there when new resources must be visible in a native build.
- Actuator exposes all endpoints (`management.endpoints.web.exposure.include=*`). The H2 console is not enabled by default even though the README mentions it.
- CI (`.github/workflows/main.yml`) builds and pushes the Docker image on pushes to `main` only; it does not run tests.

## Testing conventions

- Controller tests use `@WebMvcTest(...)` with `MockMvc` and `@MockBean` repositories (no database).
- Repository/domain tests use `@DataJpaTest` with `@AutoConfigureTestDatabase(replace = Replace.NONE)` (`ClinicServiceTests`), so they run against the configured datasource (H2 by default) and the seeded `db/h2/data.sql` rows.
- `PetClinicIntegrationTests` uses `@SpringBootTest(webEnvironment = RANDOM_PORT)`; it calls `VetRepository.findAll()` twice to exercise the cache.
- `ValidatorTests` builds a `LocalValidatorFactoryBean` directly to verify Bean Validation messages.
- Tests use JUnit 5, AssertJ, Hamcrest, and Mockito.

## Conventions

- Java files carry the Apache 2.0 license header; match the existing header.
