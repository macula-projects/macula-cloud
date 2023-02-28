package dev.macula.cloud.system.filter;

import org.springframework.core.NamedInheritableThreadLocal;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;

/**
 * 简易获取指定表包含system租户的信息
 * @author qiuyuhao
 */
@Deprecated
public class CustomAppTenantFilter extends OncePerRequestFilter {
    private static final String TENANT_Id = "tenantId";

    private static final ThreadLocal<Long> TENANT_ID_THREAD_COCAL = new NamedInheritableThreadLocal<>(TENANT_Id);

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
        return Objects.nonNull(TENANT_ID_THREAD_COCAL.get()) ? TENANT_ID_THREAD_COCAL.get() : null;
    }

    /**
     * 特殊环境需要内部动态设置租户id，比如创建租户需给新增租户赋予默认菜单
     * @param tenantId
     */
    public static void setCurTenantId(Long tenantId){
        TENANT_ID_THREAD_COCAL.set(tenantId);
    }

}
