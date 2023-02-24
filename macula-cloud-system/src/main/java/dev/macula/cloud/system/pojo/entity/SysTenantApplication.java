package dev.macula.cloud.system.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SysTenantApplication {
    private Long applicationId;
    private Long tenantId;
}
