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
import dev.macula.cloud.tinyid.pojo.entity.TinyIdInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * TinyID 发号业务 MyBatis-Plus Mapper。
 *
 * @author Rain
 * @since 6.1.0
 */
@Mapper
public interface TinyIdInfoMapper extends BaseMapper<TinyIdInfo> {

    /**
     * 使用最大 ID 和版本号双重条件更新号段。
     *
     * @param id 业务主键
     * @param newMaxId 新最大 ID
     * @param oldMaxId 原最大 ID
     * @param version 原版本号
     * @param bizType 业务类型
     * @return 更新记录数
     */
    int updateMaxId(@Param("id") Long id, @Param("newMaxId") Long newMaxId,
        @Param("oldMaxId") Long oldMaxId, @Param("version") Long version,
        @Param("bizType") String bizType);
}
