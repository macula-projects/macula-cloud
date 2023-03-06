package dev.macula.cloud.system.pojo.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuditLogBO {
    /**     操作IP     */     
    private String opIp;

    /**     请求路径     */     
    private String opUrl;

    /**     操作人     */     
    private String opName;

    /**     操作标题     */     
    private String opTitle;

    /**     请求方法     */     
    private String opMethod;

    /**     请求方式     */     
    private String opRequestMethod;

    /**     请求参数     */     
    private String opParam;

    /**     操作状态（0-成功; 1-失败）     */     
    private Integer opStatus;

    /**     错误信息     */     
    private String errorMsg;

    /**     响应结果信息     */     
    private String jsonResult;

    /**     创建时间     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDateTime createTime;

    /**     创建人     */     
    private String createBy;
}
