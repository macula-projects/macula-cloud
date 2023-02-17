package dev.macula.cloud.system.pojo.entity;

import dev.macula.boot.starter.mp.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class SysApplication extends BaseEntity {

    private String applicationName;

    private String homepage;

    private String manager;

    private String maintainer;

    private String mobile;

    private String code;
}
