package com.example.Centralized_product.controller;

import com.example.Centralized_product.entities.Client;
import com.example.Centralized_product.entities.Product;
import com.example.Centralized_product.entities.Subscription;
import com.example.Centralized_product.entities.User;
import com.example.Centralized_product.repository.ClientRepository;
import com.example.Centralized_product.repository.ProductRepository;
import com.example.Centralized_product.repository.SubscriptionRepository;
import com.example.Centralized_product.repository.UserRepository;
import com.example.Centralized_product.dto.SignupRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/onboard")
@RequiredArgsConstructor
public class OnboardingController {

    private final ClientRepository clientRepository;
    private final ProductRepository productRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<String> onboardClient(@RequestBody SignupRequest request) {

        // 1) Save Client → realmName as clientName
        Client client = clientRepository.findByClientName(request.getRealmName())
                .orElseGet(() -> {
                    Client c = new Client();
                    c.setClientName(request.getRealmName());
                    return clientRepository.save(c);
                });

        // 2) Save Product → clientId as productName
        Product product = productRepository.findByProductName(request.getClientId())
                .orElseGet(() -> {
                    Product p = new Product();
                    p.setProductName(request.getClientId());
                    return productRepository.save(p);
                });

        // 3) Save Subscription (link client + product)
        if (!subscriptionRepository.existsByClientClientNameAndProductProductName(
                client.getClientName(), product.getProductName())) {
            Subscription subscription = new Subscription();
            subscription.setClient(client);
            subscription.setProduct(product);
            subscriptionRepository.save(subscription);
        }

        // 4) Save Admin User
        if (request.getAdminUser() != null) {
            User user = new User();
            user.setUsername(request.getAdminUser().getUsername());
            user.setEmail(request.getAdminUser().getEmail());
            user.setFirstName(request.getAdminUser().getFirstName());
            user.setLastName(request.getAdminUser().getLastName());
            user.setPassword(request.getAdminUser().getPassword()); // ⚠️ ideally hash this before saving
            userRepository.save(user);
        }

        return ResponseEntity.ok("✅ Onboarding + user data saved in centralized DB!");
    }
}
