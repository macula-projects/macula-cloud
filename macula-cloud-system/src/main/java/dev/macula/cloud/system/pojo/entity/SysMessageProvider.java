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
package dev.macula.cloud.system.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import dev.macula.boot.starter.crypto.annotation.CryptoField;
import lombok.Data;

/**
 * 平台级邮件或短信供应商配置。
 *
 * @author Rain
 * @since 2026-10-13
 */
@Data
@TableName("sys_message_provider")
public class SysMessageProvider {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String channel;
    private String providerType;
    private String name;
    private String endpoint;
    private String account;
    @CryptoField
    private String credential;
    private String regionCode;
    private String sender;
    private String configJson;
    private Boolean enabled;
}
