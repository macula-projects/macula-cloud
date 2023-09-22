package dev.macula.cloud.system.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import dev.macula.boot.result.Option;
import dev.macula.boot.starter.security.utils.SecurityUtils;
import dev.macula.cloud.system.converter.TenantConverter;
import dev.macula.cloud.system.form.TenantForm;
import dev.macula.cloud.system.mapper.SysTenantInfoMapper;
import dev.macula.cloud.system.pojo.bo.TenantBO;
import dev.macula.cloud.system.pojo.entity.SysTenantInfo;
import dev.macula.cloud.system.pojo.entity.SysTenantUser;
import dev.macula.cloud.system.query.TenantPageQuery;
import dev.macula.cloud.system.service.SysTenantService;
import dev.macula.cloud.system.service.SysTenantUserService;
import dev.macula.cloud.system.vo.tenant.TenantPageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysTenantServiceImpl extends ServiceImpl<SysTenantInfoMapper, SysTenantInfo> implements SysTenantService {

    private final TenantConverter tenantConverter;
    private final SysTenantUserService tenantUserService;

    @Override
    public IPage<TenantPageVO> listTenantpages(TenantPageQuery queryParams) {
        // 查询数据
        Page<TenantBO> tenantpages =
            this.baseMapper.listTenantpages(new Page<>(queryParams.getPageNum(), queryParams.getPageSize()),
                queryParams);
        Page<TenantPageVO> result = tenantConverter.bo2Page(tenantpages);
        return result;
    }

    @Override
    @Transactional
    public boolean saveTenant(TenantForm tenantForm) {
        long count = this.count(
            new LambdaQueryWrapper<SysTenantInfo>().eq(SysTenantInfo::getCode, tenantForm.getCode()).or()
                .eq(SysTenantInfo::getName, tenantForm.getName()));
        Assert.isTrue(count == 0, "租户已存在");

        SysTenantInfo sysTenant = tenantConverter.form2Entity(tenantForm);
        boolean saveFlag = save(sysTenant);
        Assert.isTrue(saveFlag, "保存租户失败");
        List<SysTenantUser> tenantUserList =
            tenantForm.getSupervisor().stream().map(userId -> new SysTenantUser(userId, sysTenant.getId()))
                .collect(Collectors.toList());
        return tenantUserService.saveBatch(tenantUserList);
    }

    @Override
    @Transactional
    public boolean updateTenant(Long id, TenantForm tenantForm) {
        String tenantName = tenantForm.getName();
        long count = this.count(new LambdaQueryWrapper<SysTenantInfo>().ne(SysTenantInfo::getId, id).and(wrapper -> {
            wrapper.eq(SysTenantInfo::getName, tenantName).or().eq(SysTenantInfo::getCode, tenantForm.getCode());
        }));
        Assert.isTrue(count == 0, "租户已存在");

        SysTenantInfo sysTenant = tenantConverter.form2Entity(tenantForm);
        sysTenant.setId(id);
        boolean saveFlag = updateById(sysTenant);
        Assert.isTrue(saveFlag, "更新租户失败");
        tenantUserService.remove(
            new LambdaQueryWrapper<SysTenantUser>().eq(SysTenantUser::getTenantId, sysTenant.getId()));
        List<SysTenantUser> tenantUserList =
            tenantForm.getSupervisor().stream().map(userId -> new SysTenantUser(userId, sysTenant.getId()))
                .collect(Collectors.toList());
        return tenantUserService.saveBatch(tenantUserList);
    }

    @Override
    @Transactional
    public boolean deleteTenants(String idsStr) {
        Assert.isTrue(StrUtil.isNotBlank(idsStr), "删除的租户数据为空");
        // 逻辑删除
        List<Long> ids =
            Arrays.asList(idsStr.split(",")).stream().map(idStr -> Long.parseLong(idStr)).collect(Collectors.toList());
        boolean result = this.removeByIds(ids);
        return result;
    }

    @Override
    public List<Option<Long>> listTenantOptions(boolean filterMe) {
        boolean filterMeFlag = filterMe && !SecurityUtils.isRoot();
        Set<Long> ids = tenantUserService.getMeTenantIds();
        if (CollectionUtil.isEmpty(ids)) {
            // 默认租户
            ids.add(1L);
        }
        List<SysTenantInfo> sysTenantInfoList =
            list(new LambdaQueryWrapper<SysTenantInfo>().in(filterMeFlag, SysTenantInfo::getId, ids));
        return sysTenantInfoList.stream().map(tenantInfo -> new Option<>(tenantInfo.getId(), tenantInfo.getName()))
            .collect(Collectors.toList());
    }

}
