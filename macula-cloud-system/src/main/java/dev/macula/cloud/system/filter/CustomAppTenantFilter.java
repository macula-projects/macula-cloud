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
    /**
     * 应用id与租户id映射关系
     */
    private static final Map<String, Long> APP_TENANT_ID_MAP = new ConcurrentHashMap<>();

    private static SysTenantService sysTenantService;

    private static final ThreadLocal<String> APP_NAME_THREAD_COCAL = new NamedInheritableThreadLocal<>("appCode");

    public CustomAppTenantFilter(SysTenantService sysTenantService){
        CustomAppTenantFilter.sysTenantService = sysTenantService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try{
            String appCode = request.getParameter("appCode");
            if(StringUtils.isNotBlank(appCode)){
                APP_NAME_THREAD_COCAL.set(appCode);
            }
            filterChain.doFilter(request,response);
        } finally {
            APP_NAME_THREAD_COCAL.remove();
        }
    }

    public static Long getAppCodeTenantId(String appCode){
        Long tenantId = APP_TENANT_ID_MAP.get(appCode);
        if(Objects.isNull(tenantId)){
            tenantId = sysTenantService.getAppTenantId(appCode);
            APP_TENANT_ID_MAP.put(appCode, tenantId);
        }
        return tenantId;
    }

    public static Long getCurTenantId(){
        return StringUtils.isNotBlank(APP_NAME_THREAD_COCAL.get()) ? getAppCodeTenantId(APP_NAME_THREAD_COCAL.get()) : getSystemTenantId();
    }

    public static Long getSystemTenantId(){
        return sysTenantService.getSystemTenantId();
    }

    public static void clearMap(){
        APP_TENANT_ID_MAP.clear();
    }
}
