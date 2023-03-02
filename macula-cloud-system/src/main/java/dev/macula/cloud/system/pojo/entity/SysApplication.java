package dev.macula.cloud.system.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import dev.macula.boot.starter.mp.entity.BaseEntity;
import lombok.Data;

@Data
@TableName("sys_application_tenant")
public class SysApplication extends BaseEntity {

    private String applicationName;

    private String homepage;

    private String ak;

    private String sk;

    private String manager;

    private String maintainer;

    private String mobile;

    private String code;

    private String accessPath;
}
