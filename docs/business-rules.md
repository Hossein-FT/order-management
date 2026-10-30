# Business Rules

- The client submits product IDs and quantities; price always comes from the database.
- Order creation is transactional. If any item is invalid or out of stock, all changes roll back.
- Duplicate product IDs in a request are combined into one line item.
- Order items preserve the product name and unit price at purchase time.
- Only `PENDING` orders can be cancelled. Cancellation restores inventory within the same transaction.
- Product rows are locked pessimistically while stock is modified; IDs are locked in ascending order.
- Monetary values use `BigDecimal` and PostgreSQL `NUMERIC`.
- This sample supports `PENDING` and `CANCELLED` only; payment/fulfilment are out of scope.
