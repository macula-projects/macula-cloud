package dev.macula.cloud.system.aspect;

import cn.hutool.extra.servlet.ServletUtil;
import com.alibaba.fastjson.JSONObject;
import dev.macula.boot.starter.security.utils.SecurityUtils;
import dev.macula.cloud.system.annotation.AuditLog;
import dev.macula.cloud.system.pojo.entity.SysLog;
import dev.macula.cloud.system.service.SysLogService;
import dev.macula.cloud.system.utils.ServletUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

/**
 * 审计日志切面类 使用方式： 在需要审计的接口使用注解 @AuditLog 例如：
 *
 * @AuditLog(title = "接口说明")
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class AuditLogAspect {

    private final SysLogService sysLogService;
    private static final String LOCAL_HOST = "127.0.0.1";
    private static final String LOCAL_HOST_IPV6 = "0:0:0:0:0:0:0:1";

    /**
     * 处理完请求后执行
     *
     * @param joinPoint 切点
     */
    @AfterReturning(pointcut = "@annotation(controllerLog)", returning = "jsonResult")
    public void doAfterReturning(JoinPoint joinPoint, AuditLog controllerLog, Object jsonResult) {
        handleLog(joinPoint, controllerLog, null, jsonResult);
    }

    protected void handleLog(final JoinPoint joinPoint, AuditLog controllerLog, final Exception e, Object jsonResult) {
        try {

            // 日志记录
            SysLog opLog = new SysLog();
            opLog.setOpStatus(0);

            // 获取当前的用户
            String loginUser = SecurityUtils.getCurrentUser();
            if (StringUtils.isNotEmpty(loginUser)) {
                opLog.setOpName(loginUser);
            }

            // 请求的IP地址
            String ip = ServletUtil.getClientIP(ServletUtils.getRequest());
            if (LOCAL_HOST_IPV6.equals(ip)) {
                ip = LOCAL_HOST;
            }
            opLog.setOpIp(ip);
            opLog.setOpUrl(ServletUtils.getRequest().getRequestURI());

            if (e != null) {
                opLog.setOpStatus(1);
                opLog.setErrorMsg(StringUtils.substring(e.getMessage(), 0, 2000));
            }
            // 设置方法名称
            String className = joinPoint.getTarget().getClass().getName();
            String methodName = joinPoint.getSignature().getName();
            opLog.setOpMethod(className + "." + methodName + "()");
            opLog.setOpRequestMethod(ServletUtils.getRequest().getMethod());
            opLog.setCreateTime(LocalDateTime.now());
            // 处理设置注解上的参数
            getControllerMethodDescription(joinPoint, controllerLog, opLog, jsonResult);
            // 保存数据库
            sysLogService.save(opLog);

        } catch (Exception exp) {
            log.error("异常信息:{}", exp.getMessage());
            exp.printStackTrace();
        }
    }

    /**
     * 获取注解中对方法的描述信息 用于Controller层注解
     *
     * @param log   日志
     * @param opLog 操作日志
     */
    public void getControllerMethodDescription(JoinPoint joinPoint, AuditLog log, SysLog opLog, Object jsonResult) {

        // 设置标题
        opLog.setOpTitle(log.title());
        // 是否需要保存request，参数和值
        if (log.isSaveRequestData()) {
            // 设置参数的信息
            setRequestValue(joinPoint, opLog);
        }
        // 是否需要保存response，参数和值
        if (log.isSaveResponseData() && ObjectUtils.isNotEmpty(jsonResult)) {
            opLog.setJsonResult(StringUtils.substring(JSONObject.toJSONString(jsonResult), 0, 2000));
        }
    }

    /**
     * 获取请求的参数，放到log中
     *
     * @param opLog 操作日志
     */
    private void setRequestValue(JoinPoint joinPoint, SysLog opLog) {
        String requestMethod = opLog.getOpRequestMethod();
        if (HttpMethod.PUT.matches(requestMethod) || HttpMethod.POST.matches(
            requestMethod) || HttpMethod.DELETE.matches(requestMethod) || HttpMethod.PATCH.matches(requestMethod)) {
            String params = argsArrayToString(joinPoint.getArgs());
            opLog.setOpParam(StringUtils.substring(params, 0, 2000));
        } else {
            Map<?, ?> paramsMap =
                (Map<?, ?>)ServletUtils.getRequest().getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
            opLog.setOpParam(StringUtils.substring(paramsMap.toString(), 0, 2000));
        }
    }

    /**
     * 参数拼装
     */
    private String argsArrayToString(Object[] paramsArray) {
        StringBuilder params = new StringBuilder();
        if (paramsArray != null) {
            for (Object object : paramsArray) {
                // 不为空 并且是不需要过滤的 对象
                if (ObjectUtils.isNotEmpty(object) && !isFilterObject(object)) {
                    Object jsonObj = JSONObject.toJSON(object);
                    params.append(jsonObj.toString()).append(" ");
                }
            }
        }
        return params.toString().trim();
    }

    /**
     * 判断是否需要过滤的对象。
     *
     * @param object 对象信息。
     * @return 如果是需要过滤的对象，则返回true；否则返回false。
     */
    @SuppressWarnings("rawtypes")
    public boolean isFilterObject(final Object object) {
        Class<?> clazz = object.getClass();
        if (clazz.isArray()) {
            return clazz.getComponentType().isAssignableFrom(MultipartFile.class);
        } else if (Collection.class.isAssignableFrom(clazz)) {
            Collection collection = (Collection)object;
            for (Object value : collection) {
                return value instanceof MultipartFile;
            }
        } else if (Map.class.isAssignableFrom(clazz)) {
            Map map = (Map)object;
            for (Object value : map.entrySet()) {
                Map.Entry entry = (Map.Entry)value;
                return entry.getValue() instanceof MultipartFile;
            }
        }
        return object instanceof MultipartFile || object instanceof HttpServletRequest || object instanceof HttpServletResponse || object instanceof BindingResult;
    }

}
