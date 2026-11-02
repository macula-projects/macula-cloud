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
package dev.macula.cloud.iam.authentication.aliyun;

import com.aliyun.tea.TeaException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证阿里云服务端错误只在明确的 5xx 情况下视为可重试基础设施故障。
 *
 * @author Rain
 * @since 6.1.0
 */
class AliyunMobileExternalCodeVerifierTest {

    @Test
    void releasesReplayClaimOnlyForExplicitServerFailure() {
        TeaException clientFailure = exception(400);
        TeaException serverFailure = exception(503);

        assertThat(AliyunMobileExternalCodeVerifier.isInfrastructureFailure(clientFailure)).isFalse();
        assertThat(AliyunMobileExternalCodeVerifier.isInfrastructureFailure(serverFailure)).isTrue();
    }

    private TeaException exception(int status) {
        return new TeaException(Map.of("code", "fixture", "message", "fixture",
            "data", Map.of("statusCode", status)));
    }
}
