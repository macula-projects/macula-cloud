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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

/**
 * 演示专用固定动作 API；会话归属、协议编排与敏感输出处理由 Service 完成。
 * @author Rain
 * @since 6.1.0
 */
@RestController
@RequestMapping("/api/v1/iam-playground")
public class PlaygroundApiController {
    private final PlaygroundFlowService flows;
    public PlaygroundApiController(PlaygroundFlowService flows) { this.flows = flows; }

    @GetMapping("/configuration")
    public Map<String, Object> configuration(HttpServletRequest request) {
        var result = new LinkedHashMap<>(flows.configuration());
        var csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        result.put("csrfToken", csrf.getToken());
        result.put("csrfHeader", csrf.getHeaderName());
        return result;
    }

    @PostMapping("/flows")
    public PlaygroundResponse start(HttpSession session, @Valid @RequestBody PlaygroundRequest request) {
        return flows.start(session, request);
    }

    @GetMapping("/flows/{id}")
    public PlaygroundResponse state(HttpSession session, @PathVariable String id) { return flows.state(session, id); }

    @PostMapping("/flows/{id}/callback")
    public PlaygroundResponse callback(HttpSession session, @PathVariable String id,
        @Valid @RequestBody PlaygroundRequest.Callback request) { return flows.exchange(session, id, request); }

    @PostMapping("/flows/{id}/actions/{action}")
    public PlaygroundResponse action(HttpSession session, @PathVariable String id, @PathVariable String action,
        @RequestBody(required = false) Map<String, Object> input) {
        if (input != null && !input.isEmpty()) throw new IllegalArgumentException("Actions only accept a session flow reference");
        return flows.action(session, id, action);
    }

    @PostMapping("/reset")
    public Map<String, Object> reset(HttpSession session) {
        flows.reset(session);
        return Map.of("cleared", true);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class,
        MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> invalidFlow() {
        // Handle binding failures locally too: global advice logs rejected values/parser input.
        // Never log or return exception/cause text, even before the controller method is entered.
        return Map.of("error", "invalid_playground_operation", "message", "流程已失效、参数不匹配或场景未配置，请重新开始。");
    }
}
