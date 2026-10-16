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

    @GetMapping("/providers")
    public List<MessageProviderVO> providers(@RequestParam String channel) {
        return service.providers(channel);
    }

    @GetMapping("/providers/{id}")
    public MessageProviderVO provider(@PathVariable Long id) {
        return service.provider(id);
    }

    @PostMapping("/providers")
    public MessageProviderVO createProvider(@Valid @RequestBody MessageProviderForm form) {
        return service.saveProvider(null, form);
    }

    @PutMapping("/providers/{id}")
    public MessageProviderVO updateProvider(@PathVariable Long id, @Valid @RequestBody MessageProviderForm form) {
        return service.saveProvider(id, form);
    }

    @DeleteMapping("/providers/{id}")
    public void deleteProvider(@PathVariable Long id) {
        service.deleteProvider(id);
    }

    @GetMapping("/templates")
    public List<MessageTemplateVO> templates(@RequestParam String channel,
        @RequestParam(required = false) String providerType) {
        return service.templates(channel, providerType);
    }

    @PutMapping("/templates/{channel}")
    public MessageTemplateVO saveTemplate(@PathVariable String channel,
        @Valid @RequestBody MessageTemplateForm form) {
        return service.saveTemplate(channel, form);
    }

    @PostMapping("/templates/{channel}/{key}/preview")
    public Map<String, String> preview(@PathVariable String channel, @PathVariable String key,
        @RequestParam(required = false) String providerType, @RequestBody Map<String, String> variables) {
        return Map.of("content", service.preview(channel, key, providerType, variables));
    }

    @PostMapping("/test")
    public Map<String, String> test(@Valid @RequestBody MessageTestForm form) {
        service.test(form);
        return Map.of("status", "submitted");
    }
}
