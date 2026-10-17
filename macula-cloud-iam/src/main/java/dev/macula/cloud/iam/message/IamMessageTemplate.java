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
package dev.macula.cloud.iam.message;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 共享 System 表的 IAM 只读登录短信模板映射。
 *
 * @author Rain
 * @since 2026-10-17
 */
@Data
@TableName("sys_message_template")
public class IamMessageTemplate {
    @TableId
    private Long id;
    private String channel;
    private String templateKey;
    private String providerType;
    private String languageCode;
    private String providerTemplateCode;
    private Boolean enabled;
}
