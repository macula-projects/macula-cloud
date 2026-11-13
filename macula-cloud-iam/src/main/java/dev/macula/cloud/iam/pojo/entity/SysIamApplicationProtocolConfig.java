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
package dev.macula.cloud.iam.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.macula.boot.starter.mp.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 应用的唯一协议登记；秘密字段不参与普通JSON或日志投影。
 * @author Rain
 * @since 6.1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_iam_application_protocol_config")
public class SysIamApplicationProtocolConfig extends BaseEntity {
    private Long applicationId;
    private String protocol;
    private Integer schemaVersion;
    private Boolean enabled;
    private String protocolIdentifier;
    private LocalDateTime identifierIssuedAt;
    @JsonIgnore
    @ToString.Exclude
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String credentialHash;
    @JsonIgnore
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime credentialExpiresAt;
    private String configuration;
}
