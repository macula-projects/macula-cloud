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
package dev.macula.cloud.system.converter;

import dev.macula.cloud.system.form.IdentityProviderForm;
import dev.macula.cloud.system.pojo.entity.SysIdentityProvider;
import dev.macula.cloud.system.vo.IdentityProviderVO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.Map;

/**
 * 身份提供商模型转换；响应只映射通过校验的公开配置，不回显持久化凭据。
 *
 * @author Rain
 * @since 2026-10-15
 */
@Mapper
public interface IdentityProviderConverter {

    @Mapping(target = "category", source = "category")
    @Mapping(target = "callbackUrl", source = "callbackUrl")
    @Mapping(target = "config", source = "config")
    @Mapping(target = "configuredSecrets", source = "configuredSecrets")
    IdentityProviderVO toVO(SysIdentityProvider entity, String category, String callbackUrl, Map<String, String> config,
        Map<String, Boolean> configuredSecrets);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "name", source = "form.name")
    @Mapping(target = "remark", source = "form.remark")
    @Mapping(target = "providerType", source = "form.providerType")
    void updateEntity(IdentityProviderForm form, @MappingTarget SysIdentityProvider entity);
}
