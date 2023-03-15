package dev.macula.cloud.system.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.web.bind.annotation.RequestMethod;

@Data
@Schema(description = "权限路径验证器")
public class PermissionValidtorForm {
    @Schema(description = "权限id")
    private Long id;
    @Schema(description = "编码")
    private String code;
    @Schema(description = "请求的url")
    private String url;
    @Schema(description = "请求方法")
    private RequestMethod method;
}
