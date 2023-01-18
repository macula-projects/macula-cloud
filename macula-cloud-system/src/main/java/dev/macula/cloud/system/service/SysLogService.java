package dev.macula.cloud.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import dev.macula.cloud.system.pojo.entity.SysLog;
import dev.macula.cloud.system.query.LogPageQuery;
import dev.macula.cloud.system.vo.log.AuditLogVO;

public interface SysLogService extends IService<SysLog> {
    Page<AuditLogVO> listUserPages(LogPageQuery queryParams);

}
