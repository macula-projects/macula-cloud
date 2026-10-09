/*
 * Copyright (c) 2023 Macula
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

package dev.macula.cloud.iam.playground;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

/**
 * 固定演示动作输入；不接收目标 URL、client_id、secret 或任意 token。
 * @author Rain
 * @since 6.1.0
 */
public record PlaygroundRequest(
    @NotNull PlaygroundFlow.Scenario scenario,
    @Size(max = 128) String state,
    @Size(max = 128) String nonce,
    @Size(max = 128) String challenge,
    @Size(max = 128) String username,
    @Size(max = 256) String password,
    @Size(max = 32) String phone,
    @Size(max = 32) String captcha) {
    @Override public String toString() { return "PlaygroundRequest[redacted]"; }
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknown(String name, Object value) { throw new IllegalArgumentException("Unsupported playground input"); }

    public record Callback(@Size(max = 128) String state, @Size(max = 4096) String code,
        @Size(max = 128) String verifier) {
        @Override public String toString() { return "Callback[redacted]"; }
        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void rejectUnknown(String name, Object value) { throw new IllegalArgumentException("Unsupported callback input"); }
    }
}
