package com.example.Api_gateway.dto;

import lombok.Data;

@Data
public class SignupRequest {
    private String realmName;
    private String clientId;
    private boolean publicClient = true;

    private AdminUser adminUser;

}