package dev.macula.cloud.system.vo.log;


import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "审计日志分页视图对象")
@Data
public class AuditLogVO {
    @Schema(description = "操作IP")
    private String opIp;     
    
    @Schema(description = "请求路径")
    private String opUrl;     

    @Schema(description = "操作人")
    private String opName;     

    @Schema(description = "操作标题")
    private String opTitle;     

    @Schema(description = "请求方法")
    private String opMethod;     

    @Schema(description = "请求方式")
    private String opRequestMethod;     

    @Schema(description = "请求参数")
    private String opParam;     

    @Schema(description = "操作状态（0-成功; 1-失败）")
    private Integer opStatus;     

    @Schema(description = "错误信息")
    private String errorMsg;     

    @Schema(description = "响应结果信息")
    private String jsonResult;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDateTime createTime;

    @Schema(description = "创建人")
    private String createBy;
}
