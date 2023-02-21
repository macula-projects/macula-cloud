package dev.macula.cloud.system.pojo.bo;


import cn.hutool.db.DaoTemplate;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class TenantBo {

    private Long id;

    /**
     * 租户名称
     */
    private String name;

    /**
     * 租户编码
     */
    private String code;

    /**
     * 描述
     */
    private String description;

    /**
     * 负责人
     */
    private String supervisor;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date createTime;
}
