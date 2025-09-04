package com.example.Api_gateway.dto;
import lombok.Data;

@Data
public class AdminUser {
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String password;
}