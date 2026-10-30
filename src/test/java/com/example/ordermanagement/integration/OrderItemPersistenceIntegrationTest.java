package com.example.ordermanagement.integration;

import com.example.ordermanagement.entity.CustomerOrder;
import com.example.ordermanagement.entity.OrderItem;
import com.example.ordermanagement.entity.Product;
import com.example.ordermanagement.repository.OrderRepository;
import com.example.ordermanagement.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
class OrderItemPersistenceIntegrationTest {

    // Use PostgreSQL 17 to verify persistence behavior against the target database.
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("order_management")
                    .withUsername("app")
                    .withPassword("app");

    // Configure Spring Data JPA using the container's runtime connection details.
    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsProductRelationshipAndOrderTimeSnapshots() {
        Product product = productRepository.saveAndFlush(
                new Product("Keyboard", new BigDecimal("25.00"), 10));
        CustomerOrder order = new CustomerOrder();
        order.addItem(new OrderItem(product, product.getName(), 2, product.getPrice()));
        order.setTotalAmount(new BigDecimal("50.00"));
        CustomerOrder saved = orderRepository.saveAndFlush(order);
        Long orderId = saved.getId();
        Long productId = product.getId();

        // Clear the persistence context to ensure subsequent reads load database state.
        entityManager.clear();
        Product reloadedProduct = productRepository.findById(productId).orElseThrow();
        reloadedProduct.setName("Gaming Keyboard");
        reloadedProduct.setPrice(new BigDecimal("30.00"));
        productRepository.saveAndFlush(reloadedProduct);

        // Reload the order to verify that its historical snapshots remain unchanged.
        entityManager.clear();

        CustomerOrder reloadedOrder = orderRepository.findDetailedById(orderId).orElseThrow();
        OrderItem item = reloadedOrder.getItems().get(0);
        assertThat(item.getProduct().getId()).isEqualTo(productId);
        assertThat(item.getProductName()).isEqualTo("Keyboard");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("25.00");
        assertThat(reloadedOrder.getTotalAmount()).isEqualByComparingTo("50.00");
    }
}
