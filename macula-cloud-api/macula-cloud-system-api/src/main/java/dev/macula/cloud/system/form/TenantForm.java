package dev.macula.cloud.system.form;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 *  租户表单对象
 */
@Schema(description = "租户表单对象")
@Data
public class TenantForm {

    private Long tenantId;

    private String name;

    private String code;

    private String description;

    private String supervisor;
}
