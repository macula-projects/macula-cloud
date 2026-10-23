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
package dev.macula.cloud.iam.protocol.oauth2.grant.externalcode;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * external_code 防重放和服务端摘要配置。
 *
 * @author Rain
 * @since 6.1.0
 */
@Data
@Component
@ConfigurationProperties("macula.cloud.iam.external-code")
public class ExternalCodeSecurityProperties {
    private Duration replayTtl = Duration.ofMinutes(10);
    private Duration bindingTicketTtl = Duration.ofMinutes(5);
    private Duration bindingTicketClaimLease = Duration.ofSeconds(30);
    private int bindingTicketMaxAttempts = 5;
    private String digestKey;
}
