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
package dev.macula.cloud.iam.authentication.thirdparty.directory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 成员准入与资料查询结果分离；未知业务拒绝不能伪装成可忽略的网络故障。
 *
 * @author Rain
 * @since 6.1.0
 */
public record ProviderDirectoryResult(Status status, String organizationId, Map<String, String> profile,
                                      Instant observedAt, boolean tolerableProfileFailure) {
    private static final Set<String> FIELDS = Set.of("member_id", "employee_no", "job_number", "mobile", "email");

    /**
     * 白名单资料只用于独立快照，不覆盖本地账号或角色。
     */
    public ProviderDirectoryResult {
        Objects.requireNonNull(status);
        Objects.requireNonNull(observedAt);
        if (!FIELDS.containsAll(profile.keySet())) {
            throw new IllegalArgumentException("目录资料字段无效");
        }
        profile = Map.copyOf(profile);
    }

    /**
     * 仅网络/服务故障或已确认权限错误可作为可选资料缺失。
     */
    public static ProviderDirectoryResult unavailable(boolean tolerable) {
        return new ProviderDirectoryResult(Status.UNAVAILABLE, null, Map.of(), Instant.now(), tolerable);
    }

    /**
     * 默认日志不输出手机号、成员资料或组织。
     */
    @Override
    public String toString() {
        return "ProviderDirectoryResult[status=" + status + "]";
    }

    /**
     * 统一的已确认成员、非成员、禁用及资料权限状态。
     *
     * @since 6.1.0
     */
    public enum Status {MEMBER, NOT_MEMBER, DISABLED, PERMISSION_DENIED, UNAVAILABLE}
}
