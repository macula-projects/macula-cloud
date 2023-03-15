package dev.macula.cloud.system.vo.tenant;

import dev.macula.cloud.system.vo.user.UserVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Schema(description = "租户视图对象")
@Data
public class TenantPageVO implements Serializable {

    @Schema(description = "租户ID")
    private Long id;

    @Schema(description = "租户名称")
    private String name;

    @Schema(description = "租户编码")
    private String code;

    @Schema(description = "负责人")
    private List<UserVO> supervisor;

    @Schema(description = "描述")
    private String description;
}
