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
package dev.macula.cloud.tinyid.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.macula.cloud.tinyid.pojo.entity.TinyIdToken;
import dev.macula.cloud.tinyid.pojo.bo.TinyIdApplicationBO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * TinyID 应用授权 MyBatis-Plus Mapper。
 *
 * @author Rain
 * @since 6.1.0
 */
@Mapper
public interface TinyIdTokenMapper extends BaseMapper<TinyIdToken> {

    /**
     * 分页查询按 Token 聚合的接入应用。
     *
     * @param page 分页参数
     * @param keywords 应用备注关键字
     * @return 接入应用分页数据
     */
    Page<TinyIdApplicationBO> selectApplicationPage(Page<TinyIdApplicationBO> page,
        @Param("keywords") String keywords);

    /**
     * 按任意授权行主键查询对应接入应用。
     *
     * @param appId 授权行主键
     * @return 接入应用，不存在时返回 {@code null}
     */
    TinyIdApplicationBO selectApplicationById(@Param("appId") long appId);

    /**
     * 按 Token 查询接入应用。
     *
     * @param token 应用接入 Token
     * @return 接入应用，不存在时返回 {@code null}
     */
    TinyIdApplicationBO selectApplicationByToken(@Param("token") String token);
}
