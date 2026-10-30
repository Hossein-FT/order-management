package com.example.ordermanagement.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CreateProductRequest(@NotBlank
                                   @Size(max = 150) String name, @NotNull
                                   @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
                                   @NotNull
                                   @Min(0) Integer stockQuantity) {
}
