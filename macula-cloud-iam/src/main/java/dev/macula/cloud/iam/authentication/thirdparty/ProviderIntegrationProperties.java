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

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 平台应用凭据缓存与后台资料刷新边界。
 * @author Rain
 * @since 6.1.0
 */
@Data
@Component
@ConfigurationProperties("macula.cloud.iam.providers")
public class ProviderIntegrationProperties {
    private Duration tokenLockLease = Duration.ofSeconds(15);
    private Duration tokenWait = Duration.ofSeconds(20);
    private Duration tokenExpirySkew = Duration.ofSeconds(60);
    private Duration memberSnapshotTtl = Duration.ofMinutes(5);
    private Duration refreshLease = Duration.ofSeconds(30);
    private int refreshMaxAttempts = 3;
    private int refreshQueueCapacity = 1000;
}
