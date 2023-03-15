package dev.macula.cloud.system.query;

import dev.macula.boot.base.BasePageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "租户分页查询对象")
public class TenantPageQuery extends BasePageQuery {

    @Schema(description = "关键字(租户名/租户编码/负责人)")
    private String keywords;
}
