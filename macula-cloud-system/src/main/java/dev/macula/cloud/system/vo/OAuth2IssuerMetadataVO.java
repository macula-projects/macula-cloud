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
package dev.macula.cloud.system.vo;

import java.util.Map;

/**
 * 只返回白名单端点和受控状态，不透传原始网络错误。
 *
 * @author Rain
 * @since 6.1.0
 */
public record OAuth2IssuerMetadataVO(String protocol, boolean available, String message, Map<String, String> endpoints) {
    /** 老管理调用明确指向OIDC；协议化API均显式提供protocol。 */
    public OAuth2IssuerMetadataVO(boolean available, String message, Map<String, String> endpoints) {
        this("OIDC", available, message, endpoints);
    }

    /** 防止缓存结果被调用者修改。 */
    public OAuth2IssuerMetadataVO {
        endpoints = Map.copyOf(endpoints);
    }
}
