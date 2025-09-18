package com.paxaris.ApiGateway.dto;

import lombok.Data;

@Data
public class RoleCreationRequest {
    private String name;
    private String oldRoleName;
    private String description;
    private String url;
    private String uri;
}
