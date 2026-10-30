# Architecture

## Current implementation

The application currently uses a conventional Spring layered structure:

- `controller`: REST endpoints and HTTP mapping
- `dto`: request/response records and API error payload
- `service`: application use cases and transaction boundaries
- `repository`: Spring Data JPA persistence access
- `entity`: JPA entities
- `exception`: domain/business errors and HTTP exception mapping

The main use cases are `ProductService.create/get` and `OrderService.create/get/cancel`.

## Important business invariants

1. A product's price is loaded from persistence; clients cannot set an order-line price.
2. Order creation reserves stock and persists the order in one database transaction.
3. Product rows are locked in ascending product-ID order during order creation to reduce deadlock risk.
4. A cancelled order restores its reserved stock in the same transaction as the status change.
5. Only `PENDING` orders can be cancelled.
6. Order items store product name and unit price snapshots.

## Concurrency and consistency

`ProductRepository.findByIdForUpdate` and `OrderRepository.findByIdForUpdate` use pessimistic write locks. The transaction ensures that if any line cannot be fulfilled, prior stock mutations in that order attempt are rolled back. The Testcontainers integration test verifies this rollback behavior against PostgreSQL.

## Order item and product relationship

`OrderItem` references `Product` with a mandatory lazy `@ManyToOne` association and a `product_id` foreign key. It also stores `productName` and `unitPrice` as immutable-in-practice snapshots captured when the order is created. The association identifies the catalog product; the snapshot fields preserve the historical order facts if the catalog name or price later changes.

## Test layers

- Unit: `OrderServiceTest` (JUnit 5 and Mockito; business behavior without a database).
- Persistence integration: `OrderItemPersistenceIntegrationTest` (Spring Data JPA with PostgreSQL 17 via Testcontainers).
- Full integration: `OrderManagementIntegrationTest` (Spring Boot HTTP API with PostgreSQL 17 via Testcontainers).
