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
package dev.macula.cloud.system.message.mail;

import dev.macula.cloud.system.pojo.entity.SysMessageProvider;

/**
 * 管理端邮件投递端口，仅使用服务端已保存的配置。
 * @author Rain
 * @since 6.1.0
 */
public interface MailSender {
    /** 按已选定模板提交邮件，失败时抛出安全异常。 */
    void send(SysMessageProvider provider, String target, String senderName, String subject, String content);
}
