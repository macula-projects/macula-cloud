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

/**
 * 平台凭据的消费确定性；未知结果不能释放防重放占用。
 *
 * @author Rain
 * @since 6.1.0
 */
public enum ExternalCodeConsumptionState {
    NOT_SUBMITTED,
    UNKNOWN,
    CONSUMED
}
