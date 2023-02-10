package dev.macula.cloud.system.service.impl;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import dev.macula.cloud.system.converter.ApplicationConverter;
import dev.macula.cloud.system.form.ApplicationForm;
import dev.macula.cloud.system.mapper.SysApplicationMapper;
import dev.macula.cloud.system.pojo.bo.ApplicationBO;
import dev.macula.cloud.system.pojo.entity.SysApplication;
import dev.macula.cloud.system.query.ApplicationPageQuery;
import dev.macula.cloud.system.service.SysApplicationService;
import dev.macula.cloud.system.vo.app.ApplicationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysApplicationServiceImpl extends ServiceImpl<SysApplicationMapper, SysApplication> implements SysApplicationService {

    private final ApplicationConverter applicationConverter;

    @Override
    public Page<ApplicationVO> listApplicationPages(ApplicationPageQuery queryParams) {
        Page<SysApplication> page = new Page<>(queryParams.getPageNum(), queryParams.getPageSize());
        Page<ApplicationBO> bo = this.baseMapper.listApplicationPages(page, queryParams);
        Page<ApplicationVO> list = applicationConverter.bo2Vo(bo);
        return list;
    }

    @Override
    public boolean saveApplication(ApplicationForm appForm) {
        SysApplication sysApplication = applicationConverter.form2Entity(appForm);
        boolean result = this.save(sysApplication);
        return result;
    }

    @Override
    public boolean updateApplication(Long appId, ApplicationForm appForm) {
        SysApplication sysApplication = applicationConverter.form2Entity(appForm);
        sysApplication.setId(appId);
        boolean result = this.updateById(sysApplication);
        return result;
    }

    @Override
    public boolean deleteApplications(String idsStr) {
        Assert.isTrue(StrUtil.isNotBlank(idsStr), "删除的应用数据为空");
        // 逻辑删除
        List<Long> ids = Arrays.asList(idsStr.split(",")).stream()
                .map(idStr -> Long.parseLong(idStr)).collect(Collectors.toList());
        boolean result = this.removeByIds(ids);
        return result;
    }
}
