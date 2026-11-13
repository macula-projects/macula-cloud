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

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import dev.macula.boot.starter.mp.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 应用公共元数据与登录策略；不映射协议字段、凭据或来源JSON。
 * @author Rain
 * @since 6.1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_iam_application")
public class SysIamApplication extends BaseEntity {
    private String applicationCode;
    private LocalDateTime applicationCodeIssuedAt;
    private String applicationName;
    private String purpose;
    private String managedKey;
    private Long authorizationVersion;
    private String resourcePermissions;
    private String applicationType;
    private String webLoginMode;
    private String miniappPlatform;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String hostedLoginMethods;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String directLoginCapability;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String userType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long tenantId;
    private String configurationStatus;
}
