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
package dev.macula.cloud.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.macula.cloud.iam.pojo.entity.SysIamApplicationIdentityProvider;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 应用的明确登录能力到具体身份源的唯一关联；不保存身份源凭据。
 * @author Rain
 * @since 6.1.0
 */
@Mapper
public interface SysIamApplicationIdentityProviderMapper extends BaseMapper<SysIamApplicationIdentityProvider> {
    /** 登录能力排序稳定，构造一致的应用快照。 */
    @Select("SELECT * FROM sys_iam_application_identity_provider WHERE application_id=#{applicationId} ORDER BY login_capability")
    List<SysIamApplicationIdentityProvider> selectByApplicationId(@Param("applicationId") Long applicationId);

    /** 主应用锁内使用当前读，不能再混入普通RR快照。 */
    @Select("SELECT * FROM sys_iam_application_identity_provider WHERE application_id=#{applicationId} ORDER BY login_capability FOR UPDATE")
    List<SysIamApplicationIdentityProvider> selectByApplicationIdForUpdate(@Param("applicationId") Long applicationId);
}
