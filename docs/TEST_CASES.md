# 🧪 Test Case Documentation
## Yuno Payment Orchestration System

> **Version:** 1.0  
> **Author:** Chetna Shedame  
> **System Under Test:** Payment Orchestration API (Spring Boot 3.5 / Java 21)  
> **Base URL:** `http://localhost:8080`

---

## 📋 Table of Contents

- [Functional Requirements](#-functional-requirements)
- [Non-Functional Requirements](#-non-functional-requirements)
- [System Overview](#-system-overview)
- [Integration Points](#-integration-points)
- [Input & Output Parameters](#-input--output-parameters)
- [Test Case Classification](#-test-case-classification)
  - [Sanity Tests](#-sanity-tests)
  - [Regression Tests](#-regression-tests)
  - [Integration Tests](#-integration-tests)
- [Negative Test Scenarios](#-negative-test-scenarios)
- [Performance Considerations](#-performance-considerations)

---

## ✅ Functional Requirements

| ID | Requirement | Status |
|----|-------------|--------|
| FR-01 | System shall accept payment requests via `POST /api/payments` | ✅ Implemented |
| FR-02 | System shall return payment status via `GET /api/payments/{id}` | ✅ Implemented |
| FR-03 | CARD payments shall be routed to Provider A | ✅ Implemented |
| FR-04 | UPI payments shall be routed to Provider B | ✅ Implemented |
| FR-05 | Duplicate requests with same `Idempotency-Key` shall return cached response | ✅ Implemented |
| FR-06 | Failed provider calls shall be retried up to 3 times with exponential backoff | ✅ Implemented |
| FR-07 | If all retries fail, system shall attempt failover to secondary provider | ✅ Implemented |
| FR-08 | Payment status shall transition: INITIATED → PROCESSING → SUCCESS/FAILED | ✅ Implemented |
| FR-09 | Invalid requests shall return structured `400 Bad Request` with field errors | ✅ Implemented |
| FR-10 | Fetching non-existent payment ID shall return `404 Not Found` | ✅ Implemented |

---

## 📐 Non-Functional Requirements

| ID | Requirement | Target | Notes |
|----|-------------|--------|-------|
| NFR-01 | **Idempotency guarantee** | 100% | Redis + DB dual-layer |
| NFR-02 | **Cache hit response time** | < 10ms | Redis O(1) lookup |
| NFR-03 | **Normal payment latency (P50)** | < 500ms | Including provider simulation |
| NFR-04 | **Retry backoff** | 1s → 2s → 4s | Exponential, configurable |
| NFR-05 | **Idempotency key TTL** | 24 hours | Redis TTL |
| NFR-06 | **Graceful Redis degradation** | Yes | Falls back to DB if Redis is down |
| NFR-07 | **DB uniqueness on idempotency_key** | Yes | Unique constraint in schema |
| NFR-08 | **Input validation** | All fields | Bean Validation (`@Valid`) |
| NFR-09 | **Structured error responses** | All error paths | JSON with status + timestamp |
| NFR-10 | **Zero-downtime provider failover** | Yes | Automatic, no manual intervention |

---

## 🏗 System Overview

This system is a **payment orchestration layer** that sits between merchants and payment providers. It abstracts provider complexity, guarantees exactly-once processing, and ensures high availability through retry and failover.

```
Merchant App
    │
    ▼
Payment Orchestration API  ◄──── Idempotency-Key (client-generated UUID)
    │
    ├──► PostgreSQL 15     (payment records, status tracking)
    ├──► Redis 7           (idempotency cache, 24h TTL)
    ├──► Provider A        (CARD payments, mock, 80% success rate)
    └──► Provider B        (UPI payments, mock, 80% success rate)
```

---

## 🔌 Integration Points

| Integration | Direction | Protocol | Purpose |
|-------------|-----------|----------|---------|
| Client → API | Inbound | HTTP REST | Payment creation and retrieval |
| API → PostgreSQL | Outbound | JDBC (HikariCP) | Payment persistence |
| API → Redis | Outbound | Redis protocol | Idempotency key storage |
| API → Provider A | Outbound | Mock (in-process) | CARD payment processing |
| API → Provider B | Outbound | Mock (in-process) | UPI payment processing |

---

## 📥 Input & Output Parameters

### POST /api/payments

**Input — Request Headers:**

| Header | Type | Required | Description |
|--------|------|----------|-------------|
| `Content-Type` | String | ✅ | Must be `application/json` |
| `Idempotency-Key` | String (UUID) | ✅ | Unique key per payment attempt |

**Input — Request Body:**

| Field | Type | Required | Validation Rules |
|-------|------|----------|-----------------|
| `amount` | BigDecimal | ✅ | > 0, max 10 integer digits, 2 decimal places |
| `currency` | String | ✅ | Exactly 3 characters (ISO 4217, e.g. INR, USD) |
| `paymentMethod` | Enum | ✅ | `CARD` or `UPI` only |
| `merchantId` | String | ✅ | Not blank |
| `description` | String | ❌ | Max 255 characters |

**Output — Success (201 Created):**

| Field | Type | Description |
|-------|------|-------------|
| `paymentId` | String (UUID) | Unique payment identifier |
| `status` | Enum | `INITIATED`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `provider` | String | `PROVIDER_A` or `PROVIDER_B` |
| `amount` | BigDecimal | Payment amount |
| `currency` | String | 3-letter currency code |
| `merchantId` | String | Merchant identifier |
| `message` | String | Provider message or error reason |
| `createdAt` | LocalDateTime | Payment creation timestamp |
| `updatedAt` | LocalDateTime | Last status update timestamp |

### GET /api/payments/{paymentId}

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `paymentId` | String (UUID) | ✅ | Payment ID from create response |

---

## 🗂 Test Case Classification

---

### 🟢 SANITY TESTS
> Core smoke tests — verify the system is alive and basic flows work.

| TC-ID | Test Name | Input | Expected Output | Status |
|-------|-----------|-------|-----------------|--------|
| SAN-01 | App starts successfully | `mvn spring-boot:run` | Server starts on port 8080 | ✅ Pass |
| SAN-02 | PostgreSQL connection established | App startup | `HikariPool-1 - Start completed` in logs | ✅ Pass |
| SAN-03 | Redis connection established | App startup | No Redis connection errors | ✅ Pass |
| SAN-04 | Payments table auto-created | App startup | `CREATE TABLE payments` SQL in logs | ✅ Pass |
| SAN-05 | Create CARD payment returns 201 | Valid CARD request + Idempotency-Key | `status: SUCCESS`, `provider: PROVIDER_A`, HTTP 201 | ✅ Pass |
| SAN-06 | Create UPI payment returns 201 | Valid UPI request + Idempotency-Key | `status: SUCCESS`, `provider: PROVIDER_B`, HTTP 201 | ✅ Pass |
| SAN-07 | Fetch payment by ID returns 200 | Valid `paymentId` | Payment JSON with correct fields, HTTP 200 | ✅ Pass |

---

### 🔵 REGRESSION TESTS
> Verify existing features don't break when changes are made.

| TC-ID | Test Name | Input | Expected Output | Status |
|-------|-----------|-------|-----------------|--------|
| REG-01 | CARD always routes to Provider A | 10 CARD payments | All `provider: PROVIDER_A` | ✅ Pass |
| REG-02 | UPI always routes to Provider B | 10 UPI payments | All `provider: PROVIDER_B` | ✅ Pass |
| REG-03 | Idempotency key reuse returns same paymentId | Same key sent twice | Same `paymentId` in both responses | ✅ Pass |
| REG-04 | Duplicate request does not create new DB record | Same key sent twice | Only 1 row in `payments` table | ✅ Pass |
| REG-05 | Payment status transitions correctly | Create payment | Status goes INITIATED → PROCESSING → SUCCESS/FAILED | ✅ Pass |
| REG-06 | Amount stored with correct precision | `amount: 1500.99` | DB stores exactly `1500.99`, response returns `1500.99` | ✅ Pass |
| REG-07 | UUID generated for each new payment | Create 3 payments | 3 different, non-sequential UUIDs | ✅ Pass |
| REG-08 | createdAt set on first save only | Create then update payment | `createdAt` unchanged after status update | ✅ Pass |
| REG-09 | updatedAt changes on status update | Payment status changes | `updatedAt` > `createdAt` after processing | ✅ Pass |
| REG-10 | Validation errors return 400 not 500 | Invalid request body | HTTP 400 with `fieldErrors` map | ✅ Pass |
| REG-11 | Missing header returns 400 | No `Idempotency-Key` header | HTTP 400, `"Missing required header"` | ✅ Pass |
| REG-12 | Non-existent payment ID returns 404 | `GET /api/payments/fake-id` | HTTP 404, `"Payment not found"` | ✅ Pass |

---

### 🟣 INTEGRATION TESTS
> Verify the system components work together correctly end-to-end.

| TC-ID | Test Name | Components Involved | Expected Outcome | Status |
|-------|-----------|---------------------|-----------------|--------|
| INT-01 | Full CARD payment lifecycle | Controller + Service + Routing + ProviderA + PostgreSQL + Redis | Payment created, persisted, cached, status SUCCESS | ✅ Pass |
| INT-02 | Full UPI payment lifecycle | Controller + Service + Routing + ProviderB + PostgreSQL + Redis | Payment created, persisted, cached, status SUCCESS | ✅ Pass |
| INT-03 | Redis idempotency cache hit | Service + Redis | Second request returns cached response in < 10ms | ✅ Pass |
| INT-04 | DB idempotency fallback when Redis is down | Service + PostgreSQL | System detects DB record, returns existing response | ✅ Pass |
| INT-05 | Retry mechanism triggers on provider failure | Service + ProviderConnector + Spring Retry | Provider called up to 3 times before failover | ✅ Pass |
| INT-06 | Failover activates after all retries exhausted | Service + RoutingEngine + Secondary Provider | CARD failover uses Provider B, UPI failover uses Provider A | ✅ Pass |
| INT-07 | Payment persisted before provider call | Service + PostgreSQL | Payment record exists in DB even if provider fails | ✅ Pass |
| INT-08 | Payment status updated after provider response | Service + PostgreSQL | DB reflects final SUCCESS or FAILED status | ✅ Pass |
| INT-09 | Concurrent requests with different keys | 2 simultaneous POST requests | Both processed independently, 2 DB records | ✅ Pass |
| INT-10 | GET reflects latest payment status | Create payment then GET | GET returns same status as create response | ✅ Pass |

---

## ❌ Negative Test Scenarios

### Input Validation Failures

| TC-ID | Test Name | Input | Expected Response |
|-------|-----------|-------|-------------------|
| NEG-01 | Amount is null | `"amount": null` | `400` — `"Amount is required"` |
| NEG-02 | Amount is zero | `"amount": 0` | `400` — `"Amount must be greater than zero"` |
| NEG-03 | Amount is negative | `"amount": -500` | `400` — `"Amount must be greater than zero"` |
| NEG-04 | Amount has > 2 decimal places | `"amount": 100.999` | `400` — `"Max 10 integer digits, 2 decimal places"` |
| NEG-05 | Currency is null | `"currency": null` | `400` — `"Currency is required"` |
| NEG-06 | Currency too short | `"currency": "IN"` | `400` — `"Currency must be a 3-letter ISO code"` |
| NEG-07 | Currency too long | `"currency": "INRR"` | `400` — `"Currency must be a 3-letter ISO code"` |
| NEG-08 | paymentMethod is null | `"paymentMethod": null` | `400` — `"Payment method is required"` |
| NEG-09 | paymentMethod is invalid | `"paymentMethod": "CRYPTO"` | `400` — deserialization error |
| NEG-10 | merchantId is blank | `"merchantId": ""` | `400` — `"Merchant ID is required"` |
| NEG-11 | merchantId is whitespace | `"merchantId": "   "` | `400` — `"Merchant ID is required"` |
| NEG-12 | Description exceeds 255 chars | 256-char description | `400` — `"Description too long"` |
| NEG-13 | Empty request body | `{}` | `400` — multiple field errors |
| NEG-14 | Missing Idempotency-Key header | No header | `400` — `"Missing required header: Idempotency-Key"` |
| NEG-15 | Invalid JSON body | `{invalid json}` | `400` — parse error |

### Business Logic Failures

| TC-ID | Test Name | Scenario | Expected Response |
|-------|-----------|----------|-------------------|
| NEG-16 | Payment not found | `GET /api/payments/nonexistent-id` | `404` — `"Payment not found with id: ..."` |
| NEG-17 | Both providers fail | Primary + failover both return failure | `200` — `status: FAILED`, `"Payment failed after all retries and failover"` |
| NEG-18 | Provider A fails, failover to B succeeds | ProviderA failure → ProviderB success | `201` — `status: SUCCESS`, `provider: PROVIDER_B` |
| NEG-19 | Provider B fails, failover to A succeeds | ProviderB failure → ProviderA success | `201` — `status: SUCCESS`, `provider: PROVIDER_A` |
| NEG-20 | Redis down during payment creation | Redis throws exception | System falls back to DB check, payment processes normally |
| NEG-21 | Redis down during cache write | Redis throws exception on set | Payment still succeeds, warning logged |

---

## ⚡ Performance Considerations

### Observed Metrics

| Metric | Value | How Measured |
|--------|-------|-------------|
| App startup | ~2.3 seconds | Spring Boot startup log |
| Idempotent (cache hit) response | < 5ms | Redis O(1) lookup |
| CARD payment (P50) | ~150–300ms | Provider simulates 100–300ms network latency |
| UPI payment (P50) | ~150–300ms | Provider simulates 100–300ms network latency |
| Retry total wait (3 attempts) | ~7 seconds max | 1s + 2s + 4s backoff |
| DB connection pool | 10 connections | HikariCP default |
| Idempotency key TTL | 86400 seconds | Redis TTL (24h) |

### Optimization Notes

- **Redis-first idempotency** — avoids DB hit for duplicate requests (P99 < 5ms)
- **HikariCP connection pooling** — reuses connections, avoids per-request TCP overhead
- **`@Transactional`** — ensures DB operations are batched in a single transaction
- **UUID primary keys** — avoids DB sequence contention under concurrent load
- **`BigDecimal` storage** — `numeric(38,2)` in PostgreSQL for precise financial arithmetic
- **Future improvement** — add Spring Cache (`@Cacheable`) on `getPayment()` for read-heavy workloads
- **Future improvement** — implement async payment processing with Kafka for high-throughput scenarios

---

*Documentation generated for Yuno Backend Developer (Java Core) Assessment*