package com.example.Centralized_product.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRoles {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "role_id")
    @JsonIgnoreProperties("productRoles")
    private Role role;

    @ManyToOne
    @JoinColumn(name = "product_id")
    @JsonIgnoreProperties("productRoles")
    private Product product;
}
