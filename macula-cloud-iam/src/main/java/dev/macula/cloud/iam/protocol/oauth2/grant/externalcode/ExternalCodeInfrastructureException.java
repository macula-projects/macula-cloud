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

import org.springframework.security.authentication.AuthenticationServiceException;

/**
 * 标识平台故障及凭据消费确定性；默认不能推断Code尚未被消费。
 *
 * @author Rain
 * @since 6.1.0
 */
public class ExternalCodeInfrastructureException extends AuthenticationServiceException {
    private final ExternalCodeConsumptionState consumptionState;

    /**
     * 超时/网络错误未证明请求没有到达平台，默认保留占用。
     */
    public ExternalCodeInfrastructureException() {
        this(ExternalCodeConsumptionState.UNKNOWN);
    }

    /**
     * 仅平台适配器可根据实际执行阶段声明尚未提交或已消费。
     */
    public ExternalCodeInfrastructureException(ExternalCodeConsumptionState consumptionState) {
        super("External identity provider is temporarily unavailable.");
        this.consumptionState = java.util.Objects.requireNonNull(consumptionState);
    }

    /**
     * 返回执行阶段证明的消费状态，不包括凭据或远端正文。
     */
    public ExternalCodeConsumptionState getConsumptionState() {
        return consumptionState;
    }
}
