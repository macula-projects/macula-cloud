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
package dev.macula.cloud.iam.service.identity;

import java.util.List;

import dev.macula.cloud.iam.authentication.thirdparty.VerifiedExternalIdentity;
import dev.macula.cloud.iam.mapper.SysUserMapper;
import dev.macula.cloud.iam.pojo.entity.SysUser;
import dev.macula.cloud.system.enums.IdentityBindingMapping;
import dev.macula.cloud.system.enums.IdentityBindingSourceField;
import dev.macula.cloud.system.security.UserIdentifierNormalizer;

/**
 * 首次与事务内重查共用的字段规范化匹配，不参与跨字段登录兜底或用户资料回写。
 *
 * @author Rain
 * @since 6.1.0
 */
public final class IdentityBindingMatcher {
    private static final String TRIM_PATTERN = "^[\\x{0000}-\\x{0020}]+|[\\x{0000}-\\x{0020}]+$";
    private static final String MOBILE_PATTERN = "[-\\x{0009}-\\x{000D}\\x{0020}]";
    private final SysUserMapper users;
    private final UserIdentifierNormalizer normalizer = new UserIdentifierNormalizer();

    /**
     * 复用现有Mapper，不增加与账号目录不同的查询数据源。
     */
    public IdentityBindingMatcher(SysUserMapper users) {
        this.users = users;
    }

    /**
     * 按选定规则唯一查询；无属性或类型不合法不查询，手机号证明维持专用归属约束。
     *
     * @param mapping 当前来源受控映射
     * @param identity 服务端核验身份和属性
     * @param forUpdate 仅绑定事务内为true
     * @return 至多两个候选；调用方仍须核对Client和账号准入
     */
    public List<SysUser> candidates(IdentityBindingMapping mapping, VerifiedExternalIdentity identity,
            boolean forUpdate) {
        if (mapping == null) {
            return List.of();
        }
        if (VerifiedExternalIdentity.MOBILE_PROOF.equals(identity.identityKind())
                && (mapping.source() != IdentityBindingSourceField.MOBILE
                || mapping.target() != dev.macula.cloud.system.enums.IdentityBindingTargetField.MOBILE)) {
            return List.of();
        }
        String value = normalized(mapping, mapping.attribute(identity.attributes()));
        if (value == null) {
            return List.of();
        }
        if (forUpdate) {
            // 规范化表达式没有唯一索引，必须在有范围锁的真实绑定事务内重查。
            String isolation = users.identityBindingIsolation();
            if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                    || !("REPEATABLE-READ".equals(isolation) || "SERIALIZABLE".equals(isolation))) {
                throw new org.springframework.security.authentication.BadCredentialsException("账号绑定条件暂不可用");
            }
        }
        return users.findIdentityBindingCandidates(mapping.target().value(), value, TRIM_PATTERN, MOBILE_PATTERN,
                forUpdate);
    }

    /**
     * 全部命中必须指向同一账号；缺失与无命中不贡献候选，任一歧义都停止自动绑定。
     *
     * @param mappings 完整且受控的来源映射集合
     * @param identity 服务端核验后的身份属性
     * @param forUpdate 绑定事务内必须为true
     * @return 唯一一致候选，冲突或无命中返回空集合
     */
    public List<SysUser> candidates(List<IdentityBindingMapping> mappings, VerifiedExternalIdentity identity,
            boolean forUpdate) {
        SysUser candidate = null;
        for (var mapping : dev.macula.cloud.system.enums.IdentityBindingMappings.ordered(mappings)) {
            List<SysUser> matches = candidates(mapping, identity, forUpdate);
            if (matches.size() > 1) {
                return List.of();
            }
            if (!matches.isEmpty()) {
                SysUser current = matches.get(0);
                if (candidate != null && !java.util.Objects.equals(candidate.getId(), current.getId())) {
                    return List.of();
                }
                candidate = current;
            }
        }
        return candidate == null ? List.of() : List.of(candidate);
    }

    /**
     * 源属性先按证明类型验证，再按目标语义比较；不对邮箱、手机号猜测缺失信息。
     */
    private String normalized(IdentityBindingMapping mapping, String value) {
        String source = switch (mapping.source()) {
            case MEMBER_ID, EMPLOYEE_NO -> normalizer.username(value).orElse(null);
            case MOBILE -> normalizer.mobile(value).orElse(null);
            case EMAIL -> normalizer.email(value).orElse(null);
        };
        if (source == null) {
            return null;
        }
        // SQL与Java对非ASCII邮箱的大小写折叠可能不同，未证明一致前不自动绑定。
        if (mapping.source() == IdentityBindingSourceField.EMAIL && source.chars().anyMatch(ch -> ch > 127)) {
            return null;
        }
        return switch (mapping.target()) {
            case USERNAME -> normalizer.username(source).orElse(null);
            case MOBILE -> normalizer.mobile(source).orElse(null);
            case EMAIL -> normalizer.email(source).orElse(null);
        };
    }
}
