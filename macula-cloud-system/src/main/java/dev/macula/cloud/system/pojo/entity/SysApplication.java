/*
 * Copyright (c) 2026 Macula
 * macula.dev, China
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
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import dev.macula.boot.starter.mp.entity.BaseEntity;
import lombok.Data;

/**
 * 第三方应用及其独立的归属租户、认证租户配置。
 *
 * @author Rain
 * @since 6.1.0
 */
@Data
@TableName("sys_application_tenant")
public class SysApplication extends BaseEntity {

    /** 归属租户由租户拦截器维护，不接受应用表单覆盖。 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Long tenantId;

    /** 认证租户为空时回退到归属租户，更新由 Service 显式控制。 */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Long authTenantId;

    private String applicationName;

    private String homepage;

    private String code;

    private String sk;

    private String manager;

    private String maintainer;

    private String mobile;

    private String accessPath;

    /** 每行一个来源 IP/CIDR，空白表示未单独限制。 */
    private String allowIps;

    private boolean useAttrs;

    private String allowedAttrs;
}
