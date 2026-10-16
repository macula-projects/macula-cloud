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
package dev.macula.cloud.system.service.impl;

import dev.macula.cloud.system.form.PasswordPolicyForm;
import dev.macula.cloud.system.mapper.SysPasswordPolicyMapper;
import dev.macula.cloud.system.pojo.entity.SysPasswordPolicy;
import dev.macula.cloud.system.service.PasswordPolicyService;
import dev.macula.cloud.system.vo.PasswordPolicyVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * 固定主键的密码策略持久化；保存不改变当前密码校验流程。
 *
 * @author Rain
 * @since 2026-10-13
 */
@Service
@RequiredArgsConstructor
public class PasswordPolicyServiceImpl implements PasswordPolicyService {
    private final SysPasswordPolicyMapper mapper;
    private final JsonMapper json = JsonMapper.builder().build();

    @Override
    public PasswordPolicyVO get() {
        SysPasswordPolicy entity = mapper.selectById(1L);
        PasswordPolicyForm form = entity == null ? new PasswordPolicyForm(8, 128, 2, 0, 0, 3, true, 0,
            List.of(), true, List.of()) : json.readValue(entity.getSettingsJson(), PasswordPolicyForm.class);
        return PasswordPolicyVO.from(form);
    }

    @Override
    @Transactional
    public PasswordPolicyVO save(PasswordPolicyForm form) {
        if (form.getMinLength() > form.getMaxLength() ||
            (form.getExpiryDays() == 0 && form.getReminderDays() != 0) ||
            (form.getExpiryDays() > 0 && form.getReminderDays() >= form.getExpiryDays()) ||
            (form.getIllegalSequences() != null && form.getIllegalSequences().size() > 100) ||
            (form.getCustomWeakPasswords() != null && form.getCustomWeakPasswords().size() > 1000)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码策略参数组合无效");
        }
        SysPasswordPolicy entity = new SysPasswordPolicy();
        entity.setId(1L);
        entity.setSettingsJson(json.writeValueAsString(form));
        if (mapper.selectById(1L) == null) mapper.insert(entity); else mapper.updateById(entity);
        return PasswordPolicyVO.from(form);
    }
}
