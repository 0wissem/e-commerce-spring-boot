# Backend Learning Plan — Java / Spring (technical-test prep)

> **Goal:** sharpen core **Java + Spring** to pass backend technical tests (and lead a
> backend team). The infra/DevOps arc (k8s, Terraform, EKS) is done and parked — this
> plan is **backend only**.
>
> **Method:** this repo is the **lab**. Each item is exercised on the real codebase
> (monolith = customers, product-service = products/categories, order-service = orders,
> gateway). Tick items as we finish them. Work top to bottom — priority order.

---

## Phase 0 — Calibrate (fill this in)
- [ ] **Target test format?** take-home project / live-coding / Q&A-whiteboard / algorithms / a mix.
  - _Notes:_ ______
- [ ] If algorithms are in scope → run the separate **Algorithms track** (bottom) in parallel.

---

## Phase 1 — Testing layer ⭐ THE priority (biggest gap + most-tested skill)
*The project has zero automated tests. Interviewers probe this hardest, and writing tests forces deep understanding of the code.*

- [~] **Unit tests** — JUnit 5 + Mockito. Services + mappers. Mock the repository ports.
  - [x] `ProductServiceTest` (5 tests: stubbing, exception, verify/never, soft-delete, real mapper) ✅ 2026-06-26
  - [ ] `ProductMapperTest` (categories mapping, finalPrice rule)
  - [x] replicated to order-service (`OrderServiceTest` 5 + `OrderControllerTest` 4) & monolith (`CustomerServiceTest` 5 + `CustomerControllerTest` 4 + smoke) ✅ 2026-06-29
- [x] **Persistence tests** — real Postgres via Testcontainers ✅ 2026-06-26 (`@DataJpaTest` slice isn't on the Boot 4 classpath → used `@SpringBootTest`).
- [x] **Controller (web) tests** — MockMvc: request/response, validation→400, error shapes ✅ 2026-06-26
  - `ProductControllerTest` (4 tests, standalone MockMvc — the `@WebMvcTest` slice was moved out of starter-test in Boot 4.0; on Boot 3.x use `@WebMvcTest` + `@MockitoBean`).
- [x] **Integration tests** — `@SpringBootTest` + **Testcontainers** (real Postgres) ✅ 2026-06-26
  - `ProductJpaRepositoryTest` (4: save/find, findByNameIgnoreCase, **full-text tsvector search**, soft-delete via @SQLRestriction) + `AbstractIntegrationTest` (singleton-container base) + Flyway runs the real schema. Key learnings: why Testcontainers > H2, @DynamicPropertySource, @Transactional rollback, the 1st-level-cache flush/clear subtlety.
- [x] **CI test gate** ✅ 2026-06-29 — `.github/workflows/tests.yml` runs `mvn test` for all 3 services (matrix) on every push/PR; Testcontainers works on the ubuntu runner. + JaCoCo coverage on all 3 (product 64%, order 65%, monolith 69%). 48 tests total. (Optional: branch protection to make the check *required* to merge.)
- [ ] **Talking points to nail:** test pyramid, mock vs stub vs fake, `@Mock` vs `@SpringBootTest`, why Testcontainers > H2.

**Start here:** full test suite for **one service (product-service)** as the template → replicate to the others.

---

## Phase 2 — Spring deep-dives (classic test topics, exercised on the project)
- [x] **`@Transactional`** — rollback proven ✅ 2026-06-26 — `OrderTransactionalTest` (order-service): create() rolls back fully when a mid-flight product lookup throws (no partial order persisted), commits on success. Talking points: rollback on unchecked only (rollbackFor for checked), propagation (REQUIRED/REQUIRES_NEW), isolation, readOnly, **self-invocation** pitfall (proxy only intercepts external calls), and the **HTTP-in-transaction anti-pattern** spotted in create() (holds the DB connection during network I/O → move lookups outside the tx).
- [~] **Spring Data JPA pitfalls** — N+1 problem, lazy vs eager, `@Query`, pagination.
  - [x] **N+1** demonstrated + fixed ✅ 2026-06-26 — `ProductNPlusOneTest` measures query counts via Hibernate Statistics: `findAll()` = 1+N, `findAllWithCategories()` (`@EntityGraph` fetch join + DISTINCT) = 1. Talking points: detect (show-sql/Statistics/p6spy), fix (@EntityGraph/@BatchSize/2-step), pitfalls (DISTINCT, fetch-join+pagination warning, cartesian explosion).
  - [ ] lazy vs eager deep-dive, `@BatchSize`, pagination edge cases (optional)
- [ ] **Bean lifecycle & DI** — scopes, `@PostConstruct`, constructor injection, `@Conditional`, configuration.
- [ ] **Spring Security (backend)** — proper server-side auth, JWT validation filter, method security (`@PreAuthorize`). Exercise: secure the APIs server-side (currently auth is frontend-side).
- [x] **Caching** ✅ 2026-07-03 — product-service `getById` `@Cacheable("products")`, `@CacheEvict` on update/delete; Caffeine backend (TTL 10min + max 10k). `ProductCacheTest` proves it by counting DB reads (2 reads → 1 findById; update evicts → re-read). Talking points: proxy-based (self-invocation caveat like @Transactional), bounded caches (TTL/size or leak+staleness), providers simple/Caffeine/Redis (distributed for multi-instance), cache invalidation is the hard part.
- [ ] **Validation & global error handling** — already present (`GlobalExceptionHandler`); be able to explain it cold.

---

## Phase 3 — Core Java fluency (language-depth probes)
- [ ] **Collections & Streams** — map/filter/reduce/collect/groupingBy, when streams hurt.
- [ ] **Optional** — proper use, anti-patterns.
- [ ] **Records, sealed classes, pattern matching** (Java 17+).
- [ ] **`equals`/`hashCode`/immutability**, value semantics.
- [ ] **Generics** — bounded types, wildcards (PECS).
- [ ] **Exceptions** — checked vs unchecked, try-with-resources.

## Phase 4 — Concurrency (frequent, often a weak spot)
- [ ] Threads, `Runnable`/`Callable`, `ExecutorService`, thread pools.
- [ ] `CompletableFuture` — compose async calls. Exercise: parallelize order-service's cross-service calls (product + customer fetch).
- [ ] Synchronization, `volatile`, `Atomic*`, common race conditions & deadlocks.
- [ ] (Java 21) virtual threads — know what they are.

---

## Separate track — Algorithms / Data Structures (only if tests include coding rounds)
- [ ] Arrays/strings, hashmaps, two-pointers, sliding window.
- [ ] Trees/graphs (BFS/DFS), recursion, sorting, big-O.
- [ ] Timed practice (LeetCode easy→medium). *Not tied to this project — pure prep.*

## Mock-test practice (when Phases 1–2 feel solid)
- [ ] Do a **timed take-home**: build a small Spring REST API from scratch, **with tests**, in 2–3 hrs. Simulates the real thing.
- [ ] Be able to **whiteboard** the architecture of THIS project (hexagonal, microservices, transactions) — it's a strong portfolio talking point.

---

## Progress log
- 2026-06-24 — Plan created. Pivoted from infra/DevOps back to backend Java/Spring for technical-test prep. Next: Phase 1 (testing), starting with product-service.
- 2026-06-26 — Phase 1 started. `ProductServiceTest` written + green (5 tests). Covered: AAA, @Mock/@ExtendWith, stubbing (thenReturn/thenAnswer), state vs interaction assertions (verify/never/verifyNoInteractions), assertThatThrownBy, real-mapper-not-mocked.
- 2026-06-26 — `ProductControllerTest` written + green (4 tests, MockMvc). Covered: MockMvc perform/andExpect, status + jsonPath, exception→404 via @RestControllerAdvice, the validation→400 flow (@Valid → MethodArgumentNotValidException → handler). Used standalone MockMvc (Boot 4 moved @WebMvcTest out of starter-test). Next: C — persistence (`@DataJpaTest` → Testcontainers).
- 2026-06-26 — C done. `ProductJpaRepositoryTest` (4) + `AbstractIntegrationTest` (singleton Postgres container) green against real Postgres + Flyway schema. Covered: Testcontainers vs H2 (tsvector full-text search only runs on real PG), @DynamicPropertySource, @Transactional rollback, Hibernate 1st-level-cache (flush/clear), @SQLRestriction soft-delete. **Full product-service suite: 14 tests green.** Note: Boot 4.0 split all test slices (@WebMvcTest/@DataJpaTest) into per-tech modules not on the classpath — used standalone MockMvc + @SpringBootTest; on Boot 3.x (most interviews) use the slices. Next: CI test stage, or Phase 2 (transactions/JPA pitfalls).
- 2026-06-26 — Phase 2 started: **N+1** demonstrated + fixed on Product.categories (`ProductNPlusOneTest`, query-counted via Hibernate Statistics; added `findAllWithCategories()` with `@EntityGraph`). 16 product-service tests green. Next Phase 2: `@Transactional` (propagation/isolation/rollback/self-invocation) on the order-creation flow, then Spring Security / caching.
- 2026-06-26 — `@Transactional` rollback proven: `OrderTransactionalTest` (order-service) — set up order-service Testcontainers infra (AbstractIntegrationTest + pom pin 1.20.6), mocked the HTTP clients with @MockitoBean, showed create() rolls back fully on a mid-flight failure + commits on success. Spotted + noted the HTTP-in-transaction anti-pattern. order-service now has its first tests (2). Next Phase 2: Spring Security (backend) or caching.
- 2026-06-29 — **Test suites completed for all 3 services.** product-service 16, order-service 11 (added `OrderServiceTest` unit + `OrderControllerTest` web), monolith 10 (set up Testcontainers infra + `CustomerServiceTest` unit + `CustomerControllerTest` web + fixed the context-load smoke test). **37 tests total, all green.** gateway left untested (routing config, ~no logic). Next: CI test stage, then Phase 2 (Spring Security / caching) and Phase 3/4 (core Java / concurrency).
