package dev.macula.cloud.system.service.impl;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import dev.macula.boot.result.Option;
import dev.macula.cloud.system.converter.TenantConverter;
import dev.macula.cloud.system.form.TenantForm;
import dev.macula.cloud.system.mapper.SysTenantInfoMapper;
import dev.macula.cloud.system.pojo.entity.SysTenantInfo;
import dev.macula.cloud.system.query.TenantPageQuery;
import dev.macula.cloud.system.service.SysTenantService;
import dev.macula.cloud.system.service.SysUserTenantService;
import dev.macula.cloud.system.vo.tenant.TenantPageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class SysTenantServiceImpl extends ServiceImpl<SysTenantInfoMapper, SysTenantInfo> implements SysTenantService {

    private final TenantConverter tenantConverter;

    private final SysUserTenantService userTenantService;

    @Value("${spring.application.name}")
    private String systemCode;

    @Override
    public Page<TenantPageVO> listTenantpages(TenantPageQuery queryParams) {
        // 查询数据
        Page<TenantPageVO> tenantpages = this.baseMapper.listTenantpages(
                new Page<>(queryParams.getPageNum(),
                        queryParams.getPageSize()),
                queryParams
        );
        return tenantpages;
    }

    @Override
    public boolean saveTenant(TenantForm tenantForm) {
        long count = this.count(new LambdaQueryWrapper<SysTenantInfo>().eq(SysTenantInfo::getCode, tenantForm.getCode())
                .or().eq(SysTenantInfo::getName,tenantForm.getName())
        );
        Assert.isTrue(count == 0, "租户已存在");

        SysTenantInfo sysTenant = tenantConverter.form2Entity(tenantForm);
        return this.save(sysTenant);
    }

    @Override
    public boolean updateTenant(Long id, TenantForm tenantForm) {
        String tenantName = tenantForm.getName();
        long count = this.count(new LambdaQueryWrapper<SysTenantInfo>()
                .eq(SysTenantInfo::getName, tenantName)
                .ne(SysTenantInfo::getId,id)
                .or().eq(SysTenantInfo::getCode,tenantForm.getCode())
        );
        Assert.isTrue(count == 0, "租户已存在");

        SysTenantInfo sysTenant = tenantConverter.form2Entity(tenantForm);
        sysTenant.setId(id);
        return this.updateById(sysTenant);
    }

    @Override
    public boolean deleteTenants(String idsStr) {
        Assert.isTrue(StrUtil.isNotBlank(idsStr), "删除的租户数据为空");
        // 逻辑删除
        List<Long> ids = Arrays.asList(idsStr.split(",")).stream()
                .map(idStr -> Long.parseLong(idStr)).collect(Collectors.toList());
        boolean result = this.removeByIds(ids);
        return result;
    }

    @Override
    public List<Option> listTenantOptions(Integer filterMe) {
        List<SysTenantInfo> sysTenantInfoList = list(new LambdaQueryWrapper<SysTenantInfo>()
                .in(filterMe.equals(1), SysTenantInfo::getId, userTenantService.getMeTenantIds()));
        List<Option> result = sysTenantInfoList.stream().map(tenantInfo -> {
            Option option = new Option(tenantInfo.getId(), tenantInfo.getName());
            return option;
        }).collect(Collectors.toList());
        return result;
    }

}
