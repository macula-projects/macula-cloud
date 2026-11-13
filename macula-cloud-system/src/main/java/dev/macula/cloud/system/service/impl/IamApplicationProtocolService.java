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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.macula.boot.mapstruct.DateTimeConverter;
import dev.macula.cloud.system.mapper.SysIamApplicationIdentityProviderMapper;
import dev.macula.cloud.system.mapper.SysIamApplicationProtocolConfigMapper;
import dev.macula.cloud.system.pojo.entity.SysIamApplication;
import dev.macula.cloud.system.pojo.entity.SysIamApplicationIdentityProvider;
import dev.macula.cloud.system.pojo.entity.SysIamApplicationProtocolConfig;
import dev.macula.cloud.system.protocol.IamApplicationProtocolRegistration;
import dev.macula.cloud.system.protocol.IamApplicationProtocolRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 单协议持久化及聚合版本；协议策略通过端口分派，调用者必须持有主应用锁。
 * @author Rain
 * @since 6.1.0
 */
@Service
@RequiredArgsConstructor
public class IamApplicationProtocolService {
    private final SysIamApplicationProtocolConfigMapper mapper;
    private final SysIamApplicationIdentityProviderMapper sources;
    private final IamApplicationProtocolRegistry registry;
    private final DateTimeConverter dates;
    private final JsonMapper json = JsonMapper.builder().build();

    /** 根据事务用途读取快照或锁定当前读，不能在一次操作中混用。 */
    public Stored state(Long applicationId, boolean lock) {
        if (applicationId == null) {
            return new Stored(null, List.of());
        }
        return new Stored(lock ? mapper.selectByApplicationIdForUpdate(applicationId) : mapper.selectByApplicationId(applicationId),
            lock ? sources.selectByApplicationIdForUpdate(applicationId) : sources.selectByApplicationId(applicationId));
    }

    /** 非秘密运行视图；历史未分配槽不解析成协议设置。 */
    public IamApplicationProtocolRegistration<?> registration(SysIamApplication app, Stored stored) {
        var row = stored.protocol();
        return row == null ? null : registry.read(row.getProtocol(), row.getSchemaVersion(), Boolean.TRUE.equals(row.getEnabled()),
            row.getProtocolIdentifier(), dates.toInstant(row.getIdentifierIssuedAt()), row.getConfiguration(), app.getApplicationType(), app.getPurpose());
    }

    /** 对完整聚合计算版本；秘密仅进入有长度边界的摘要输入，不进入JSON视图或日志。 */
    public String version(SysIamApplication app, Stored stored) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, json.writeValueAsString(app));
            update(digest, json.writeValueAsString(stored.protocol()));
            update(digest, stored.protocol() == null ? null : stored.protocol().getCredentialHash());
            update(digest, stored.protocol() == null || stored.protocol().getCredentialExpiresAt() == null
                ? null : stored.protocol().getCredentialExpiresAt().toString());
            update(digest, json.writeValueAsString(stored.sources()));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("配置版本不可用");
        }
    }

    /** 读调用者的同一RR事务内计算当前聚合版本。 */
    public String version(SysIamApplication app) {
        return version(app, state(app.getId(), false));
    }

    /** 主行锁内比较完整配置；凭据及来源漂移同样返回409。 */
    public void checkVersion(SysIamApplication app, Stored stored, String expected) {
        if (expected == null || !expected.equals(version(app, stored))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "应用或协议已变化，请刷新后重试");
        }
    }

    /** 保留登记主键；选择新协议才替换旧槽，不因编辑无协议草稿丢失历史凭据。 */
    public void save(SysIamApplication app, Stored previous, IamApplicationProtocolRegistration<?> registration, String credential) {
        if (registration == null) {
            if (previous.protocol() != null && previous.protocol().getSchemaVersion() != 0) {
                mapper.deleteById(previous.protocol().getId());
            }
            return;
        }
        String configuration = registry.serialize(registration, app.getApplicationType(), app.getPurpose());
        var row = new SysIamApplicationProtocolConfig();
        if (previous.protocol() != null) {
            BeanUtils.copyProperties(previous.protocol(), row);
        }
        row.setApplicationId(app.getId());
        row.setProtocol(registration.protocol());
        row.setSchemaVersion(registration.schemaVersion());
        row.setEnabled(registration.enabled());
        row.setProtocolIdentifier(registration.protocolIdentifier());
        row.setIdentifierIssuedAt(dates.instantToLocalDateTime(registration.identifierIssuedAt()));
        row.setCredentialHash(credential);
        row.setConfiguration(configuration);
        if (row.getId() == null) {
            mapper.insert(row);
        } else if (mapper.updateById(row) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "协议配置已变化");
        }
    }

    /** 删除聚合时移除唯一登记，不能保留孤儿凭据。 */
    public void delete(Long applicationId) {
        mapper.delete(new LambdaQueryWrapper<SysIamApplicationProtocolConfig>().eq(SysIamApplicationProtocolConfig::getApplicationId, applicationId));
    }

    /** 显式长度和null标记避免不同字段组合产生相同摘要输入。 */
    private void update(MessageDigest digest, String value) {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value == null ? -1 : bytes.length).array());
        digest.update(bytes);
    }

    /**
     * 仅在System事务内部传递的持久化状态，不作为Controller响应。
     * @param protocol 唯一登记或null
     * @param sources 排序稳定的来源关系
     * @since 6.1.0
     */
    public record Stored(SysIamApplicationProtocolConfig protocol, List<SysIamApplicationIdentityProvider> sources) {
        /** 防止调用方修改版本计算使用的关系集合。 */
        public Stored {
            sources = List.copyOf(sources);
        }
    }
}
