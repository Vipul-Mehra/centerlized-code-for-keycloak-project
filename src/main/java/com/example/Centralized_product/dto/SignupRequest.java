package com.example.Centralized_product.dto;

import lombok.Data;

@Data
public class SignupRequest {
    private String realmName;
    private String clientId;
    private boolean publicClient = true;

    private AdminUser adminUser;

}