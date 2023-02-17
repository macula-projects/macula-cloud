package dev.macula.cloud.system.vo.perm;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 资源权限裂变VO对象
 * @author qiuyuhao
 * @date 2023.02.16
 */
@Schema(description = "资源权限裂变VO对象")
@Data
public class ResourcePermPageVO implements Serializable {
    @Schema(description = "权限id")
    private Long id;
    @Schema(description = "权限路径")
    private String urlPerm;
    @Schema(description = "权限编码/名称")
    private String code;
    @Schema(description = "来源菜单")
    private String fromMenu;
}
