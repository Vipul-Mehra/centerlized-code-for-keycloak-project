package com.example.Centralized_product.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String roleName;   // 3rd column
    private String username;   // 2nd column (copied from User.username)

    @ManyToOne
    @JoinColumn(name = "user_id")
    @JsonIgnoreProperties("roles")
    private User user;
}
