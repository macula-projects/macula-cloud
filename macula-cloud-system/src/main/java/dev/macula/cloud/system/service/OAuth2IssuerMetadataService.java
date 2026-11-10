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
package dev.macula.cloud.system.service;

import dev.macula.cloud.system.vo.OAuth2IssuerMetadataVO;

/**
 * 提供可用于接入说明的已验证端点。
 *
 * @author Rain
 * @since 6.1.0
 */
public interface OAuth2IssuerMetadataService {
    /** 延迟加载；失败只影响接入信息展示。 */
    /** 按协议读取元数据，未知协议不能降级。 */
    OAuth2IssuerMetadataVO get(String protocol);

    /** 既有管理调用的显式默认保持OIDC，不用于OAuth失败回退。 */
    default OAuth2IssuerMetadataVO get() { return get("OIDC"); }
}
