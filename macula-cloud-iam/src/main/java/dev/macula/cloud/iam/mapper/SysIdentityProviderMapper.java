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
import dev.macula.cloud.iam.pojo.entity.SysIdentityProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * IAM 身份提供商只读 Mapper。
 *
 * @author Rain
 * @since 6.1.0
 */
@Mapper
public interface SysIdentityProviderMapper extends BaseMapper<SysIdentityProvider> {
    /**
     * 绑定事务按主键锁定当前来源，返回经过现有Crypto插件处理的完整配置。
     * 通用Wrapper读取在当前插件组合下不能保证加密字段还原，不能以缓存或无锁读取替代。
     *
     * @param id 服务端已固定的来源ID
     * @return 当前锁定来源，不存在时为null
     */
    @Select("SELECT * FROM sys_identity_provider WHERE id = #{id} FOR UPDATE")
    SysIdentityProvider selectByIdForUpdate(@Param("id") Long id);
}
