package dev.macula.cloud.system.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import dev.macula.cloud.system.filter.CustomAppTenantFilter;
import dev.macula.cloud.system.service.SysTenantService;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;

/**
 * 根据过滤是实时读取请求参数渲染租户id
 */
@Deprecated
@Configuration
public class CustomSystemMybatisPlusInterceptor {


    @Bean
    @ConditionalOnBean({MybatisPlusInterceptor.class,CustomAppTenantFilter.class})
    public MybatisPlusInterceptor extendMybatisPlusAutoConfiguration(MybatisPlusInterceptor mybatisPlusInterceptor){
        mybatisPlusInterceptor.getInterceptors().stream()
                .filter(inter->inter instanceof TenantLineInnerInterceptor)
                .forEach(inter->{
                    TenantLineInnerInterceptor tenantInter = (TenantLineInnerInterceptor) inter;
                    TenantLineHandler oldTenantLineHandler = tenantInter.getTenantLineHandler();
                    tenantInter.setTenantLineHandler(new TenantLineHandler() {
                        @Override
                        public Expression getTenantId() {
                            return Objects.isNull(CustomAppTenantFilter.getCurTenantId())? oldTenantLineHandler.getTenantId(): new LongValue(CustomAppTenantFilter.getCurTenantId());
                        }

                        @Override
                        public boolean ignoreTable(String tableName) {
                            return oldTenantLineHandler.ignoreTable(tableName);
                        }
                    });
                });
        return mybatisPlusInterceptor;
    }

    @Bean
    @ConditionalOnBean(SysTenantService.class)
    public FilterRegistrationBean<CustomAppTenantFilter> addAppTenantFilter(SysTenantService sysTenantService){
        CustomAppTenantFilter appTenantFilter = new CustomAppTenantFilter(sysTenantService);
        FilterRegistrationBean filterRegistrationBean =new FilterRegistrationBean(appTenantFilter);
        filterRegistrationBean.addUrlPatterns("/*");
        return filterRegistrationBean;
    }
}
