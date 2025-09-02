package com.example.Centralized_product.service;

import com.example.Centralized_product.entities.Product;
import com.example.Centralized_product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RedirectService {

    private final ProductRepository productRepository;

    public String getRedirectUrl(String productName) {
        return productRepository.findByProductName(productName)
                .map(Product::getProductUrl)
                .orElseThrow(() -> new RuntimeException("Product not found: " + productName));
    }
}
