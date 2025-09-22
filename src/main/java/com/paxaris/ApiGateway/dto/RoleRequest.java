package com.paxaris.ApiGateway.dto;

import lombok.Data;
import java.util.List;

@Data
public class RoleRequest {
    private Long id;
    private String realmName;
    private String productName;
    private String roleName;
    private List<UrlEntry> urls;
}
