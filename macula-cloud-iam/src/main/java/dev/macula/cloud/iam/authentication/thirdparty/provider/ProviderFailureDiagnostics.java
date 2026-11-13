/*
 * Copyright (c) 2026 Macula
 *   macula.dev, China
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.macula.cloud.iam.authentication.thirdparty.provider;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 平台失败日志的安全投影，只提取数字码和已知权限，绝不参与身份判定。
 *
 * @author Rain
 * @since 6.1.0
 */
public final class ProviderFailureDiagnostics {
    private static final int MAX_RESPONSE_LENGTH = 16384;
    private static final int MAX_MESSAGE_LENGTH = 4096;
    private static final int MAX_PERMISSIONS = 16;
    private static final String UNKNOWN = "unknown";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern NUMBER = Pattern.compile("-?[0-9]{1,10}");
    private static final Pattern SUBCODE = Pattern.compile("(?:^|[\\[, ])subcode=([0-9]{1,10})(?=[,\\] ]|$)");
    private static final Set<String> DINGTALK_SCOPES = Set.of("qyapi_get_member", "Contact.User.Read");
    private static final Set<String> FEISHU_SCOPES = Set.of(
            "contact:user.id:readonly", "contact:user.base:readonly", "contact:user.employee:readonly",
            "contact:user.employee_number:read", "contact:user.employee_id:readonly",
            "contact:user.email:readonly", "contact:user.phone:readonly", "contact:contact:access_as_app",
            "contact:contact:readonly", "contact:contact:readonly_as_app");

    private static final Set<String> BINDING_STAGES = Set.of("account", "account_proof", "binding_write");
    private static final Set<String> BINDING_CLASSES = Set.of("ThirdPartyBindingService",
            "IdentityProviderUserBindingService", "IdentityProviderConfigService", "DefaultClientAccountDetailsService",
            "SysUserServiceImpl", "PendingThirdPartyBindingRepository", "IdentityDirectoryService$Admission");
    private static final Set<String> BINDING_METHODS = Set.of("requireClient", "validateAccount", "bind", "account",
            "bindVerified", "requireLoaded", "loadUserByClient", "details", "current", "findUniqueByUsername",
            "unique", "requireCurrent");

    /** 工具类不创建运行时状态或额外Bean。 */
    private ProviderFailureDiagnostics() {
    }

    /**
     * HTTP错误响应仅供诊断使用；过长或非法JSON退为null，不覆盖原失败语义。
     */
    public static JsonNode response(String body) {
        if (body == null || body.length() > MAX_RESPONSE_LENGTH) {
            return null;
        }
        try {
            return JSON.readTree(body);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * 旧接口的88错误可把subcode嵌入errmsg；只提取数字，正文永不输出。
     */
    public static void dingtalk(Logger logger, String stage, int status, JsonNode response) {
        String subcode = number(field(response, "sub_code"));
        if (UNKNOWN.equals(subcode)) {
            subcode = number(field(response, "subcode"));
        }
        String message = boundedText(field(response, "errmsg"));
        if (UNKNOWN.equals(subcode)) {
            var match = SUBCODE.matcher(message);
            if (match.find()) {
                subcode = match.group(1);
            }
        }
        var scopes = permissions(field(field(response, "accessdenieddetail"), "requiredScopes"), DINGTALK_SCOPES, false);
        // 旧错误仅提供正文中的权限列表；输出的始终是代码内固定标识，不能透传任意文本。
        for (String known : DINGTALK_SCOPES) {
            if (Pattern.compile("(?<![A-Za-z0-9_.:-])" + Pattern.quote(known) + "(?![A-Za-z0-9_.:-])")
                    .matcher(message).find()) {
                scopes.add(known);
            }
        }
        logger.warn("第三方接口失败 provider=DINGTALK stage={} status={} errcode={} subcode={} requiredScopes={}",
                stage, status, number(field(response, "errcode")), subcode, scopes);
    }

    /**
     * 飞书仅投影code及permission_violations.subject，不输出授权链接、log_id或msg。
     */
    public static void feishu(Logger logger, String stage, int status, JsonNode response) {
        var scopes = permissions(field(field(response, "error"), "permission_violations"), FEISHU_SCOPES, true);
        logger.warn("第三方接口失败 provider=FEISHU stage={} status={} code={} requiredScopes={}",
                stage, status, number(field(response, "code")), scopes);
    }

    /**
     * 在绑定统一包装异常前记录受控阶段与代码位置，不读取message/cause或输出完整堆栈。
     */
    public static void bindingFailure(Logger logger, String stage, RuntimeException failure) {
        String type = failure.getClass().getSimpleName();
        logger.warn("第三方账号绑定拒绝 stage={} exception={} site={}",
                BINDING_STAGES.contains(stage) ? stage : UNKNOWN,
                type.matches("[A-Za-z_$][A-Za-z0-9_$]{0,63}") ? type : UNKNOWN, bindingSite(failure));
    }

    /**
     * 只读取JVM代码元数据，跳过denied等包装帮助方法；字段均为显式白名单，非法位置记unknown。
     */
    private static String bindingSite(RuntimeException failure) {
        StackTraceElement[] frames = failure.getStackTrace();
        for (int index = 0; index < Math.min(frames.length, 32); index++) {
            StackTraceElement frame = frames[index];
            String name = frame.getClassName();
            String simple = name.substring(name.lastIndexOf('.') + 1);
            if (name.startsWith("dev.macula.cloud.iam.") && BINDING_CLASSES.contains(simple)
                    && BINDING_METHODS.contains(frame.getMethodName())
                    && frame.getLineNumber() > 0 && frame.getLineNumber() <= 100000) {
                return simple + "." + frame.getMethodName() + ":" + frame.getLineNumber();
            }
        }
        return UNKNOWN;
    }

    /** 仅获取对象字段，缺失/非法结构均作未知诊断。 */
    private static JsonNode field(JsonNode node, String name) {
        return node == null || !node.isObject() ? null : node.get(name);
    }

    /** 数字文本也可接受，但不允许任意字符串、超长数字或容器序列化进日志。 */
    private static String number(JsonNode node) {
        String value = boundedText(node);
        return NUMBER.matcher(value).matches() ? value : UNKNOWN;
    }

    /** 在正则或匹配前限制长度，不将容器节点转成原始JSON。 */
    private static String boundedText(JsonNode node) {
        if (node == null || !(node.isString() || node.isIntegralNumber())) {
            return "";
        }
        String value = node.asText();
        return value.length() > MAX_MESSAGE_LENGTH ? "" : value;
    }

    /** 已知权限集合与数量双重限制，未知标识即使语法合法也不能进入日志。 */
    private static Set<String> permissions(JsonNode values, Set<String> allowed, boolean subjects) {
        Set<String> result = new LinkedHashSet<>();
        if (values != null && values.isArray()) {
            int count = Math.min(values.size(), MAX_PERMISSIONS);
            for (int index = 0; index < count; index++) {
                JsonNode item = values.get(index);
                String value = boundedText(subjects ? field(item, "subject") : item);
                if (allowed.contains(value)) {
                    result.add(value);
                }
            }
        }
        return result;
    }
}
