package dev.macula.cloud.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 菜单管理，添加菜单的dto对象
 *
 * @author qiuyuhao
 * @date 2023.01.17
 */
@Schema(description = "菜单管理，添加菜单的dto对象")
@Data
public class MenuDTO implements Serializable {
    @Schema(description = "菜单id")
    private Long id;
    @Schema(description = "菜单父id")
    private long parentId;
    @Schema(description = "按钮权限标识")
    private String perm;
    @Schema(description = "菜单路径")
    private String path;
    @Schema(description = "菜单元数据")
    private MenuMetaDTO meta;
    @Schema(description = "菜单高亮")
    private String acitve;
    @Schema(description = "权限列表")
    private List<PermDTO> apiList;
    @Schema(description = "菜单组件")
    private String component;
    @Schema(description = "重定向地址")
    private String redirect;
    @Schema(description = "排序")
    private Integer sort;
    @Schema(description = "菜单所属租户")
    private Long tenantId;
}
