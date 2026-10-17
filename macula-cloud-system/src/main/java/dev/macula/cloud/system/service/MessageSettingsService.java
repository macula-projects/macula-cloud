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
package dev.macula.cloud.system.service;

import dev.macula.cloud.system.form.MessageProviderForm;
import dev.macula.cloud.system.form.MessageTemplateForm;
import dev.macula.cloud.system.form.MessageTestForm;
import dev.macula.cloud.system.vo.MessageProviderVO;
import dev.macula.cloud.system.vo.MessageTemplateVO;

import java.util.List;
import java.util.Map;

/**
 * 全局消息服务及模板配置与测试发送服务。
 *
 * @author Rain
 * @since 2026-10-13
 */
public interface MessageSettingsService {
    MessageProviderVO provider(String channel);
    MessageProviderVO saveProvider(String channel, MessageProviderForm form);
    List<MessageTemplateVO> templates(String channel, String providerType, String languageCode);
    MessageTemplateVO saveTemplate(String channel, String scene, MessageTemplateForm form);
    String preview(String scene, Map<String, String> variables);
    void test(MessageTestForm form);
}
