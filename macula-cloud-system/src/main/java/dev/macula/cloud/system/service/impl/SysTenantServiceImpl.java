package dev.macula.cloud.system.service.impl;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import dev.macula.cloud.system.converter.TenantConverter;
import dev.macula.cloud.system.form.TenantForm;
import dev.macula.cloud.system.mapper.SysTenantMapper;
import dev.macula.cloud.system.pojo.entity.SysTenant;
import dev.macula.cloud.system.query.TenantPageQuery;
import dev.macula.cloud.system.service.SysTenantService;
import dev.macula.cloud.system.vo.tenant.TenantPageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class SysTenantServiceImpl extends ServiceImpl<SysTenantMapper, SysTenant> implements SysTenantService {

    private final TenantConverter tenantConverter;


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
        long count = this.count(new LambdaQueryWrapper<SysTenant>().eq(SysTenant::getCode, tenantForm.getCode())
                .or().eq(SysTenant::getName,tenantForm.getName())
        );
        Assert.isTrue(count == 0, "租户已存在");

        SysTenant sysTenant = tenantConverter.form2Entity(tenantForm);
        return this.save(sysTenant);
    }

    @Override
    public boolean updateTenant(Long id, TenantForm tenantForm) {
        String tenantName = tenantForm.getName();
        long count = this.count(new LambdaQueryWrapper<SysTenant>()
                .eq(SysTenant::getName, tenantName)
                .ne(SysTenant::getId,id)
                .or().eq(SysTenant::getCode,tenantForm.getCode())
        );
        Assert.isTrue(count == 0, "租户已存在");

        SysTenant sysTenant = tenantConverter.form2Entity(tenantForm);
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


}
