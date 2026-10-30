# Order Management API

A Java 21 / Spring Boot REST API for a small but realistic order-management domain. The project demonstrates transactional business rules, PostgreSQL persistence, validation, consistent error responses, pessimistic locking, and automated unit/integration tests.

## Stack

- Java 21
- Spring Boot 3.5.16
- Maven 3.9+
- PostgreSQL 17 Alpine
- Spring Data JPA / Hibernate
- JUnit 5, Mockito, AssertJ, PostgreSQL 17 via Testcontainers, Testcontainers
- OpenAPI / Swagger UI

## Run the complete application

Requirements: Docker Desktop with Docker Compose enabled.

```bash
docker compose up --build
```

- API base URL: `http://localhost:8080/api/v1`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI document: `http://localhost:8080/v3/api-docs`

Stop the containers:

```bash
docker compose down
```

Remove the persisted local database volume too (destructive):

```bash
docker compose down -v
```

## Run locally

Start PostgreSQL 17 first, then run:

```bash
mvn clean test
mvn spring-boot:run
```

Default database configuration is `order_management` / `app` / `app`. Override with `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `SERVER_PORT`.

## Automated tests

```bash
mvn test
```

- `OrderServiceTest`: fast unit tests using Mockito; no database required.
- `OrderItemPersistenceIntegrationTest`: JPA/database integration test using PostgreSQL 17 via Testcontainers; verifies the `OrderItem -> Product` foreign-key relationship and historical snapshots.
- `OrderManagementIntegrationTest`: full Spring Boot + real PostgreSQL integration tests through Testcontainers. Docker must be available for these tests; they are automatically skipped when Docker is unavailable.

The tests cover order creation/cancellation, stock rollback on business failure, the `OrderItem -> Product` relationship, and preservation of the product name and unit price captured at order time.

## API endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/api/v1/products` | Create a product |
| `GET` | `/api/v1/products/{id}` | Get a product |
| `POST` | `/api/v1/orders` | Create an order and reserve stock |
| `GET` | `/api/v1/orders/{id}` | Get an order |
| `POST` | `/api/v1/orders/{id}/cancel` | Cancel a pending order and restore stock |

## Example requests

Create a product:

```http
POST /api/v1/products
Content-Type: application/json

{
  "name": "Keyboard",
  "price": 25.00,
  "stockQuantity": 10
}
```

Create an order:

```http
POST /api/v1/orders
Content-Type: application/json

{
  "items": [
    { "productId": 1, "quantity": 2 }
  ]
}
```

Cancel an order:

```http
POST /api/v1/orders/1/cancel
```

## Business and architecture notes

- Product price is read from the database; the client cannot choose the order price.
- Stock is reserved and an order is persisted in one transaction.
- Products are locked in ascending ID order during order creation to reduce deadlock risk.
- Cancellation is allowed only while an order is `PENDING`; stock restoration and status change are atomic.
- `OrderItem.product` is a real `@ManyToOne` relationship to `Product` using `product_id`.
- Order items also snapshot `productName` and `unitPrice`, so later product edits do not rewrite historical orders. API responses use the related product ID and the historical name/price snapshots.
- Business conflicts return HTTP 409; request validation errors return HTTP 400.
- Hibernate manages schema creation/update (`ddl-auto: update`); Flyway is intentionally not used for this assignment.
- The current code is organized by technical layers (`controller`, `service`, `repository`, `entity`, `dto`). The next architectural hardening step for a strict Clean Architecture requirement is to separate domain/application ports from JPA persistence models and web adapters.

See `docs/` for business rules, architecture notes, and API examples.


Database consistency note: all database-backed integration tests use PostgreSQL 17 via Testcontainers. Docker must be running for these tests.
