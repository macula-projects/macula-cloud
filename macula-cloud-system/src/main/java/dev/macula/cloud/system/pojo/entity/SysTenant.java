package dev.macula.cloud.system.pojo.entity;

import dev.macula.boot.starter.mp.entity.BaseEntity;
import lombok.Data;

@Data
public class SysTenant extends BaseEntity {
    private String tenantName;

    private String tenantType;

}
