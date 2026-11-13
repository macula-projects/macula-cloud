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
import dev.macula.cloud.iam.pojo.entity.SysIamApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 应用公共元数据与登录策略；不映射协议字段、凭据或来源JSON。
 * @author Rain
 * @since 6.1.0
 */
@Mapper
public interface SysIamApplicationMapper extends BaseMapper<SysIamApplication> {
    /** 主应用行是协议、凭据及来源更新共同的事务锁。 */
    @Select("SELECT * FROM sys_iam_application WHERE id=#{id} FOR UPDATE")
    SysIamApplication selectByIdForUpdate(@Param("id") Long id);
}
