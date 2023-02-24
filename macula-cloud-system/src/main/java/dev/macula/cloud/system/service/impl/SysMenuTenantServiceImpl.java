package dev.macula.cloud.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import dev.macula.cloud.system.mapper.SysMenuTenantMapper;

import dev.macula.cloud.system.pojo.entity.SysMenuTenant;
import dev.macula.cloud.system.service.SysMenuTenantService;
import org.springframework.stereotype.Service;

@Service
public class SysMenuTenantServiceImpl extends ServiceImpl<SysMenuTenantMapper, SysMenuTenant> implements SysMenuTenantService {

}
