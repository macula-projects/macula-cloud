/*
 * Copyright (c) 2023 Macula
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

package dev.macula.cloud.iam.protocol.oauth2.endpoint;

/**
 * IAM OAuth2扩展错误码，值属于对外协议合同。
 *
 * @author jumuning
 * @since 6.1.0
 */
public final class OAuth2ExtensionErrorCodes {
    /**
     * 已核验的外部身份需要绑定已有本地账号。
     */
    public static final String BINDING_REQUIRED = "binding_required";

    /**
     * 协议稳定标识容器，不允许实例化或实现。
     */
    private OAuth2ExtensionErrorCodes() {
    }

}
