package dev.macula.cloud.system.filter;

import dev.macula.cloud.system.service.SysTenantService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.NamedInheritableThreadLocal;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简易获取指定表包含system租户的信息
 * @author qiuyuhao
 */
@Deprecated
public class CustomAppTenantFilter extends OncePerRequestFilter {
    private static final String TENANT_Id = "tenantId";

    private static SysTenantService sysTenantService;

    private static final ThreadLocal<Long> TENANT_ID_THREAD_COCAL = new NamedInheritableThreadLocal<>(TENANT_Id);

    public CustomAppTenantFilter(SysTenantService sysTenantService){
        CustomAppTenantFilter.sysTenantService = sysTenantService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try{
            if(Objects.nonNull(request.getParameter(TENANT_Id))){
                Long tenantId = Long.valueOf(request.getParameter(TENANT_Id));
                TENANT_ID_THREAD_COCAL.set(tenantId);
            }
            filterChain.doFilter(request,response);
        } finally {
            TENANT_ID_THREAD_COCAL.remove();
        }
    }

    public static Long getCurTenantId(){
        // TODO 验证tenantId准确性及数据库是否存在相关数据， 临时类不做精细工作
        return Objects.nonNull(TENANT_ID_THREAD_COCAL.get()) ? TENANT_ID_THREAD_COCAL.get() : null;
    }

}
