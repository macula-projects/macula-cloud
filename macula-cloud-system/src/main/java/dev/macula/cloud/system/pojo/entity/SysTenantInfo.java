package dev.macula.cloud.system.pojo.entity;

import dev.macula.boot.starter.mp.entity.BaseEntity;
import lombok.Data;

@Data
public class SysTenantInfo extends BaseEntity {

    private String name;

    private String code;

    private String description;

    private String supervisor;

}
