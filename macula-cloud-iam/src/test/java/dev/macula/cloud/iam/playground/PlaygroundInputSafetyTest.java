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

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import dev.macula.boot.starter.web.advice.ControllerExceptionAdvice;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 装配真实 Macula Boot 全局异常处理，防止校验/JSON 解析错误将凭据写入日志或响应。
 * @author Rain
 * @since 6.1.0
 */
class PlaygroundInputSafetyTest {
    private static final String MARKER = "SYNTHETIC_INPUT_SECRET_";
    private final JsonMapper json = JsonMapper.builder().build();
    private final PlaygroundFlowService flows = mock(PlaygroundFlowService.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new PlaygroundApiController(flows))
            .setControllerAdvice(new ControllerExceptionAdvice()).build();
        logs.start();
        root.addAppender(logs);
    }

    @AfterEach void cleanup() { root.detachAppender(logs); logs.stop(); }

    @Test void validationRejectsSensitiveFieldsWithoutLoggingTheirRejectedValues() throws Exception {
        for (String field : new String[]{"password", "captcha", "phone", "username", "state", "nonce", "challenge"}) {
            safeFailure("/flows", json.writeValueAsString(Map.of("scenario", "PASSWORD", field, MARKER + "x".repeat(300))));
        }
        for (String field : new String[]{"code", "verifier", "state"}) {
            safeFailure("/flows/fixture/callback", json.writeValueAsString(Map.of(field, MARKER + "x".repeat(4100))));
        }
        verifyNoInteractions(flows);
    }

    @Test void unreadableJsonAndUnknownFieldsNeverExposeParserInput() throws Exception {
        safeFailure("/flows", json.writeValueAsString(Map.of("scenario", MARKER)));
        safeFailure("/flows", "{\"scenario\":\"PASSWORD\",\"password\":\"" + MARKER);
        safeFailure("/flows", json.writeValueAsString(Map.of("scenario", "PASSWORD", "password", Map.of("value", MARKER))));
        safeFailure("/flows", json.writeValueAsString(Map.of("scenario", "PASSWORD", "unexpected", MARKER)));
        safeFailure("/flows/fixture/callback", json.writeValueAsString(Map.of("code", Map.of("value", MARKER))));
        safeFailure("/flows/fixture/callback", json.writeValueAsString(Map.of("unexpected", MARKER)));
        verifyNoInteractions(flows);
    }

    private void safeFailure(String path, String input) throws Exception {
        logs.list.clear();
        var response = mvc.perform(post("/api/v1/iam-playground" + path)
            .contentType(MediaType.APPLICATION_JSON).content(input)).andReturn().getResponse();
        boolean leaked = logs.list.stream().anyMatch(event -> event.getFormattedMessage().contains(MARKER)
            || (event.getThrowableProxy() != null && ThrowableProxyUtil.asString(event.getThrowableProxy()).contains(MARKER)));
        assertThat(leaked).as("Sensitive input must not enter log messages or exception stacks").isFalse();
        assertThat(response.getContentAsString()).doesNotContain(MARKER);
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(json.readTree(response.getContentAsString()).get("error").asText()).isEqualTo("invalid_playground_operation");
    }
}
