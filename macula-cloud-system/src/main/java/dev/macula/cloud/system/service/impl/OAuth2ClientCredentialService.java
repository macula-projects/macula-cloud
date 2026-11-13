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

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import dev.macula.cloud.system.mapper.SysIamApplicationProtocolConfigMapper;
import dev.macula.cloud.system.pojo.entity.SysIamApplication;
import dev.macula.cloud.system.pojo.entity.SysIamApplicationProtocolConfig;
import dev.macula.cloud.system.protocol.IamApplicationProtocolRegistration;
import dev.macula.cloud.system.protocol.OAuth2ApplicationProtocolSettings;
import dev.macula.cloud.system.protocol.OAuth2ProtocolDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * OAuth客户端Secret的显式创建/旋转；不能把未来其他协议凭据当作共享Secret。
 * @author Rain
 * @since 6.1.0
 */
@Service
@RequiredArgsConstructor
public class OAuth2ClientCredentialService {
    private final PasswordEncoder encoder;
    private final SysIamApplicationProtocolConfigMapper mapper;

    /** 按既有操作规则保留原哈希；已有Secret只能由旋转入口更新。 */
    public String prepare(SysIamApplication app, IamApplicationProtocolRegistration<?> registration,
        SysIamApplicationProtocolConfig existing, String supplied) {
        String original = existing == null ? null : existing.getCredentialHash();
        if (registration == null) {
            if (StringUtils.hasText(supplied)) {
                throw bad("未选择协议不能设置客户端密钥");
            }
            return original;
        }
        boolean required = requiresSecret(registration);
        if (StringUtils.hasText(supplied)) {
            if (!required || StringUtils.hasText(original)) {
                throw bad("请通过密钥重置入口更新凭据");
            }
            original = encode(supplied);
        }
        if (!required) {
            return null;
        }
        if (("ENABLED".equals(app.getConfigurationStatus()) || "RESOURCE_SERVER".equals(app.getPurpose()))
            && !StringUtils.hasText(original)) {
            throw bad("机密协议必须设置应用密钥");
        }
        return original;
    }

    /** 旋转只更新已有OAuth登记的哈希列，不覆盖协议、有效期或来源。 */
    public void rotate(IamApplicationProtocolRegistration<?> registration, SysIamApplicationProtocolConfig row, String raw) {
        if (registration == null || !requiresSecret(registration)) {
            throw bad("当前协议不使用客户端密钥");
        }
        int changed = mapper.update(null, new LambdaUpdateWrapper<SysIamApplicationProtocolConfig>()
            .eq(SysIamApplicationProtocolConfig::getId, row.getId())
            .eq(SysIamApplicationProtocolConfig::getSchemaVersion, row.getSchemaVersion())
            .set(SysIamApplicationProtocolConfig::getCredentialHash, encode(raw)));
        if (changed != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "客户端认证配置已变化，请刷新后重试");
        }
    }

    /** 由具体OAuth策略判断，不将其他协议的证书或签名材料视为Secret。 */
    private boolean requiresSecret(IamApplicationProtocolRegistration<?> registration) {
        if (!(registration.settings() instanceof OAuth2ApplicationProtocolSettings settings)) {
            throw bad("当前协议不使用OAuth客户端密钥");
        }
        return new OAuth2ProtocolDefinition(registration.protocol()).requiresSecret(settings);
    }

    /** 延续最小输入长度，仅对明确提交的新Secret编码一次。 */
    private String encode(String raw) {
        if (!StringUtils.hasText(raw) || raw.length() < 16) {
            throw bad("密钥至少16个字符");
        }
        return encoder.encode(raw);
    }

    /** 输入错误不携带秘密或底层编码异常。 */
    private ResponseStatusException bad(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
