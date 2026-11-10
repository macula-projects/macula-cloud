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
package dev.macula.cloud.system.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 可信Discovery来源；不接受管理请求覆盖URL。
 *
 * @author Rain
 * @since 6.1.0
 */
@Data
@ConfigurationProperties("macula.iam.metadata")
public class OAuth2IssuerMetadataProperties {
    private String discoveryUrl;
    private String expectedIssuer;
    private boolean allowHttp = false;
    private int connectTimeoutMillis = 2000;
    private int readTimeoutMillis = 3000;
    private int cacheSeconds = 60;
}
