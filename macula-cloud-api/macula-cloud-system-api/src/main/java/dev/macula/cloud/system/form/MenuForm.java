package dev.macula.cloud.system.form;

import dev.macula.boot.enums.MenuTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;


@Schema(description = "菜单表单对象")
@Data
public class MenuForm {

    private Long id;

    private Long parentId;

    private String name;

    private String icon;

    /**
     * 路由path
     */
    private String path;

    /**
     * 组件路径
     */
    private String component;

    private Integer sort;

    private Integer visible;

    private String redirect;

    private Integer fullPage;

    /**
     * 菜单类型(1:菜单；2：目录；3：外链；4：按钮)
     */
    private MenuTypeEnum type;

    /**
     * 按钮权限标识
     */
    private String perm;

    private List<PermissionValidtorForm> apiList;
}
