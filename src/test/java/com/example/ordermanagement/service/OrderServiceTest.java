package com.example.ordermanagement.service;

import com.example.ordermanagement.dto.CreateOrderRequest;
import com.example.ordermanagement.dto.OrderItemRequest;
import com.example.ordermanagement.dto.OrderResponse;
import com.example.ordermanagement.entity.CustomerOrder;
import com.example.ordermanagement.entity.Product;
import com.example.ordermanagement.exception.BusinessException;
import com.example.ordermanagement.repository.OrderRepository;
import com.example.ordermanagement.repository.ProductRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderServiceTest {

    private OrderRepository orderRepository;
    private ProductRepository productRepository;
    private OrderService service;

    @BeforeEach
    void setUp() {
        // Create mocked dependencies for isolated unit tests.
        orderRepository = mock(OrderRepository.class);
        productRepository = mock(ProductRepository.class);
        service = new OrderService(orderRepository, productRepository);
    }

    @Test
    void createsOrderCalculatesTotalAndDecreasesStock() {
        Product p1 = productWithId(1L, "Keyboard", "25.00", 10);
        Product p2 = productWithId(2L, "Mouse", "10.50", 5);

        // IDs are assigned by the database in production; repository lookup uses requested IDs.
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(p1));
        when(productRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(p2));
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        OrderResponse response = service.create(new CreateOrderRequest(List.of(new OrderItemRequest(1L, 2), new OrderItemRequest(2L, 1))));

        // Verify the order total and reserved stock quantities.
        assertEquals(new BigDecimal("60.50"), response.totalAmount());
        assertEquals(8, p1.getStockQuantity());
        assertEquals(4, p2.getStockQuantity());
        verify(orderRepository).save(any(CustomerOrder.class));
    }

    @Test
    void orderItemKeepsProductNameAndPriceSnapshots() {
        Product product = productWithId(1L, "Keyboard", "25.00", 10);
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = service.create(
                new CreateOrderRequest(List.of(new OrderItemRequest(1L, 2)))
        );

        // Change the catalog product after order creation.
        product.setName("Gaming Keyboard");
        product.setPrice(new BigDecimal("30.00"));

        // Verify that the order retains its original product snapshots.
        assertEquals("Keyboard", response.items().get(0).productName());
        assertEquals(new BigDecimal("25.00"), response.items().get(0).unitPrice());
        assertEquals(new BigDecimal("50.00"), response.totalAmount());
    }

    @Test
    void rejectsInsufficientStock() {
        Product product = productWithId(1L, "Keyboard", "25.00", 1);
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

        // Verify that insufficient stock causes a conflict without saving an order.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(new CreateOrderRequest(List.of(new OrderItemRequest(1L, 2)))));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals(1, product.getStockQuantity());
        verify(orderRepository, never()).save(any());
    }

    private Product productWithId(Long id, String name, String price, int stock) {
        Product product = new Product(name, new BigDecimal(price), stock);
        // Assign an ID without requiring database persistence.
        ReflectionTestUtils.setField(product, "id", id);

        return product;
    }
}

