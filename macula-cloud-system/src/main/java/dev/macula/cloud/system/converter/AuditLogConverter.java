/*
 * Copyright (c) 2022 Macula
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

package dev.macula.cloud.system.converter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.macula.cloud.system.pojo.bo.AuditLogBO;
import dev.macula.cloud.system.vo.log.AuditLogVO;
import dev.macula.boot.mapstruct.GlobalMapperConfig;
import org.mapstruct.Mapper;

/**
 * 转换数据库查询对象与接口对象，统一使用公共日期时区规则。
 * @author Rain
 * @since 6.1.0
 */
@Mapper(config = GlobalMapperConfig.class)
public interface AuditLogConverter {
    /** 将审计日志查询分页转换为Date接口分页。 */
    Page<AuditLogVO> bo2Vo(Page<AuditLogBO> bo);

}
