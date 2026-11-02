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
 * 平台级第三方身份提供商持久化配置。
 *
 * @author Rain
 * @since 2026-10-13
 */
@Data
@TableName("sys_identity_provider")
public class SysIdentityProvider {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String remark;
    private String providerType;
    private String appId;
    @CryptoField
    private String appSecret;
    private String configJson;
    @CryptoField
    private String secretConfigJson;
    private Boolean enabled;
    private Boolean visible;
}
