package dev.macula.cloud.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import dev.macula.cloud.system.mapper.SysApplicationTenantMapper;
import dev.macula.cloud.system.pojo.entity.SysApplicationTenant;
import dev.macula.cloud.system.service.SysApplicationTenantService;
import org.springframework.stereotype.Service;

@Service
public class SysApplicationTenantServiceImpl extends ServiceImpl<SysApplicationTenantMapper, SysApplicationTenant> implements SysApplicationTenantService {

}
