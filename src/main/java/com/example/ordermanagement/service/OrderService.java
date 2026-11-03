package com.example.ordermanagement.service;

import com.example.ordermanagement.dto.CreateOrderRequest;
import com.example.ordermanagement.dto.OrderItemRequest;
import com.example.ordermanagement.dto.OrderItemResponse;
import com.example.ordermanagement.dto.OrderResponse;
import com.example.ordermanagement.entity.CustomerOrder;
import com.example.ordermanagement.entity.OrderItem;
import com.example.ordermanagement.entity.OrderStatus;
import com.example.ordermanagement.entity.Product;
import com.example.ordermanagement.exception.BusinessException;
import com.example.ordermanagement.exception.ResourceNotFoundException;
import com.example.ordermanagement.repository.OrderRepository;
import com.example.ordermanagement.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        Map<Long, Integer> quantities = new TreeMap<>();
        for (OrderItemRequest item : request.items()) {
            quantities.merge(item.productId(), item.quantity(), Integer::sum);
        }
        CustomerOrder order = new CustomerOrder();
        BigDecimal total = BigDecimal.ZERO;
        // Sorted IDs make lock acquisition order deterministic and reduce deadlock risk.
        for (var entry : quantities.entrySet()) {
            Product product = productRepository.findByIdForUpdate(entry.getKey())
                    .orElseThrow(() -> new ResourceNotFoundException("Product " + entry.getKey() + " was not found"));
            if (!product.isActive())
                throw new BusinessException(HttpStatus.CONFLICT, "PRODUCT_INACTIVE", "Product " + product.getId() + " is inactive");

            int quantity = entry.getValue();
            if (product.getStockQuantity() < quantity)
                throw new BusinessException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "Insufficient stock for product " + product.getId());

            product.decreaseStock(quantity);
            order.addItem(new OrderItem(product, product.getName(), quantity, product.getPrice()));
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        }
        order.setTotalAmount(total);
        return toResponse(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id) {
        return orderRepository.findDetailedById(id).map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Order " + id + " was not found"));
    }

    @Transactional
    public OrderResponse cancel(Long id) {
        CustomerOrder order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order " + id + " was not found"));
        if (order.getStatus() != OrderStatus.PENDING)
            throw new BusinessException(HttpStatus.CONFLICT, "ORDER_NOT_CANCELLABLE", "Only pending orders can be cancelled");

        List<Long> productIds = order.getItems()
                .stream()
                .map(OrderItem::getProductId)
                .distinct()
                .sorted()
                .toList();

        Map<Long, Product> products = new HashMap<>();
        for (Long productId : productIds) {
            Product product = productRepository.findByIdForUpdate(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product " + productId + " was not found"));

            products.put(productId, product);
        }
        order.getItems().forEach(item -> products.get(item.getProductId()).increaseStock(item.getQuantity()));
        order.cancel();
        return toResponse(order);
    }

    private OrderResponse toResponse(CustomerOrder order) {
        List<OrderItemResponse> items = order.getItems()
                .stream()
                .map(i -> new OrderItemResponse(i.getProductId(), i.getProductName(), i.getQuantity(), i.getUnitPrice(), i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity()))))
                .toList();
        return new OrderResponse(order.getId(), order.getStatus(), order.getTotalAmount(), order.getCreatedAt(), items);
    }
}
