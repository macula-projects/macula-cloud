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

import dev.macula.cloud.iam.authentication.thirdparty.VerifiedExternalIdentity;
import org.springframework.util.Assert;

/**
 * 外部凭证核验结果，始终保留确定来源和实际已核验属性。
 * @author Rain
 * @since 6.1.0
 */
public record ExternalCodeVerification(VerifiedExternalIdentity identity) {
    /** 缺少确定来源的结果不能进入后续解析。 */
    public ExternalCodeVerification { Assert.notNull(identity, "verified identity is required"); }
    /** 返回厂商协议值。 */
    public String providerType() { return identity.providerType(); }
    /** 返回外部身份键，仅身份型结果有值。 */
    public String subject() { return mobile() == null ? identity.identityValue() : null; }
    /** 返回已核验手机号，不是端侧提交的账号信息。 */
    public String mobile() {
        return VerifiedExternalIdentity.MOBILE_PROOF.equals(identity.identityKind()) ? identity.identityValue() : null;
    }
}
