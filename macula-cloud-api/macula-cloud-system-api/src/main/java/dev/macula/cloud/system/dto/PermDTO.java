package dev.macula.cloud.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 权限对象的BO
 * @author qiuyuhao
 * @date 2023.01.17
 */
@Schema(description = "权限对象的BO")
@Data
public class PermDTO {
  @Schema(description = "权限对象的名称")
  private String code;
  @Schema(description = "权限对象的访问路径")
  private String url;
}
