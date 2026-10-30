package com.example.ordermanagement.dto;

import com.example.ordermanagement.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(Long id, OrderStatus status, BigDecimal totalAmount, Instant createdAt,
                            List<OrderItemResponse> items) {
}
