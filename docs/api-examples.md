# API Examples

Base URL: `http://localhost:8080`

Swagger UI: `http://localhost:8080/swagger-ui.html`

## Available Endpoints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/v1/products` | Create Product |
| GET | `/api/v1/products/{id}` | Get Product |
| POST | `/api/v1/orders` | Create Order |
| GET | `/api/v1/orders/{id}` | Get Order |
| POST | `/api/v1/orders/{id}/cancel` | Cancel Order |


## 1. Create Product

**POST** `/api/v1/products`

Request:
```json
{"name": "Laptop", "price": 1200.00, "stockQuantity": 10}
```

Response — `201 Created`:


## 2. Get Product

**GET** `/api/v1/products/1`

Response — `200 OK`:
```json
{"id": 1, "name": "Laptop", "price": 1200.00, "stockQuantity": 10, "active": true}
```

## 3. Create Order

**POST** `/api/v1/orders`

Request:
```json
{
  "items": [
    {"productId": 1, "quantity": 2},
    {"productId": 2, "quantity": 1}
  ]
}
```

Response — `201 Created`:


## 4. Get Order

**GET** `/api/v1/orders/1`

Response — `200 OK`:
```json
{
  "id": 1, "status": "PENDING", "totalAmount": 2650.00, "createdAt": "2026-10-30T10:30:00",
  "items": [
    {"productId": 1, "productName": "Laptop", "quantity": 2, "unitPrice": 1200.00, "lineTotal": 2400.00},
    {"productId": 2, "productName": "Mouse", "quantity": 1, "unitPrice": 250.00, "lineTotal": 250.00}
  ]
}
```

## 5. Cancel Order

**POST** `/api/v1/orders/1/cancel`

Response — `200 OK`:
```json
{
  "id": 1, "status": "CANCELLED", "totalAmount": 2650.00, "createdAt": "2026-10-30T10:30:00",
  "items": [
    {"productId": 1, "productName": "Laptop", "quantity": 2, "unitPrice": 1200.00, "lineTotal": 2400.00},
    {"productId": 2, "productName": "Mouse", "quantity": 1, "unitPrice": 250.00, "lineTotal": 250.00}
  ]
}
```
