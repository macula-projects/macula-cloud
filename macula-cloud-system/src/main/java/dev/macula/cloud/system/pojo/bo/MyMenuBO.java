package dev.macula.cloud.system.pojo.bo;

import dev.macula.cloud.system.dto.MenuMetaDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 我的菜单结构接口
 * @author qiuyuhao
 * @date 2023.01.17
 */
@Schema(description = "我的菜单结构(目录、子菜单聚合)")
@Data
public class MyMenuBO {
  @Schema(description = "目录id")
  private long id;
  @Schema(description = "目录名字")
  private String name;
  @Schema(description = "目录路径")
  private String path;
  @Schema(description = "目录元数据对象")
  private MenuMetaDTO meta;
  @Schema(description = "目录子菜单或子目录")
  private List<MyMenuBO> children;
  @Schema(description = "子菜单前端组件名")
  private String component;
}
