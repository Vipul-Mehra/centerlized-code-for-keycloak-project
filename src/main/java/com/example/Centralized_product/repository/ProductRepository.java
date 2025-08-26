package com.example.Centralized_product.repository;

import com.example.Centralized_product.entities.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
