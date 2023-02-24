package dev.macula.cloud.system.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SysTenantMenu {
    private Long menuId;
    private Long tenantId;
}
