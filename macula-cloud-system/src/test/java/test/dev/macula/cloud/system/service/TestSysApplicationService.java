package test.dev.macula.cloud.system.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.macula.boot.constants.CacheConstants;
import dev.macula.boot.starter.redis.config.RedissonAutoConfiguration;
import dev.macula.cloud.system.mapper.SysApplicationMapper;
import dev.macula.cloud.system.pojo.bo.ApplicationBO;
import dev.macula.cloud.system.pojo.entity.SysApplication;
import dev.macula.cloud.system.pojo.entity.SysTenantInfo;
import dev.macula.cloud.system.query.ApplicationPageQuery;
import dev.macula.cloud.system.service.SysApplicationService;
import dev.macula.cloud.system.service.SysTenantService;
import dev.macula.cloud.system.vo.app.ApplicationVO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {TestSysApplicationService.SysApplicationServiceConfig.class,
        RedissonAutoConfiguration.class})
public class TestSysApplicationService {

    @ComponentScan(value = {"dev.macula.cloud.system.service", "dev.macula.cloud.system.converter"},
            includeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                    pattern = {".*(ApplicationConverter|SysApplicationService).*$"}),
            useDefaultFilters = false)
    @TestConfiguration
    public static class SysApplicationServiceConfig{ }

    @MockBean
    private SysTenantService sysTenantService;

    @MockBean
    private SysApplicationMapper sysApplicationMapper;

    @Autowired
    private SysApplicationService applicationService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    public void testListApplicationPages(){
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
        when(sysApplicationMapper.listApplicationPages(any(Page.class), any(ApplicationPageQuery.class))).thenReturn(res);

        ApplicationPageQuery query = new ApplicationPageQuery();
        query.setPageNum(1);
        query.setPageSize(1);
        query.setKeywords("bff");
        Page<ApplicationVO> dbRes = applicationService.listApplicationPages(query);
        Assertions.assertTrue(dbRes.getSize() == 1, "分页数据不正确");
        Assertions.assertTrue(dbRes.getTotal() == 1, "总数不正确");
        Assertions.assertTrue(res.getRecords().get(0).getCode().equals("bff-abd"), "数据最值正确");
    }

    @Test
    public void testRefreshApplicationCache(){
        List<SysTenantInfo> tenantInfos = new ArrayList<>();
        SysTenantInfo tenantInfo = new SysTenantInfo();
        tenantInfo.setId(1L);
        tenantInfos.add(tenantInfo);
        when(sysTenantService.list()).thenReturn(tenantInfos);
        List<SysApplication> apps = new ArrayList();
        SysApplication sys = new SysApplication();
        sys.setApplicationName("bff-abd");
        sys.setSk("test");
        sys.setCode("bff-abd");
        sys.setManager("admin");
        sys.setUseAttrs(false);
        sys.setMaintainer("admin");
        apps.add(sys);
        when(sysApplicationMapper.selectList(any(Wrapper.class))).thenReturn(apps);

        applicationService.refreshApplicationCache();

        String key =  CacheConstants.SECURITY_SYSTEM_APPS + "bff-abd";
        Assertions.assertTrue(redisTemplate.hasKey(key), "写缓存失败");
        Assertions.assertTrue(
                "test".equals(redisTemplate.opsForHash().get(key, CacheConstants.SECURITY_SYSTEM_APPS_SECRIT_KEY)),
                "缓存数据存取错误");
    }

}
