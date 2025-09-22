package com.paxaris.ApiGateway.dto;

import lombok.Data;

@Data
public class SignupRequest {
    private String realmName;
    private String clientId;
    private boolean publicClient = true;

    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String password;
}