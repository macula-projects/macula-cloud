package dev.macula.cloud.system.config;

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import dev.macula.cloud.system.filter.CustomAppTenantFilter;
import dev.macula.cloud.system.service.SysTenantService;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 临时重写mybatis的过滤器插件， 根据过滤是实时读取请求参数渲染租户id
 */
@Deprecated
@Configuration
@Slf4j
public class CustomSystemMybatisPlusInterceptor implements BeanPostProcessor {
//    private static final Set<String> CONTAINS_TENANT_TABLE = CollectionUtils.set("sys_application", "sys_dept", "sys_dict_item", "sys_menu");
    private static final Set<String> CONTAINS_TENANT_TABLE = new HashSet<>();//CollectionUtils.set("sys_application", "sys_dept", "sys_dict_item", "sys_menu");

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof MybatisPlusInterceptor) {
            MybatisPlusInterceptor mybatisPlusInterceptor = (MybatisPlusInterceptor) bean;
            mybatisPlusInterceptor.getInterceptors().stream()
                    .filter(inter -> inter instanceof TenantLineInnerInterceptor)
                    .forEach(inter -> {
                        TenantLineInnerInterceptor tenantInter = (TenantLineInnerInterceptor) inter;
                        tenantInter.setTenantLineHandler(new TenantLineHandler() {
                            @Override
                            public Expression getTenantId() {
                                return Objects.isNull(CustomAppTenantFilter.getCurTenantId()) ? new LongValue(0L) : new LongValue(CustomAppTenantFilter.getCurTenantId());
                            }

                            @Override
                            public boolean ignoreTable(String tableName) {
                                return !CONTAINS_TENANT_TABLE.contains(tableName);
                            }
                        });
                    });
        }
        return bean;
    }

    @Bean
    @ConditionalOnBean(SysTenantService.class)
    public FilterRegistrationBean<CustomAppTenantFilter> addAppTenantFilter(SysTenantService sysTenantService) {
        CustomAppTenantFilter appTenantFilter = new CustomAppTenantFilter(sysTenantService);
        FilterRegistrationBean filterRegistrationBean = new FilterRegistrationBean(appTenantFilter);
        filterRegistrationBean.addUrlPatterns("/*");
        return filterRegistrationBean;
    }
}
