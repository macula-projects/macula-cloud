package dev.macula.cloud.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.validation.constraints.NotBlank;

/**
 * 权限对象的BO
 * @author qiuyuhao
 * @date 2023.01.17
 */
@Schema(description = "权限对象的BO")
@Data
public class PermDTO {
  @Schema(description = "权限对象的id")
  private Long id;
  @Schema(description = "权限对象的名称")
  @NotBlank(message = "权限名称不能为空")
  private String code;
  @Schema(description = "权限对象的访问路径")
  @NotBlank(message = "权限路径不能为空")
  private String url;
  @Schema(description = "权限路径请求方式")
  @NotBlank(message = "权限路径请求方式不能为空")
  private RequestMethod method;
}
