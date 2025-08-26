package com.example.Centralized_product.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    // Prevents recursion: ignore the "subscriptions" list inside Client
    @JsonIgnoreProperties("subscriptions")
    private Client client;

    @ManyToOne
    private Product product;
}
