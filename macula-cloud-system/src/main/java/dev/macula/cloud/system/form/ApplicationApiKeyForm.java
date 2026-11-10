/*
 * Copyright (c) 2026 Macula
 * macula.dev, China
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

package dev.macula.cloud.system.form;

import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Data;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import dev.macula.boot.security.AccessRuleUtils;
import jakarta.validation.constraints.AssertTrue;

/**
 * API Key 的可编辑配置，不接受租户、应用或凭据覆盖。
 * @author Rain
 * @since 6.1.0
 */
@Data
public class ApplicationApiKeyForm {
    @Size(max = 255)
    private String remark;
    @Size(max = 2000)
    private String accessPath;

    @Size(max = AccessRuleUtils.MAX_RULES_LENGTH)
    private String allowIps;

    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean allowIpsProvided;

    /** 标记来源 IP 被显式提交，区分省略保留与 null 清空。 */
    @JsonSetter("allowIps")
    public void setAllowIps(String allowIps) {
        this.allowIps = allowIps;
        this.allowIpsProvided = true;
    }

    /** 返回是否显式提交来源 IP，服务层据此决定是否更新该列。 */
    @JsonIgnore
    public boolean isAllowIpsProvided() {
        return allowIpsProvided;
    }

    /** 表单校验与网关使用同一解析规则，不接受错误 IP 或纯分隔符配置。 */
    @JsonIgnore
    @AssertTrue(message = "URL/IP 规则格式不合法或超过 2000 字符")
    public boolean isAccessRulesValid() {
        try {
            AccessRuleUtils.normalizeUrls(accessPath);
            AccessRuleUtils.normalizeIps(allowIps);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /** 在持久化前统一换行与空白，非 HTTP 调用也必须执行规则校验。 */
    public void normalizeAccessRules() {
        this.accessPath = AccessRuleUtils.normalizeUrls(accessPath);
        if (allowIpsProvided) {
            this.allowIps = AccessRuleUtils.normalizeIps(allowIps);
        }
    }

    /** 显式拒绝额外身份字段，避免误以为客户端可以指定 Key 或租户。 */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("不支持的 API Key 配置字段");
    }
}
