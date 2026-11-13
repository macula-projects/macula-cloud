/*
 * Copyright (c) 2023 Macula
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

import java.util.List;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.macula.cloud.iam.pojo.dto.UserAuthInfo;
import dev.macula.cloud.iam.pojo.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户持久层
 *
 * @author haoxr
 * @since 2022/1/14
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 根据用户名获取认证信息
     *
     * @param username 用户名
     * @return 用户认证信息
     */
    @InterceptorIgnore(tenantLine = "true")
    UserAuthInfo getUserAuthInfo(Long tenantId, String username);

    /**
     * 返回当前绑定连接的实际隔离级别，避免READ COMMITTED缺少范围锁而漏掉并发新候选。
     */
    @Select("SELECT @@transaction_isolation")
    String identityBindingIsolation();

    /**
     * 按固定目标字段查询规范化候选；最多两条用于证明唯一，绑定事务使用同一条件加锁。
     *
     * @param target 仅来自受控枚举的目标标识
     * @param value 已按目标语义规范化的值
     * @param trimPattern 与Java trim相同的ASCII边界
     * @param mobilePattern 与Java手机号分隔符相同的ASCII集合
     * @param forUpdate 是否在现有绑定事务内锁定候选
     * @return 至多两个候选，不承诺账号或Client准入
     */
    @InterceptorIgnore(tenantLine = "true")
    List<SysUser> findIdentityBindingCandidates(@Param("target") String target,
            @Param("value") String value,
            @Param("trimPattern") String trimPattern,
            @Param("mobilePattern") String mobilePattern,
            @Param("forUpdate") boolean forUpdate);

}
