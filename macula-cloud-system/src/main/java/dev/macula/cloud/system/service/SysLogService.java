package dev.macula.cloud.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import dev.macula.cloud.system.pojo.entity.SysLog;
import dev.macula.cloud.system.query.LogPageQuery;
import dev.macula.cloud.system.vo.log.AuditLogVO;

public interface SysLogService extends IService<SysLog> {
    /**
     * 获取审计日志列表
     *
     * @param queryParams 查询参数
     * @return 审计列表
     */
    Page<AuditLogVO> listUserPages(LogPageQuery queryParams);

}
