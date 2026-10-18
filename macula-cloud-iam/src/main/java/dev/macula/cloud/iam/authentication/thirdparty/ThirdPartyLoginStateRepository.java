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
package dev.macula.cloud.iam.authentication.thirdparty;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 创建并原子消费第三方登录 state。
 *
 * @author Rain
 * @since 6.1.0
 */
public interface ThirdPartyLoginStateRepository {
    String create(HttpServletRequest request, ThirdPartyLoginContext context);

    ThirdPartyLoginContext consume(HttpServletRequest request, String providerType, String state);
}
