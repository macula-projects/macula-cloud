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

import java.security.Principal;
import java.util.Objects;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * 演示与设备确认视图；只读取经框架绑定的授权，不在 Controller 中修改授权记录。
 * @author Rain
 * @since 6.1.0
 */
@Controller
public class PlaygroundPageController {
    private final PlaygroundFlowService flows;
    private final OAuth2AuthorizationService authorizations;
    public PlaygroundPageController(PlaygroundFlowService flows, OAuth2AuthorizationService authorizations) {
        this.flows = flows;
        this.authorizations = authorizations;
    }

    @GetMapping({"/playground", "/playground/"})
    public String index() { return "playground/index"; }

    @GetMapping("/playground/callback")
    public String callback() { return "playground/callback"; }

    @GetMapping("/playground/device")
    public String device(Model model, Principal principal,
        @RequestParam(name = "user_code", required = false) String userCode,
        @RequestParam(required = false) String state,
        @RequestParam(required = false) String result) {
        String normalized = PlaygroundConfiguration.DeviceVerificationFilter.normalizeUserCode(userCode);
        model.addAttribute("userCode", normalized == null ? "" : normalized);
        model.addAttribute("result", java.util.Set.of("approved", "denied", "invalid").contains(
            result == null ? "" : result) ? result : "");
        model.addAttribute("verificationEndpoint", flows.configuration().get("issuer") + "/oauth2/device_verification");
        if (state != null) {
            var authorization = normalized == null ? null : authorizations.findByToken(normalized, new OAuth2TokenType("user_code"));
            if (authorization == null || !PlaygroundRegisteredClientRepository.DEVICE.equals(authorization.getRegisteredClientId())
                || principal == null || !principal.getName().equals(authorization.getPrincipalName())
                || !Objects.equals(state, authorization.getAttribute("state"))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Device confirmation is invalid or expired");
            }
            model.addAttribute("state", state);
            model.addAttribute("clientId", PlaygroundRegisteredClientRepository.DEVICE);
        }
        return "playground/device";
    }
}
