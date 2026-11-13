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
package dev.macula.cloud.system.protocol;

import dev.macula.cloud.system.protocol.oauth2.OAuth2ProtocolSettings;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import dev.macula.cloud.system.enums.ExternalCodeType;
import lombok.Data;

/**
 * OAuth家族v2设置；组合v1策略并保存外部Code许可，不复制来源或秘密。
 * 不修改v1字段，确保历史Flyway迁移仍按原始合同序列化。
 * @author Rain
 * @since 6.1.0
 */
@Data
public class OAuth2ApplicationProtocolSettings {
    private OAuth2ProtocolSettings oauth2;
    private Set<ExternalCodeType> externalCodeTypes = Set.of();

    /** 即使调用方Mapper配置宽松，也不能吞掉拼错或注入的配置字段。 */
    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("协议配置包含未知字段");
    }
}
