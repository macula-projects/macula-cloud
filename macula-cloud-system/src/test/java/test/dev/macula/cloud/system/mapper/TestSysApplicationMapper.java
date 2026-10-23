package test.dev.macula.cloud.system.mapper;

import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import dev.macula.cloud.system.mapper.SysApplicationMapper;
import dev.macula.cloud.system.pojo.bo.ApplicationBO;
import dev.macula.cloud.system.pojo.entity.SysApplication;
import dev.macula.cloud.system.query.ApplicationPageQuery;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DruidDataSourceAutoConfigure.class,
        com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class,
        dev.macula.boot.starter.mp.config.MyBatisPlusAutoConfiguration.class})
@MapperScan(basePackages = "dev.macula.cloud.system.mapper")
@TestPropertySource("classpath:application-db.properties")
public class TestSysApplicationMapper {

    @Autowired
    private SysApplicationMapper sysApplicationMapper;

    @Test
    public void listApplicationPages(){
        InterceptorIgnoreHelper.handle(IgnoreStrategy.builder().tenantLine(true).build());
        Page<SysApplication> page = new PageDTO<>(1, 1);
        ApplicationPageQuery query = new ApplicationPageQuery();
        query.setKeywords("bff");
        Page<ApplicationBO> res = sysApplicationMapper.listApplicationPages(page, query);
        Assertions.assertTrue(res.getSize() == 1, "分页数据不正确");
        Assertions.assertTrue(res.getTotal() == 1, "总数不正确");
        Assertions.assertTrue(res.getRecords().get(0).getCode().contains("bff"), "数据最值正确");
    }
}
