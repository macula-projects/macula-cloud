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

import com.baomidou.mybatisplus.annotation.TableName;
import dev.macula.boot.starter.mp.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 具体身份源的稳定身份键到本地不可变用户ID关系。
 *
 * @author Rain
 * @since 6.1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_registration_identity_binding")
public class SysRegistrationIdentityBinding extends BaseEntity {
    private Long registrationId;
    private String identityKind;
    private String identityValue;
    private String userType;
    private Long userId;
}
