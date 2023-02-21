package dev.macula.cloud.system.pojo.entity;

import dev.macula.boot.starter.mp.entity.BaseEntity;
import lombok.Data;

@Data
public class SysTenant extends BaseEntity {

    private Long tenantId;
    private String name;

    private String code;

    private String description;

    private String supervisor;

}
