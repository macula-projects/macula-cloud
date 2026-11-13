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
package dev.macula.cloud.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.macula.cloud.system.pojo.entity.SysIamApplicationProtocolConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 应用的唯一协议登记；秘密字段不参与普通JSON或日志投影。
 * @author Rain
 * @since 6.1.0
 */
@Mapper
public interface SysIamApplicationProtocolConfigMapper extends BaseMapper<SysIamApplicationProtocolConfig> {
    /** 依赖application_id唯一约束读取唯一登记，不接受任意默认协议。 */
    @Select("SELECT * FROM sys_iam_application_protocol_config WHERE application_id=#{applicationId}")
    SysIamApplicationProtocolConfig selectByApplicationId(@Param("applicationId") Long applicationId);

    /** 在持有主应用锁的同一事务中锁定登记，避免RR快照读到旧配置。 */
    @Select("SELECT * FROM sys_iam_application_protocol_config WHERE application_id=#{applicationId} FOR UPDATE")
    SysIamApplicationProtocolConfig selectByApplicationIdForUpdate(@Param("applicationId") Long applicationId);
}
