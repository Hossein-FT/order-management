package com.example.ordermanagement.integration;

import com.example.ordermanagement.dto.CreateOrderRequest;
import com.example.ordermanagement.dto.CreateProductRequest;
import com.example.ordermanagement.dto.OrderItemRequest;
import com.example.ordermanagement.dto.OrderResponse;
import com.example.ordermanagement.dto.ProductResponse;
import com.example.ordermanagement.entity.Product;
import com.example.ordermanagement.repository.OrderRepository;
import com.example.ordermanagement.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderManagementIntegrationTest {

    // Run integration tests against PostgreSQL 17.
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("order_management")
                    .withUsername("app")
                    .withPassword("app");

    // Configure the application to use the Testcontainers database.
    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    // Reset database state before each test.
    @BeforeEach
    void cleanDatabase() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
    }

    // ثبت سفارش، محاسبه مبلغ، کاهش موجودی و بازگرداندن موجودی هنگام لغو سفارش.
    @Test
    void createsOrderAndCancellationRestoresStock() {
        ProductResponse product = createProduct("Keyboard", "25.00", 10);

        ResponseEntity<OrderResponse> createResponse = restTemplate.postForEntity(
                "/api/v1/orders",
                new CreateOrderRequest(List.of(new OrderItemRequest(product.id(), 2))),
                OrderResponse.class);

        // Verify order creation and stock reservation.
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).isNotNull();
        assertThat(createResponse.getBody().totalAmount()).isEqualByComparingTo("50.00");
        assertThat(productRepository.findById(product.id()).orElseThrow()
                .getStockQuantity()).isEqualTo(8);

        ResponseEntity<OrderResponse> cancelResponse = restTemplate.postForEntity(
                "/api/v1/orders/" + createResponse.getBody().id() + "/cancel",
                null,
                OrderResponse.class);

        // Verify cancellation restores the reserved stock.
        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelResponse.getBody()).isNotNull();
        assertThat(cancelResponse.getBody().status().name()).isEqualTo("CANCELLED");
        assertThat(productRepository.findById(product.id()).orElseThrow()
                .getStockQuantity()).isEqualTo(10);
    }

    // اگر موجودی یکی از محصولات کافی نباشد، موجودی سایر محصولات نیز نباید تغییر کند و سفارشی نباید ذخیره شود.
    @Test
    void insufficientStockRollsBackAllStockChanges() {
        ProductResponse first = createProduct("Keyboard", "25.00", 10);
        ProductResponse second = createProduct("Mouse", "10.00", 1);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/orders",
                new CreateOrderRequest(List.of(
                        new OrderItemRequest(first.id(), 2),
                        new OrderItemRequest(second.id(), 2))),
                String.class);

        // Verify the failed order leaves inventory and orders unchanged.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(productRepository.findById(first.id()).orElseThrow()
                .getStockQuantity()).isEqualTo(10);
        assertThat(productRepository.findById(second.id()).orElseThrow()
                .getStockQuantity()).isEqualTo(1);
        assertThat(orderRepository.count()).isZero();
    }

    // بررسی ارتباط محصول با آیتم سفارش و حفظ نام و قیمت تاریخی آن پس از تغییر محصول.
    @Test
    void orderItemReferencesProductAndPreservesHistoricalSnapshot() {
        ProductResponse product = createProduct("Keyboard", "25.00", 10);

        ResponseEntity<OrderResponse> createResponse = restTemplate.postForEntity(
                "/api/v1/orders",
                new CreateOrderRequest(List.of(new OrderItemRequest(product.id(), 2))),
                OrderResponse.class);

        // Verify the order item references the correct product and snapshots.
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).isNotNull();
        assertThat(createResponse.getBody().items()).hasSize(1);
        assertThat(createResponse.getBody().items().get(0).productId()).isEqualTo(product.id());
        assertThat(createResponse.getBody().items().get(0).productName()).isEqualTo("Keyboard");
        assertThat(createResponse.getBody().items().get(0).unitPrice()).isEqualByComparingTo("25.00");

        // Change the product and verify the order retains its original snapshots.
        Product entity = productRepository.findById(product.id()).orElseThrow();
        entity.setName("Gaming Keyboard");
        entity.setPrice(new BigDecimal("30.00"));
        productRepository.saveAndFlush(entity);

        ResponseEntity<OrderResponse> readResponse = restTemplate.getForEntity(
                "/api/v1/orders/" + createResponse.getBody().id(), OrderResponse.class);
        assertThat(readResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readResponse.getBody()).isNotNull();
        assertThat(readResponse.getBody().items().get(0).productName()).isEqualTo("Keyboard");
        assertThat(readResponse.getBody().items().get(0).unitPrice()).isEqualByComparingTo("25.00");
        assertThat(readResponse.getBody().totalAmount()).isEqualByComparingTo("50.00");
    }

    // Create a product through the public HTTP API.
    private ProductResponse createProduct(String name, String price, int stock) {
        ResponseEntity<ProductResponse> response = restTemplate.postForEntity(
                "/api/v1/products",
                new CreateProductRequest(name, new BigDecimal(price), stock),
                ProductResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }
}
