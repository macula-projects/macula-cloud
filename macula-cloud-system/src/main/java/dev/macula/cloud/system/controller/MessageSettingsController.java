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
package dev.macula.cloud.system.controller;

import dev.macula.cloud.system.form.MessageProviderForm;
import dev.macula.cloud.system.form.MessagePreviewForm;
import dev.macula.cloud.system.form.MessageTemplateForm;
import dev.macula.cloud.system.form.MessageTestForm;
import dev.macula.cloud.system.service.MessageSettingsService;
import dev.macula.cloud.system.vo.MessageProviderVO;
import dev.macula.cloud.system.vo.MessageTemplateVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 消息服务、模板和测试发送 API，访问权限由平台 URL 权限配置控制。
 *
 * @author Rain
 * @since 2026-10-13
 */
@RestController
@RequestMapping("/api/v1/message-settings")
@RequiredArgsConstructor
public class MessageSettingsController {
    private final MessageSettingsService service;

    @GetMapping("/providers/{channel}")
    public MessageProviderVO provider(@PathVariable String channel) {
        return service.provider(channel);
    }

    @PutMapping("/providers/{channel}")
    public MessageProviderVO saveProvider(@PathVariable String channel,
        @Valid @RequestBody MessageProviderForm form) {
        return service.saveProvider(channel, form);
    }

    @GetMapping("/templates/{channel}")
    public List<MessageTemplateVO> templates(@PathVariable String channel,
        @RequestParam(required = false) String providerType,
        @RequestParam(required = false, defaultValue = "zh-CN") String languageCode) {
        return service.templates(channel, providerType, languageCode);
    }

    @PutMapping("/templates/{channel}/{scene}")
    public MessageTemplateVO saveTemplate(@PathVariable String channel, @PathVariable String scene,
        @Valid @RequestBody MessageTemplateForm form) {
        return service.saveTemplate(channel, scene, form);
    }

    @PostMapping("/templates/MAIL/{scene}/preview")
    public Map<String, String> preview(@PathVariable String scene,
        @RequestBody(required = false) MessagePreviewForm form) {
        return Map.of("content", service.preview(scene, form == null ? null : form.getVariables()));
    }

    @PostMapping("/test")
    public Map<String, String> test(@Valid @RequestBody MessageTestForm form) {
        service.test(form);
        return Map.of("status", "submitted");
    }
}
