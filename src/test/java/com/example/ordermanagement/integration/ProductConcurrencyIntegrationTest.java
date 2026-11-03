package com.example.ordermanagement.integration;

import com.example.ordermanagement.dto.CreateOrderRequest;
import com.example.ordermanagement.dto.OrderItemRequest;
import com.example.ordermanagement.entity.Product;
import com.example.ordermanagement.exception.BusinessException;
import com.example.ordermanagement.repository.OrderRepository;
import com.example.ordermanagement.repository.ProductRepository;
import com.example.ordermanagement.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@Import(OrderService.class)
class ProductConcurrencyIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("order_management")
                    .withUsername("app")
                    .withPassword("app");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private OrderService orderService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private OrderRepository orderRepository;

    private Long productId;

    @BeforeEach
    void setUp() {
        Product product = productRepository.saveAndFlush(
                new Product("Keyboard", new BigDecimal("25.00"), 1));
        productId = product.getId();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldAllowOnlyOneOrderWhenTwoRequestsArriveConcurrently() throws Exception {
        int numberOfThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch ready = new CountDownLatch(numberOfThreads);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Callable<Boolean> orderTask = () -> {
                ready.countDown();
                start.await();
                try {
                    CreateOrderRequest request = new CreateOrderRequest(
                            List.of(new OrderItemRequest(productId, 1)));
                    orderService.create(request);
                    return true;
                } catch (BusinessException exception) {
                    return false;
                }
            };

            Future<Boolean> firstRequest = executor.submit(orderTask);
            Future<Boolean> secondRequest = executor.submit(orderTask);

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            boolean firstResult = firstRequest.get(15, TimeUnit.SECONDS);
            boolean secondResult = secondRequest.get(15, TimeUnit.SECONDS);
            int successfulOrders = (firstResult ? 1 : 0) + (secondResult ? 1 : 0);

            assertThat(successfulOrders).isEqualTo(1);
            assertThat(productRepository.findById(productId).orElseThrow().getStockQuantity()).isZero();
            assertThat(orderRepository.count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }
}
