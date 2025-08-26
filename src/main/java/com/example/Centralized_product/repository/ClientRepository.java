package com.example.Centralized_product.repository;

import com.example.Centralized_product.entities.Client;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, Long> {
}
