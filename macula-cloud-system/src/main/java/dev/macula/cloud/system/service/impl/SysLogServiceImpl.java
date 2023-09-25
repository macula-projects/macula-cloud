package dev.macula.cloud.system.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import dev.macula.cloud.system.converter.AuditLogConverter;
import dev.macula.cloud.system.mapper.SysLogMapper;
import dev.macula.cloud.system.pojo.bo.AuditLogBO;
import dev.macula.cloud.system.pojo.entity.SysLog;
import dev.macula.cloud.system.query.LogPageQuery;
import dev.macula.cloud.system.service.SysLogService;
import dev.macula.cloud.system.vo.log.AuditLogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SysLogServiceImpl extends ServiceImpl<SysLogMapper, SysLog> implements SysLogService {

    private final AuditLogConverter auditLogConverter;

    @Override
    public Page<AuditLogVO> listUserPages(LogPageQuery queryParams) {
        Page<SysLog> page = new Page<>(queryParams.getPageNum(), queryParams.getPageSize());
        Page<AuditLogBO> bos = this.baseMapper.listLogPages(page, queryParams);
        return auditLogConverter.bo2Vo(bos);
    }
}
