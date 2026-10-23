package test.dev.macula.cloud.system.controller;

import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.macula.boot.constants.CacheConstants;
import dev.macula.boot.starter.redis.config.RedissonAutoConfiguration;
import dev.macula.cloud.system.controller.SysApplicationController;
import dev.macula.cloud.system.form.ApplicationForm;
import dev.macula.cloud.system.mapper.SysApplicationMapper;
import dev.macula.cloud.system.pojo.bo.ApplicationBO;
import dev.macula.cloud.system.pojo.entity.SysApplication;
import dev.macula.cloud.system.pojo.entity.SysTenantInfo;
import dev.macula.cloud.system.query.ApplicationPageQuery;
import dev.macula.cloud.system.service.SysTenantService;
import dev.macula.cloud.system.vo.app.ApplicationVO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import test.dev.macula.cloud.system.service.TestSysApplicationService;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {TestSysApplicationController.SysApplicationControllerConfig.class,
        TestSysApplicationService.SysApplicationServiceConfig.class,
        DruidDataSourceAutoConfigure.class, com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class,
        dev.macula.boot.starter.mp.config.MyBatisPlusAutoConfiguration.class, RedissonAutoConfiguration.class})
@MapperScan(basePackages = "dev.macula.cloud.system.mapper")
@TestPropertySource("classpath:application-db.properties")
public class TestSysApplicationController {
    @ComponentScan(basePackages = {"dev.macula.cloud.system.controller"}, includeFilters = {
            @ComponentScan.Filter(type= FilterType.REGEX, pattern = ".*SysApplicationController")},
            useDefaultFilters = false)
    @TestConfiguration
    static class SysApplicationControllerConfig{}

    @MockBean
    private SysTenantService sysTenantService;

    @SpyBean
    private SysApplicationMapper sysApplicationMapper;

    @Autowired
    private SysApplicationController sysApplicationController;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    public void testListApplications(){
        Page<ApplicationBO> res = new Page<>();
        res.setSize(1);
        res.setTotal(1);
        List<ApplicationBO> records = new ArrayList<>();
        ApplicationBO bo = new ApplicationBO();
        bo.setApplicationName("bff-abd");
        bo.setSk("test");
        bo.setCode("bff-abd");
        bo.setManager("admin");
        bo.setUseAttrs(false);
        bo.setMaintainer("admin");
        records.add(bo);
        res.setRecords(records);
        doReturn(res).when(sysApplicationMapper).listApplicationPages(any(Page.class), any(ApplicationPageQuery.class));

        ApplicationPageQuery query = new ApplicationPageQuery();
        query.setPageNum(1);
        query.setPageSize(1);
        query.setKeywords("bff");
        IPage<ApplicationVO> dbRes = sysApplicationController.listApplications(query);
        Assertions.assertTrue(dbRes.getSize() == 1, "分页数据不正确");
        Assertions.assertTrue(dbRes.getTotal() == 1, "总数不正确");
        Assertions.assertTrue(res.getRecords().get(0).getCode().equals("bff-abd"), "数据最值正确");
    }

    @Test
    public void testUpdateApplication(){
        doReturn(1).when(sysApplicationMapper).updateById(any(SysApplication.class));
        List<SysTenantInfo> tenantInfos = new ArrayList<>();
        SysTenantInfo tenantInfo = new SysTenantInfo();
        tenantInfo.setId(1L);
        tenantInfos.add(tenantInfo);
        when(sysTenantService.list()).thenReturn(tenantInfos);
        List<SysApplication> apps = new ArrayList();
        SysApplication sys = new SysApplication();
        sys.setApplicationName("bff-abd");
        sys.setSk("test");
        sys.setCode("bff-abd1");
        sys.setManager("admin");
        sys.setUseAttrs(false);
        sys.setMaintainer("admin");
        apps.add(sys);
        doReturn(apps).when(sysApplicationMapper).selectList(any(Wrapper.class));

        ApplicationForm applicationForm = new ApplicationForm();
        applicationForm.setApplicationName("test1");
        applicationForm.setCode("test1");
        applicationForm.setSk("test1");
        applicationForm.setManager("admin");
        applicationForm.setUseAttrs(false);
        boolean flag = sysApplicationController.updateApplication(1L, applicationForm);
        Assertions.assertTrue(flag, "保存应用失败");
        String key =  CacheConstants.SECURITY_SYSTEM_APPS + "bff-abd1";
        Assertions.assertTrue(redisTemplate.hasKey(key), "更新数据刷新缓存失败");
    }
}
