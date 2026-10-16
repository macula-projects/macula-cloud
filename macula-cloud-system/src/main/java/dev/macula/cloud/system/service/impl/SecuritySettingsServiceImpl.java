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

import dev.macula.cloud.system.form.SecuritySettingsForm;
import dev.macula.cloud.system.mapper.SysSecuritySettingsMapper;
import dev.macula.cloud.system.pojo.entity.SysSecuritySettings;
import dev.macula.cloud.system.service.SecuritySettingsService;
import dev.macula.cloud.system.vo.SecuritySettingsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 固定主键的通用安全设置持久化；保存不改变当前认证行为。
 *
 * @author Rain
 * @since 2026-10-13
 */
@Service
@RequiredArgsConstructor
public class SecuritySettingsServiceImpl implements SecuritySettingsService {
    private final SysSecuritySettingsMapper mapper;
    private final JsonMapper json = JsonMapper.builder().build();

    @Override
    public SecuritySettingsVO get() {
        SysSecuritySettings entity = mapper.selectById(1L);
        SecuritySettingsForm form = entity == null ? new SecuritySettingsForm(1, 30, 0, 300, 15, 5, 30, "")
            : json.readValue(entity.getSettingsJson(), SecuritySettingsForm.class);
        return SecuritySettingsVO.from(form);
    }

    @Override
    @Transactional
    public SecuritySettingsVO save(SecuritySettingsForm form) {
        if (form.getContentSecurityPolicy() != null &&
            (form.getContentSecurityPolicy().contains("\n") || form.getContentSecurityPolicy().contains("\r"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "内容安全策略不可包含换行");
        }
        SysSecuritySettings entity = new SysSecuritySettings();
        entity.setId(1L);
        entity.setSettingsJson(json.writeValueAsString(form));
        if (mapper.selectById(1L) == null) mapper.insert(entity); else mapper.updateById(entity);
        return SecuritySettingsVO.from(form);
    }
}
