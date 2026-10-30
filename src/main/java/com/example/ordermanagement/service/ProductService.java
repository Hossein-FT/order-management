package com.example.ordermanagement.service;

import com.example.ordermanagement.dto.CreateProductRequest;
import com.example.ordermanagement.dto.ProductResponse;
import com.example.ordermanagement.entity.Product;
import com.example.ordermanagement.exception.ResourceNotFoundException;
import com.example.ordermanagement.repository.ProductRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {
    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        Product product = productRepository.save(new Product(request.name().trim(), request.price(), request.stockQuantity()));
        return toResponse(product);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return toResponse(productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product " + id + " was not found")));
    }

    private ProductResponse toResponse(Product p) {
        return new ProductResponse(p.getId(), p.getName(), p.getPrice(), p.getStockQuantity(), p.isActive());
    }
}
