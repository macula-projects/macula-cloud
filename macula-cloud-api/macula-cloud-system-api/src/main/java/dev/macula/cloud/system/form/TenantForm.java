package dev.macula.cloud.system.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * 租户表单对象
 */
@Schema(description = "租户表单对象")
@Data
public class TenantForm {
    @Schema(description = "租户名字")
    private String name;
    @Schema(description = "租户编码")
    private String code;
    @Schema(description = "租户描述")
    private String description;
    @Valid
    @NotNull(message = "负责人不能为空")
    @Size(min = 1, message = "至少需要一个负责人")
    @Schema(description = "负责人id")
    private List<Long> supervisor;
}
