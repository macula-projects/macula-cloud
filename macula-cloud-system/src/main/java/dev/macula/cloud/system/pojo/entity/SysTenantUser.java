package dev.macula.cloud.system.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SysTenantUser {
    private Long userId;
    private Long tenantId;
}
