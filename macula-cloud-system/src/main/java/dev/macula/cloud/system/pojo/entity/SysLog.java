package dev.macula.cloud.system.pojo.entity;

import dev.macula.boot.starter.mp.entity.BaseEntity;
import lombok.Data;

@Data
public class SysLog extends BaseEntity {

    private String opIp;    // 操作IP

    private String opUrl;    // 请求路径

    private String opName;    // 操作人

    private String opTitle;    // 操作标题

    private String opMethod;    // 请求方法

    private String opRequestMethod;    // 请求方式

    private String opParam;    // 请求参数

    private Integer opStatus;    // 操作状态（0-成功 1-失败）

    private String errorMsg;    // 错误信息

    private String jsonResult;    // 响应结果信息

}
