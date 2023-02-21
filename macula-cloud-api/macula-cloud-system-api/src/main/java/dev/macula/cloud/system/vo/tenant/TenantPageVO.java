package dev.macula.cloud.system.vo.tenant;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Schema(description = "租户视图对象")
@Data
public class TenantPageVO implements Serializable {

    @Schema(description = "租户ID")
    private Long id;

    private Long tenantId;

    @Schema(description = "租户名称")
    private String name;

    @Schema(description = "租户编码")
    private String code;

    @Schema(description = "负责人")
    private String supervisor;
}
