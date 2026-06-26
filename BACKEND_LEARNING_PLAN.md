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
  - [ ] then replicate to order-service / monolith
- [ ] **Repository slice tests** — `@DataJpaTest` against an in-memory or Testcontainers Postgres.
- [x] **Controller (web) tests** — MockMvc: request/response, validation→400, error shapes ✅ 2026-06-26
  - `ProductControllerTest` (4 tests, standalone MockMvc — the `@WebMvcTest` slice was moved out of starter-test in Boot 4.0; on Boot 3.x use `@WebMvcTest` + `@MockitoBean`).
- [ ] **Integration tests** — `@SpringBootTest` + **Testcontainers** (real Postgres). The gold standard; strong interview signal.
- [ ] **Add a `test` stage to the CI pipelines** — `mvn test` as a gate before build (closes the CI/CD scorecard gap too).
- [ ] **Talking points to nail:** test pyramid, mock vs stub vs fake, `@Mock` vs `@SpringBootTest`, why Testcontainers > H2.

**Start here:** full test suite for **one service (product-service)** as the template → replicate to the others.

---

## Phase 2 — Spring deep-dives (classic test topics, exercised on the project)
- [ ] **`@Transactional`** — propagation (REQUIRED/REQUIRES_NEW), isolation, rollback rules, self-invocation pitfall. Exercise: order-creation flow.
- [ ] **Spring Data JPA pitfalls** — N+1 problem (detect + fix with fetch joins / `@EntityGraph`), lazy vs eager, `@Query`, pagination. Exercise: product/category queries.
- [ ] **Bean lifecycle & DI** — scopes, `@PostConstruct`, constructor injection, `@Conditional`, configuration.
- [ ] **Spring Security (backend)** — proper server-side auth, JWT validation filter, method security (`@PreAuthorize`). Exercise: secure the APIs server-side (currently auth is frontend-side).
- [ ] **Caching** — Spring Cache abstraction (`@Cacheable`), eviction. Exercise: product reads.
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
