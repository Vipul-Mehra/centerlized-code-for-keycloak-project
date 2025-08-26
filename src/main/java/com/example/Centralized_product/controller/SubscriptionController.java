package com.example.Centralized_product.controller;

import com.example.Centralized_product.entities.Client;
import com.example.Centralized_product.entities.Product;
import com.example.Centralized_product.entities.Subscription;
import com.example.Centralized_product.repository.ClientRepository;
import com.example.Centralized_product.repository.ProductRepository;
import com.example.Centralized_product.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionRepository subscriptionRepository;
    private final ClientRepository clientRepository;
    private final ProductRepository productRepository;

    @GetMapping("/data")
    public List<Subscription> getAllSubscriptions() {
        return subscriptionRepository.findAll();
    }

    @PostMapping
    public Subscription subscribeClientToProduct(@RequestParam Long clientId, @RequestParam Long productId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found"));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        Subscription subscription = Subscription.builder()
                .client(client)
                .product(product)
                .build();

        return subscriptionRepository.save(subscription);
    }
}
