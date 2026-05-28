# yuno-payment-orchestration
Simplified payment orchestration system — Yuno Backend Assessment
# 💳 Yuno Payment Orchestration System

> A production-grade simplified payment orchestration engine built with **Java 21 + Spring Boot 3.5**, inspired by real-world platforms like Yuno. Supports intelligent routing, idempotency, retry with exponential backoff, and automatic failover.

---

## 📌 Table of Contents

- [Overview](#-overview)
- [Architecture](#-architecture)
- [Tech Stack](#-tech-stack)
- [Features](#-features)
- [Project Structure](#-project-structure)
- [Quick Start](#-quick-start)
- [API Reference](#-api-reference)
- [Payment Flow](#-payment-flow)
- [Error Handling](#-error-handling)
- [Running Tests](#-running-tests)
- [Performance Considerations](#-performance-considerations)
- [Design Decisions](#-design-decisions)

---

## 🌐 Overview

This system acts as a **payment orchestration layer** — it sits between merchants and payment providers, intelligently routing transactions, handling failures gracefully, and guaranteeing exactly-once processing through idempotency.

```
Merchant → Yuno Orchestration Layer → Provider A (CARD)
                                    → Provider B (UPI)
```

Built across 3 phases aligned with Yuno's engineering process:

| Phase | What was done |
|-------|--------------|
| **Analysis** | Defined functional + non-functional requirements, integration points, data models |
| **Development** | Implemented orchestration logic, routing engine, retry/failover, idempotency |
| **QA & Go-Live** | Unit tests, negative scenarios, Docker-based deployment |

---

## 🏗 Architecture

```
┌─────────────────────────────────────────────────────────┐
│                        CLIENT                           │
│         POST /api/payments   GET /api/payments/{id}     │
└──────────────────────┬──────────────────────────────────┘
                       │ HTTP + Idempotency-Key header
┌──────────────────────▼──────────────────────────────────┐
│                  CONTROLLER LAYER                       │
│    PaymentController — validates input, routes calls    │
└──────────────────────┬──────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────┐
│           SERVICE LAYER (Orchestration Engine)          │
│  • Idempotency check (Redis → DB fallback)              │
│  • Payment lifecycle management                         │
│  • Retry with exponential backoff (@Retryable)          │
│  • Failover coordination                                │
└──────┬───────────────────────────────────┬──────────────┘
       │                                   │
┌──────▼──────┐                   ┌────────▼────────┐
│   ROUTING   │                   │  PERSISTENCE    │
│   ENGINE    │                   │    LAYER        │
│ CARD → A    │                   │  PostgreSQL 15  │
│ UPI  → B    │                   │  (JPA/Hibernate)│
└──────┬──────┘                   └────────┬────────┘
       │                                   │
┌──────▼──────────────┐         ┌──────────▼────────┐
│  PROVIDER CONNECTORS│         │  IDEMPOTENCY STORE│
│  ┌────────────────┐ │         │  Redis 7           │
│  │ Provider A     │ │         │  TTL: 24 hours     │
│  │ (CARD payments)│ │         │  Key: idempotency: │
│  └────────────────┘ │         │  + idempotencyKey  │
│  ┌────────────────┐ │         └───────────────────┘
│  │ Provider B     │ │
│  │ (UPI payments) │ │
│  └────────────────┘ │
└─────────────────────┘
```

---

## 🛠 Tech Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| Language | Java 21 | LTS version, modern switch expressions |
| Framework | Spring Boot 3.5 | REST APIs, DI, JPA, Redis |
| Database | PostgreSQL 15 | Payment persistence |
| Cache | Redis 7 | Idempotency store |
| ORM | Hibernate / Spring Data JPA | DB operations |
| Retry | Spring Retry | `@Retryable` with exponential backoff |
| Build | Maven 3.9 | Dependency management |
| Infra | Docker Compose | Zero-friction local setup |
| Testing | JUnit 5 + Mockito | Unit + integration tests |
| Logging | SLF4J + Logback | Structured request tracing |

---

## ✨ Features

### ✅ Core Functional Features

| Feature | Description |
|---------|-------------|
| **Create Payment API** | `POST /api/payments` — accepts CARD and UPI payments |
| **Fetch Payment API** | `GET /api/payments/{id}` — retrieve payment status at any point |
| **Intelligent Routing** | CARD → Provider A, UPI → Provider B |
| **Idempotency** | Same `Idempotency-Key` → same response, no duplicate charges |
| **Retry + Backoff** | 3 attempts with exponential backoff (1s → 2s → 4s) |
| **Automatic Failover** | Primary provider fails → secondary provider takes over |
| **Status Tracking** | `INITIATED → PROCESSING → SUCCESS / FAILED` |

### ✅ Non-Functional Features

| Feature | Description |
|---------|-------------|
| **Two-layer idempotency** | Redis first, DB fallback if Redis is down |
| **Graceful degradation** | System works even if Redis is unavailable |
| **Structured logging** | Every payment step logged for traceability |
| **Input validation** | `@Valid`, `@NotNull`, `@Positive`, `@Digits` on all DTOs |
| **Clean error responses** | Structured JSON errors with status + timestamp |
| **DB-level uniqueness** | Unique constraint on `idempotency_key` column |

---

## 📁 Project Structure

```
payment-orchestration/
├── compose.yaml                          # Docker Compose (PostgreSQL + Redis)
├── pom.xml                               # Maven dependencies
├── docs/
│   ├── TEST_CASES.md                     # Full test case documentation
│   └── PROMPTS_LOG.md                    # AI prompts used during development
└── src/
    ├── main/
    │   ├── java/com/yuno/payment/
    │   │   ├── PaymentOrchestrationApplication.java
    │   │   ├── config/
    │   │   │   ├── RedisConfig.java       # Redis JSON serialization
    │   │   │   └── RetryConfig.java       # @EnableRetry
    │   │   ├── controller/
    │   │   │   └── PaymentController.java # REST endpoints
    │   │   ├── dto/
    │   │   │   ├── PaymentRequest.java    # Validated input DTO
    │   │   │   └── PaymentResponse.java   # API response DTO
    │   │   ├── exception/
    │   │   │   ├── GlobalExceptionHandler.java
    │   │   │   ├── PaymentNotFoundException.java
    │   │   │   └── PaymentProcessingException.java
    │   │   ├── model/
    │   │   │   ├── Payment.java           # JPA entity
    │   │   │   ├── PaymentMethod.java     # Enum: CARD, UPI
    │   │   │   └── PaymentStatus.java     # Enum: INITIATED, PROCESSING, SUCCESS, FAILED
    │   │   ├── provider/
    │   │   │   ├── PaymentProvider.java   # Interface (Strategy Pattern)
    │   │   │   ├── ProviderAConnector.java# CARD payments (80% success simulation)
    │   │   │   ├── ProviderBConnector.java# UPI payments (80% success simulation)
    │   │   │   └── ProviderResponse.java  # Provider result DTO
    │   │   ├── repository/
    │   │   │   └── PaymentRepository.java # JPA repository
    │   │   ├── routing/
    │   │   │   └── RoutingEngine.java     # Routes by PaymentMethod
    │   │   └── service/
    │   │       └── PaymentService.java    # Core orchestration logic
    │   └── resources/
    │       └── application.yml
    └── test/
        └── java/com/yuno/payment/
            ├── service/
            │   └── PaymentServiceTest.java
            └── routing/
                └── RoutingEngineTest.java
```

---

## 🚀 Quick Start

### Prerequisites
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) running
- Java 21+
- Maven 3.9+

### 1. Clone the repository
```bash
git clone https://github.com/chetnashedame/yuno-payment-orchestration.git
cd yuno-payment-orchestration
```

### 2. Start infrastructure (PostgreSQL + Redis)
```bash
docker-compose up -d
```

Verify containers are running:
```bash
docker ps
# You should see: yuno_postgres + yuno_redis
```

### 3. Run the application
```bash
mvn spring-boot:run
```

App starts on → **http://localhost:8080**

Hibernate auto-creates the `payments` table on first run. No SQL scripts needed.

### 4. Run tests
```bash
mvn test
```

---

## 📡 API Reference

### POST /api/payments — Create Payment

**Headers:**
```
Content-Type: application/json
Idempotency-Key: <unique-uuid-per-request>
```

**Request Body:**
```json
{
  "amount": 1500.00,
  "currency": "INR",
  "paymentMethod": "CARD",
  "merchantId": "merchant-001",
  "description": "Order #4521"
}
```

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `amount` | BigDecimal | ✅ | > 0, max 10 digits + 2 decimal places |
| `currency` | String | ✅ | Exactly 3 characters (ISO 4217) |
| `paymentMethod` | Enum | ✅ | `CARD` or `UPI` |
| `merchantId` | String | ✅ | Not blank |
| `description` | String | ❌ | Max 255 characters |

**Success Response (201 Created):**
```json
{
  "paymentId": "91d8bcfd-43de-4899-b07c-da9152ae46dc",
  "status": "SUCCESS",
  "provider": "PROVIDER_A",
  "amount": 1500.00,
  "currency": "INR",
  "merchantId": "merchant-001",
  "message": "Payment processed successfully",
  "createdAt": "2026-05-29T04:23:30.618698",
  "updatedAt": "2026-05-29T04:23:30.618713"
}
```

---

### GET /api/payments/{paymentId} — Fetch Payment

```bash
curl http://localhost:8080/api/payments/{paymentId}
```

**Success Response (200 OK):**
```json
{
  "paymentId": "91d8bcfd-43de-4899-b07c-da9152ae46dc",
  "status": "SUCCESS",
  "provider": "PROVIDER_A",
  "amount": 1500.00,
  "currency": "INR",
  "merchantId": "merchant-001",
  "createdAt": "2026-05-29T04:23:30.618698",
  "updatedAt": "2026-05-29T04:23:30.618713"
}
```

---

### Sample cURL Commands

**CARD payment:**
```bash
curl -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "amount": 1500.00,
    "currency": "INR",
    "paymentMethod": "CARD",
    "merchantId": "merchant-001",
    "description": "Order #4521"
  }'
```

**UPI payment:**
```bash
curl -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "amount": 500.00,
    "currency": "INR",
    "paymentMethod": "UPI",
    "merchantId": "merchant-001"
  }'
```

**Test idempotency (run same key twice):**
```bash
curl -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: fixed-key-001" \
  -d '{"amount":1000.00,"currency":"INR","paymentMethod":"CARD","merchantId":"m-001"}'
# Run this command twice — you'll get the same paymentId both times
```

---

## 🔄 Payment Flow

```
Request arrives
      │
      ▼
Is Idempotency-Key in Redis?
  YES ──────────────────────────────► Return cached response (no processing)
  NO
      │
      ▼
Is Idempotency-Key in DB?
  YES ──────────────────────────────► Return DB response (no processing)
  NO
      │
      ▼
Save payment (status: INITIATED)
      │
      ▼
Route by paymentMethod
  CARD ──► Provider A
  UPI  ──► Provider B
      │
      ▼
Set status: PROCESSING → Call Provider
      │
      ├── SUCCESS ──► Set status: SUCCESS → Cache in Redis → Return response
      │
      └── FAILURE (retry up to 3x with exponential backoff)
              │
              └── Still failing? → Try FAILOVER provider
                      │
                      ├── SUCCESS ──► Set status: SUCCESS → Return response
                      └── FAILURE ──► Set status: FAILED → Return response
```

---

## ❌ Error Handling

| Scenario | HTTP Status | Response |
|----------|-------------|----------|
| Missing `Idempotency-Key` header | 400 | `"Missing required header: Idempotency-Key"` |
| Invalid `paymentMethod` value | 400 | `"fieldErrors": {"paymentMethod": "..."}` |
| Amount is null or negative | 400 | `"fieldErrors": {"amount": "Amount is required"}` |
| Currency not 3 characters | 400 | `"fieldErrors": {"currency": "..."}` |
| Payment ID not found | 404 | `"Payment not found with id: ..."` |
| Both providers fail | 200 | `"status": "FAILED"` |
| Unexpected server error | 500 | `"An unexpected error occurred"` |

---

## 🧪 Running Tests

```bash
mvn test
```

Test coverage includes:
- ✅ Successful CARD payment routed to Provider A
- ✅ Successful UPI payment routed to Provider B
- ✅ Idempotency — duplicate key returns cached response
- ✅ Redis down — fallback to DB idempotency check
- ✅ Provider failure — failover to secondary provider
- ✅ Invalid payment ID — `PaymentNotFoundException` thrown
- ✅ Routing engine — CARD→A, UPI→B, failover directions

See [`docs/TEST_CASES.md`](docs/TEST_CASES.md) for full test case documentation.

---

## ⚡ Performance Considerations

| Metric | Observed | Notes |
|--------|----------|-------|
| App startup time | ~2.3 seconds | Spring Boot + PostgreSQL + Redis |
| Successful payment (P50) | ~150–300ms | Includes simulated provider latency |
| Idempotent response (cache hit) | < 5ms | Redis lookup only |
| Retry delay | 1s → 2s → 4s | Exponential backoff |
| DB connection pool | HikariCP default | 10 connections |
| Redis TTL | 24 hours | Idempotency key expiry |

---

## 🎯 Design Decisions

**Why `BigDecimal` for amount?**
`double` and `float` have floating-point precision errors. `0.1 + 0.2 ≠ 0.3` in IEEE 754. In payment systems, precision errors cause real money discrepancies. `BigDecimal` is the industry standard.

**Why Strategy Pattern for providers?**
Both `ProviderAConnector` and `ProviderBConnector` implement the `PaymentProvider` interface. The routing engine returns a `PaymentProvider` — the service doesn't need to know which provider it's calling. Adding Provider C in the future requires zero changes to existing code.

**Why two-layer idempotency?**
Redis is fast but can go down. The DB has a unique constraint on `idempotency_key` as a safety net. If Redis is unavailable, no duplicate payments are possible — the DB constraint prevents it at the storage level.

**Why `GenerationType.UUID` for IDs?**
Auto-increment integers are predictable (attackers can enumerate IDs). UUIDs are unpredictable and work across distributed systems without coordination.

**Why H2 was not used?**
PostgreSQL is what Yuno uses in production. Using the same DB in the assessment demonstrates production-readiness. Docker Compose keeps setup frictionless.

---

## 👩‍💻 Author

**Chetna Shedame**
Backend Developer — Java | Spring Boot | Microservices
📧 chetnashedame54@gmail.com