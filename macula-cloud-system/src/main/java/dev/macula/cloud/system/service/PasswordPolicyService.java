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

import dev.macula.cloud.system.form.PasswordPolicyForm;
import dev.macula.cloud.system.vo.PasswordPolicyVO;

/**
 * 全局密码策略读写服务。
 *
 * @author Rain
 * @since 2026-10-13
 */
public interface PasswordPolicyService {
    PasswordPolicyVO get();
    PasswordPolicyVO save(PasswordPolicyForm form);
}
