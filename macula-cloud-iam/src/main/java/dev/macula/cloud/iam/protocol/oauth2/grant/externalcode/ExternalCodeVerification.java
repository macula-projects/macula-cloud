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

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * 第三方服务端核验后得到的最小可信身份结果。
 *
 * @author Rain
 * @since 6.1.0
 */
public record ExternalCodeVerification(String providerType, String subject, String mobile) {
    public ExternalCodeVerification {
        boolean identity = StringUtils.hasText(providerType) && StringUtils.hasText(subject) && mobile == null;
        boolean phone = providerType == null && subject == null && StringUtils.hasText(mobile);
        Assert.isTrue(identity || phone, "exactly one verified identity result is required");
    }

    public static ExternalCodeVerification identity(String providerType, String subject) {
        Assert.hasText(providerType, "providerType cannot be empty");
        Assert.hasText(subject, "subject cannot be empty");
        return new ExternalCodeVerification(providerType, subject, null);
    }

    public static ExternalCodeVerification mobile(String mobile) {
        Assert.hasText(mobile, "mobile cannot be empty");
        return new ExternalCodeVerification(null, null, mobile);
    }
}
