# 🤖 AI Prompts Log — Vibe Coding Documentation
## Yuno Payment Orchestration System

> This document logs all AI-assisted prompts used during the development of this project,  
> aligned with Yuno's **vibe coding** philosophy: crafting high-quality prompts that generate  
> performant, production-aligned code.

---

## 📋 Overview

| Attribute | Value |
|-----------|-------|
| **Project** | Yuno Payment Orchestration System |
| **AI Tool Used** | Claude (Anthropic) |
| **Development Approach** | Prompt-driven iterative development with full understanding of each output |
| **Total Prompts** | 18 |
| **Phases Covered** | Architecture → Implementation → Testing → Documentation |

---

> ⚠️ **Integrity Note:**  
> All AI-generated code was reviewed, understood, and validated by the developer before inclusion.  
> Every design decision was deliberate — prompts were crafted to produce code aligned with  
> production standards. No code was blindly copy-pasted without understanding.

---

## 🏗 Phase 1 — Architecture & Design

---

### PROMPT-01 — System Architecture Design

**Goal:** Understand the overall system design before writing any code.

```
You are a senior Java backend engineer designing a payment orchestration system.

The system must:
- Accept payment requests via REST API
- Route CARD payments to Provider A and UPI payments to Provider B
- Support idempotency via a unique key per request
- Retry failed provider calls with exponential backoff
- Failover to a secondary provider if all retries fail
- Track payment status: INITIATED → PROCESSING → SUCCESS/FAILED
- Persist payments in PostgreSQL
- Cache idempotency keys in Redis

Design the layered architecture with:
1. Package structure
2. Class responsibilities
3. Data flow from request to response
4. Design patterns to apply

Output a clear architecture diagram and package breakdown.
```

**Why this prompt worked:**
Specifying all constraints upfront (routing rules, idempotency, retry, failover, persistence) in a single prompt forced the model to think holistically rather than in isolation. The explicit request for design patterns ensured the Strategy Pattern was surfaced for providers.

**Key output applied:** Layered architecture (Controller → Service → Routing → Provider → DB/Redis), Strategy Pattern for `PaymentProvider` interface.

---

### PROMPT-02 — Domain Modeling

**Goal:** Design the Payment entity and enums correctly before writing code.

```
Design a JPA Payment entity for a payment orchestration system in Java 21 + Spring Boot 3.5.

Requirements:
- UUID-based primary key (not auto-increment — explain why)
- Store payment method as enum: CARD, UPI
- Store status as enum: INITIATED, PROCESSING, SUCCESS, FAILED
- Use BigDecimal for amount (explain why not double/float)
- Auto-set createdAt and updatedAt using JPA lifecycle hooks
- Store idempotencyKey with a unique constraint
- Use Lombok to reduce boilerplate

Include explanation of each design decision, especially around financial data types
and ID generation strategy.
```

**Why this prompt worked:**
Asking for *explanations* alongside code forces the model to justify decisions, which also produces better code. Explicitly ruling out `double/float` and `auto-increment` IDs signals domain awareness and produces production-grade output.

**Key output applied:** `GenerationType.UUID`, `BigDecimal`, `@PrePersist`/`@PreUpdate` hooks, `@Enumerated(EnumType.STRING)`.

---

## ⚙️ Phase 2 — Core Implementation

---

### PROMPT-03 — Payment Request DTO with Validation

**Goal:** Generate a validated input DTO that rejects bad data at the API boundary.

```
Create a PaymentRequest DTO for a Spring Boot payment API in Java 21.

Validation requirements:
- amount: required, must be positive, max 10 integer digits and 2 decimal places
- currency: required, exactly 3 characters (ISO 4217 standard)
- paymentMethod: required enum (CARD or UPI) — invalid values should auto-reject
- merchantId: required, not blank
- description: optional, max 255 characters

Use:
- Jakarta Bean Validation annotations (@NotNull, @Positive, @Digits, @Size, @NotBlank)
- Lombok @Data, @Builder, @NoArgsConstructor, @AllArgsConstructor
- Custom validation messages for each field

Explain why BigDecimal is used over double for financial amounts.
```

**Key output applied:** `@Digits(integer=10, fraction=2)`, `@Size(min=3, max=3)` for currency, enum type rejection at deserialization level.

---

### PROMPT-04 — Strategy Pattern for Provider Connectors

**Goal:** Implement provider connectors using the Strategy Pattern for extensibility.

```
Implement two mock payment provider connectors in Java 21 + Spring Boot using the Strategy Pattern.

Interface: PaymentProvider with methods processPayment(PaymentRequest) and getProviderName()

Provider A (CARD): 
- Simulates 80% success rate
- Simulates 100-300ms network latency using Thread.sleep
- Returns ProviderResponse with success flag, providerName, message, transactionId
- Logs each attempt with SLF4J

Provider B (UPI):
- Same structure as Provider A
- Different provider name and log messages

Requirements:
- Use @Component on both connectors
- Use @Slf4j for logging
- Use @Builder on ProviderResponse
- Explain why the Strategy Pattern makes adding Provider C trivial in the future
```

**Why this prompt worked:**
Specifying the exact interface signature, success rate, latency simulation, and logging requirements produced connectors that work correctly with the retry mechanism. Asking for the Strategy Pattern explanation ensured clean interface design.

**Key output applied:** `PaymentProvider` interface, `@Slf4j`, `simulateNetworkDelay()` method, `ProviderResponse.builder()`.

---

### PROMPT-05 — Routing Engine with Failover

**Goal:** Build the routing engine that maps payment methods to providers and handles failover.

```
Build a RoutingEngine class in Java 21 for a payment orchestration system.

Requirements:
- route(PaymentMethod) returns the correct PaymentProvider: CARD→A, UPI→B
- getFailoverProvider(PaymentMethod) returns the OPPOSITE provider for failover
- Use Java 21 switch expressions with yield (not traditional switch/if-else)
- Use @Component and @RequiredArgsConstructor
- Log routing decisions with SLF4J at INFO level
- Log failover activations at WARN level
- Throw IllegalArgumentException for unsupported payment methods

Explain why Java 21 switch expressions are preferred over if-else chains here.
```

**Key output applied:** Java 21 `switch` with `yield`, `getFailoverProvider()` for cross-provider failover, WARN-level failover logging.

---

### PROMPT-06 — Idempotency with Two-Layer Redis + DB Fallback

**Goal:** Implement production-grade idempotency that survives Redis failures.

```
Implement idempotency for a Spring Boot payment service using two layers:

Layer 1 — Redis (primary):
- Key format: "idempotency:" + idempotencyKey
- TTL: 24 hours
- If key found: return cached PaymentResponse immediately
- If Redis throws exception: log warning, fall through to Layer 2

Layer 2 — PostgreSQL (fallback):
- Query payments table by idempotencyKey column
- If found: return mapped response
- If not found: proceed with new payment

Why two layers:
- Redis can go down; DB unique constraint is the final safety net
- DB has a unique constraint on idempotency_key — prevents duplicate inserts even if both cache layers miss

Use RedisTemplate<String, Object>, wrap Redis calls in try-catch for graceful degradation.
Explain the tradeoffs between Redis TTL and DB-level constraints.
```

**Key output applied:** `IDEMPOTENCY_PREFIX` constant, two-layer check in `createPayment()`, `try-catch` around all Redis operations, graceful degradation.

---

### PROMPT-07 — Retry with Exponential Backoff

**Goal:** Implement Spring Retry with exponential backoff on provider calls.

```
Add Spring Retry to a payment service in Spring Boot 3.5.

Requirements:
- Enable retry globally with @EnableRetry in a @Configuration class
- Use @Retryable on the provider call method:
  - maxAttempts: 3
  - backoff: start at 1000ms, multiplier 2.0 (exponential: 1s → 2s → 4s)
- The method should throw RuntimeException on provider failure to trigger retry
- After all retries exhausted, catch the exception and attempt failover

Explain why exponential backoff is better than fixed-interval retry in payment systems.
Also explain why @Retryable requires @EnableRetry to work — what happens if @EnableRetry is missing.
```

**Key output applied:** `@Retryable(maxAttempts=3, backoff=@Backoff(delay=1000, multiplier=2))`, `RetryConfig.java` with `@EnableRetry`, `RuntimeException` throw to trigger retry.

---

### PROMPT-08 — Service Layer Orchestration

**Goal:** Tie all components together in a clean, transactional service layer.

```
Implement the PaymentService orchestration layer in Spring Boot 3.5 + Java 21.

The service coordinates:
1. Idempotency check (Redis → DB fallback)
2. Save payment with INITIATED status
3. Route to correct provider via RoutingEngine
4. Update status to PROCESSING
5. Call provider with @Retryable (3 attempts, exponential backoff)
6. On retry exhaustion: attempt failover via getFailoverProvider()
7. Update final status to SUCCESS or FAILED
8. Cache result in Redis with 24h TTL

Use:
- @Service, @Transactional, @RequiredArgsConstructor, @Slf4j
- RedisTemplate<String, Object> for cache operations
- Separate private methods for: checkIdempotencyCache, cacheIdempotencyResponse,
  processWithRetryAndFailover, updateAndRespond, mapToResponse

Keep the public createPayment() method clean and readable — delegate to private methods.
```

**Key output applied:** 6-step flow with clear comments, private helper methods, `@Transactional` on `createPayment()`, clean separation of concerns.

---

### PROMPT-09 — Global Exception Handler

**Goal:** Handle all error types and return structured JSON responses.

```
Create a @RestControllerAdvice GlobalExceptionHandler for a Spring Boot payment API.

Handle these exception types:
1. PaymentNotFoundException → 404 with custom message
2. MethodArgumentNotValidException → 400 with fieldErrors map (field → message)
3. MissingRequestHeaderException → 400 with missing header name
4. IllegalArgumentException → 400 with message
5. Exception (catch-all) → 500 with generic message

Every response must include: status (int), error (reason phrase), message, timestamp (ISO string).
Use @ResponseStatus on each handler method.
Log errors with appropriate level: ERROR for 500s, WARN/ERROR for 404s.

Explain why a catch-all Exception handler is important and what it prevents.
```

**Key output applied:** `buildError()` helper method, `fieldErrors` map in validation response, timestamp in every error, catch-all `Exception` handler.

---

### PROMPT-10 — Redis Configuration

**Goal:** Configure Redis to store values as readable JSON, not binary.

```
Configure RedisTemplate<String, Object> in Spring Boot 3.5 for storing Java objects as JSON.

Requirements:
- Keys: StringRedisSerializer (human-readable string keys)
- Values: Jackson2JsonRedisSerializer with ObjectMapper
- Register JavaTimeModule to handle LocalDateTime serialization
- Disable WRITE_DATES_AS_TIMESTAMPS so dates are stored as ISO strings, not longs
- Call template.afterPropertiesSet() after configuration

Explain what happens if you don't configure a custom serializer
(hint: default Java serialization produces unreadable binary data in Redis).
```

**Key output applied:** `Jackson2JsonRedisSerializer`, `JavaTimeModule`, `WRITE_DATES_AS_TIMESTAMPS` disabled, `StringRedisSerializer` for keys.

---

## 🧪 Phase 3 — Testing

---

### PROMPT-11 — Unit Tests for PaymentService

**Goal:** Write meaningful unit tests with Mockito that prove the service logic works.

```
Write JUnit 5 unit tests for PaymentService in a Spring Boot payment orchestration system.

Use @ExtendWith(MockitoExtension.class) — no Spring context needed.

Test these scenarios:
POSITIVE:
1. Successful CARD payment — verify provider called once, status SUCCESS
2. Idempotency cache hit — verify provider NEVER called (verify(routingEngine, never()))
3. Fetch payment by ID — verify correct mapping
4. Redis unavailable — fallback to DB check

NEGATIVE:
5. Invalid payment ID — PaymentNotFoundException thrown
6. Primary provider fails — verify failover provider is called

Use @BeforeEach setUp() to create reusable test data.
Use @DisplayName for readable test names.
Use AAA pattern (Arrange / Act / Assert) with comments.
```

**Key output applied:** `verify(routingEngine, never())` for idempotency test, `@BeforeEach` setup, `assertThrows` for exception testing, AAA pattern with comments.

---

### PROMPT-12 — Unit Tests for RoutingEngine

**Goal:** Verify all routing and failover directions with simple, focused tests.

```
Write JUnit 5 unit tests for RoutingEngine using @ExtendWith(MockitoExtension.class).

Test all 4 routing scenarios:
1. CARD → returns ProviderAConnector
2. UPI → returns ProviderBConnector
3. CARD failover → returns ProviderBConnector
4. UPI failover → returns ProviderAConnector

Keep tests focused and simple — RoutingEngine has no async or DB behavior.
Use assertEquals(providerA, result) style assertions.
Add @DisplayName to each test.
```

**Key output applied:** Simple, focused tests with no unnecessary mocking, `assertEquals` on provider identity.

---

## 🏗 Phase 4 — Infrastructure & Documentation

---

### PROMPT-13 — Docker Compose Setup

**Goal:** Create a zero-friction local dev environment.

```
Create a Docker Compose file for local development of a Spring Boot payment system.

Services needed:
1. PostgreSQL 15:
   - container_name: yuno_postgres
   - DB: payment_db, user: yuno, password: yuno123
   - Port: 5432:5432
   - Named volume for data persistence

2. Redis 7:
   - container_name: yuno_redis
   - Port: 6379:6379

The goal is a reviewer can clone the repo, run 'docker-compose up -d',
then 'mvn spring-boot:run' and the entire system is live.
No manual database setup. No environment variables to configure.
```

**Key output applied:** Named volumes for PostgreSQL persistence, clean service names, all credentials pre-configured for zero-friction reviewer setup.

---

### PROMPT-14 — application.yml Configuration

**Goal:** Externalize all configuration properly with YAML.

```
Create application.yml for Spring Boot 3.5 connecting to PostgreSQL + Redis.

Include:
- datasource: PostgreSQL connection (localhost:5432/payment_db, user: yuno, pass: yuno123)
- jpa: ddl-auto: update, show-sql: true, format_sql: true, open-in-view: false
- redis: host: localhost, port: 6379
- server port: 8080
- Custom payment config namespace:
    payment.retry.max-attempts: 3
    payment.retry.delay: 1000
    payment.idempotency.ttl-hours: 24

Explain why open-in-view: false is important and what the Spring anti-pattern it prevents.
Explain why retry config should be in application.yml not hardcoded in Java.
```

**Key output applied:** `open-in-view: false`, custom `payment:` namespace for retry and idempotency config, YAML nesting over flat `.properties` format.

---

### PROMPT-15 — Payment Status Lifecycle Design

**Goal:** Ensure status transitions are correct and no invalid states are possible.

```
Design the payment status lifecycle for a payment orchestration system.

States: INITIATED, PROCESSING, SUCCESS, FAILED

Define:
1. Which transitions are valid (state machine)
2. Who is responsible for each transition (which layer/method)
3. What happens if the system crashes between transitions
4. How JPA @PrePersist handles the initial state

Ensure INITIATED is set automatically via @PrePersist so no caller can forget to set it.
Explain why storing status as EnumType.STRING (not ORDINAL) prevents data corruption
when enum values are reordered.
```

**Key output applied:** `@PrePersist` sets `INITIATED` automatically, `EnumType.STRING` for all enums, clear transition ownership per layer.

---

### PROMPT-16 — Input Validation Strategy

**Goal:** Validate all inputs at the API boundary, never in business logic.

```
Design a comprehensive input validation strategy for a payment REST API in Spring Boot.

For PaymentRequest, define validation rules for each field using Jakarta Bean Validation:
- amount: @NotNull + @Positive + @Digits(integer=10, fraction=2)
- currency: @NotBlank + @Size(min=3, max=3) — enforce ISO 4217
- paymentMethod: @NotNull — invalid enum values rejected at JSON deserialization
- merchantId: @NotBlank (rejects null, empty, whitespace)
- description: @Size(max=255) — optional field

Explain the difference between @NotNull and @NotBlank.
Explain why @Valid on the controller method parameter is required to trigger validation.
Show how MethodArgumentNotValidException is caught in @RestControllerAdvice
to return structured fieldErrors responses.
```

**Key output applied:** `@NotBlank` (not `@NotNull`) for String fields, `@Valid` on controller parameter, field-level error messages.

---

### PROMPT-17 — Failover Design Reasoning

**Goal:** Ensure the failover strategy is sound and documented.

```
In a payment orchestration system with two providers (A handles CARD, B handles UPI),
design the failover strategy when a provider is down.

Questions to answer:
1. If Provider A (CARD) fails after 3 retries, should we failover to Provider B?
   What are the tradeoffs?
2. How do we avoid infinite retry loops?
3. Should failover be transparent to the merchant, or should we notify?
4. How is payment status tracked during failover?

Implement getFailoverProvider(PaymentMethod) in the RoutingEngine that returns
the opposite provider for each payment method.
Document the design decision in code comments.
```

**Key output applied:** `getFailoverProvider()` returning the opposite provider, payment status stays `PROCESSING` during failover, transparent failover behavior.

---

### PROMPT-18 — Performance & Observability

**Goal:** Add meaningful logging and document performance characteristics.

```
Add structured observability to a Spring Boot payment orchestration system.

For each stage of payment processing, add @Slf4j logging:
- INFO: payment received, routing decision, provider called, result
- WARN: retry attempts, failover activation, Redis unavailable
- ERROR: unexpected exceptions, both providers failed

Also document expected performance metrics:
- Startup time
- P50 latency for successful payments
- Idempotency cache hit response time
- Retry delay schedule

Explain why structured logging (with payment IDs in every log line) is critical
for debugging payment issues in production.
```

**Key output applied:** Consistent log format with payment IDs, INFO/WARN/ERROR levels per scenario, performance metrics in README.

---

## 📊 Prompt Quality Summary

| Prompt | Phase | Lines of Code Generated | Key Pattern |
|--------|-------|------------------------|-------------|
| PROMPT-01 | Architecture | ~0 (design only) | Layered architecture |
| PROMPT-02 | Domain Model | ~60 | JPA lifecycle hooks |
| PROMPT-03 | DTO Design | ~40 | Bean Validation |
| PROMPT-04 | Providers | ~80 | Strategy Pattern |
| PROMPT-05 | Routing | ~40 | Java 21 switch expressions |
| PROMPT-06 | Idempotency | ~50 | Two-layer resilience |
| PROMPT-07 | Retry | ~20 | Exponential backoff |
| PROMPT-08 | Service | ~120 | Orchestration pattern |
| PROMPT-09 | Exception Handling | ~60 | @RestControllerAdvice |
| PROMPT-10 | Redis Config | ~40 | JSON serialization |
| PROMPT-11 | Service Tests | ~120 | AAA pattern + Mockito |
| PROMPT-12 | Routing Tests | ~50 | Focused unit tests |
| PROMPT-13 | Docker Compose | ~25 | Zero-friction setup |
| PROMPT-14 | YAML Config | ~30 | Externalized config |
| PROMPT-15 | Status Lifecycle | ~20 | State machine design |
| PROMPT-16 | Validation Strategy | ~30 | API boundary validation |
| PROMPT-17 | Failover Design | ~30 | Resilience patterns |
| PROMPT-18 | Observability | ~20 | Structured logging |

---

## 🎯 Key Takeaways

**What made these prompts effective:**

1. **Context-first** — every prompt starts with the system context so the AI understands the bigger picture before generating code
2. **Explicit constraints** — specifying what NOT to do (`not double/float`, `not auto-increment`) produces better code than only specifying what to do
3. **Ask for explanations** — prompts that request justification of decisions produce more thoughtful, production-aligned output
4. **One concern per prompt** — focused prompts targeting a single class/pattern produce cleaner code than broad "build everything" prompts
5. **Reference real patterns** — naming patterns (Strategy, layered architecture) steers the output toward industry-standard solutions

---

*Prompts Log — Yuno Backend Developer (Java Core) Assessment*  
*Developer: Chetna Shedame | chetnashedame54@gmail.com*