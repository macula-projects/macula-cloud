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

import dev.macula.cloud.system.form.PasswordPolicyConfigForm;
import dev.macula.cloud.system.form.SecurityBasicConfigForm;
import dev.macula.cloud.system.form.SecurityDefensePolicyConfigForm;
import dev.macula.cloud.system.vo.PasswordPolicyConfigResult;
import dev.macula.cloud.system.vo.SecurityBasicConfigResult;
import dev.macula.cloud.system.vo.SecurityDefensePolicyConfigResult;
import dev.macula.cloud.system.vo.SecurityPolicyResult;

/**
 * 当前租户安全策略读取与分区保存服务。
 *
 * @author Rain
 * @since 2026-10-16
 */
public interface SecurityPolicyService {
    SecurityPolicyResult get();

    SecurityBasicConfigResult saveBasic(SecurityBasicConfigForm form);

    SecurityDefensePolicyConfigResult saveDefense(SecurityDefensePolicyConfigForm form);

    PasswordPolicyConfigResult savePassword(PasswordPolicyConfigForm form);
}
