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

import dev.macula.cloud.system.enums.ExternalCodeType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 按稳定 code type 选择唯一 verifier，拒绝条件分支回退或多端点试探。
 *
 * @author Rain
 * @since 6.1.0
 */
@Component
public class ExternalCodeVerifierRegistry {
    private final Map<ExternalCodeType, ExternalCodeVerifier> verifiers;

    public ExternalCodeVerifierRegistry(List<ExternalCodeVerifier> candidates) {
        Map<ExternalCodeType, ExternalCodeVerifier> mappings = new EnumMap<>(ExternalCodeType.class);
        for (ExternalCodeVerifier candidate : candidates) {
            for (ExternalCodeType type : candidate.supportedTypes()) {
                if (mappings.putIfAbsent(type, candidate) != null) {
                    throw new IllegalStateException("外部凭证类型存在多个 verifier: " + type.name());
                }
            }
        }
        this.verifiers = Map.copyOf(mappings);
    }

    public ExternalCodeVerifier require(ExternalCodeType codeType) {
        ExternalCodeVerifier verifier = verifiers.get(codeType);
        if (verifier == null) {
            throw new IllegalStateException("外部凭证类型没有 verifier: " + codeType.name());
        }
        return verifier;
    }
}
