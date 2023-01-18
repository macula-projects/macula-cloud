package dev.macula.cloud.system.pojo.bo;

import dev.macula.cloud.system.dto.MenuMetaDTO;
import dev.macula.cloud.system.dto.PermDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 菜单管理，菜单列表BO对象
 * @author qiuyuhao
 * @date 2023.01.17
 */
@Schema(description = "菜单管理，菜单列表BO对象")
@Data
public class MenuListBO {
  @Schema(description = "当前菜单id")
  private long id;
  @Schema(description = "当前菜单的父菜单id")
  private long parentId;
  @Schema(description = "当前菜单的名字")
  private String name;
  @Schema(description = "当前菜单的路径")
  private String path;
  @Schema(description = "当前菜单的元数据对象")
  private MenuMetaDTO meta;
  @Schema(description = "当前菜单关联的访问权限信息")
  private List<PermDTO> apiList;
  @Schema(description = "当前菜单的显示组件")
  private String component;
  @Schema(description = "当前菜单的子菜单信息")
  private List<MenuListBO> children;
}
