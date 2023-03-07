package dev.macula.cloud.system.query;

import dev.macula.boot.base.BasePageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "审计日志分页查询对象")
@Data
public class LogPageQuery extends BasePageQuery {

    @Schema(description = "关键字(接口操作名称/请求路径/请求用户)")
    private String keywords;

    @Schema(description = "开始时间(格式：yyyy-MM-dd)")
    private String beginDate;

    @Schema(description = "截止时间(格式：yyyy-MM-dd)")
    private String endDate;
}
