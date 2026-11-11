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
package dev.macula.cloud.iam.service.identity.directory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import dev.macula.cloud.iam.authentication.thirdparty.VerifiedExternalIdentity;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService;
import org.springframework.security.authentication.BadCredentialsException;

/**
 * 公共身份服务所需的目录准入端口；隔离平台网络、缓存与后台刷新实现。
 *
 * @author Rain
 * @since 6.1.0
 */
public interface IdentityDirectoryService {
    /**
     * 事务外核验当前成员身份，返回有界有效期的可信快照；不可用时拒绝。
     */
    Admission check(VerifiedExternalIdentity identity);

    /**
     * 补充服务端核实的白名单资料，不改变来源、主体键或账号归属。
     */
    VerifiedExternalIdentity enrich(VerifiedExternalIdentity identity, Admission admission);

    /**
     * 仅在账号解析或绑定成功后安排必要的后台资料刷新。
     */
    void bound(VerifiedExternalIdentity identity);

    /**
     * 无网络的绑定事务准入投影。
     *
     * @since 6.1.0
     */
    public record Admission(VerifiedExternalIdentity identity, Long sourceVersion, String flow, String organizationId,
                            Instant expiresAt, Map<String, String> profile) {
        /**
         * 固化服务端资料，不能由业务客户端构造准入响应。
         */
        public Admission {
            Objects.requireNonNull(identity);
            Objects.requireNonNull(expiresAt);
            if (!Objects.equals(identity.sourceVersion(), sourceVersion) || !identity.flow().equals(flow)) {
                throw new IllegalArgumentException("目录快照来源不一致");
            }
            profile = Map.copyOf(profile);
        }

        /**
         * 仅比较有效期/来源时可不携带资料。
         */
        public Admission(VerifiedExternalIdentity identity, String organizationId, Instant expiresAt) {
            this(identity, identity.sourceVersion(), identity.flow(), organizationId, expiresAt, Map.of());
        }

        /**
         * 在锁内复核完整主体键及快照，无外部调用，不延长证明有效期。
         */
        public void requireCurrent(VerifiedExternalIdentity expected,
                IdentityProviderConfigService.ProviderConfig source) {
            if (!Objects.equals(identity.registrationId(), expected.registrationId())
                    || !identity.identityKind().equals(expected.identityKind()) ||
                    !identity.identityValue().equals(expected.identityValue())
                    || !Objects.equals(sourceVersion, source.version()) || !flow.equals(source.flow().value())
                    || !Objects.equals(organizationId, source.organizationId()) || !expiresAt.isAfter(Instant.now())) {
                throw new BadCredentialsException("目录准入证明无效");
            }
        }

        /**
         * 不在日志输出手机号、工号等资料。
         */
        @Override
        public String toString() {
            return "Admission[redacted]";
        }
    }
}
