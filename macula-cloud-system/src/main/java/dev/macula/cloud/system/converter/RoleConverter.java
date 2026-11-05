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

package dev.macula.cloud.system.converter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.macula.boot.result.Option;
import dev.macula.cloud.system.form.RoleForm;
import dev.macula.cloud.system.pojo.entity.SysRole;
import dev.macula.cloud.system.vo.role.RolePageVO;
import dev.macula.cloud.system.vo.role.RoleDetailVO;
import dev.macula.boot.mapstruct.GlobalMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import java.util.List;

/**
 * 角色对象转换器
 *
 * @author haoxr
 * @since 2022/5/29
 */
@Mapper(config = GlobalMapperConfig.class)
public interface RoleConverter {
    /** 将角色实体分页转换为接口分页，并按公共时区转换审计时间。 */
    Page<RolePageVO> entity2Page(Page<SysRole> page);

    /** 将角色名称和主键映射为下拉选项。 */
    @Mappings({@Mapping(target = "value", source = "id"), @Mapping(target = "label", source = "name")})
    Option<Long> role2Option(SysRole role);
    /** 批量生成角色下拉选项。 */
    List<Option<Long>> roles2Options(List<SysRole> roles);
    /** 将角色可维护字段映射为数据库实体。 */
    SysRole form2Entity(RoleForm roleForm);
    /** 将角色实体转换为含明确时间点的详情响应。 */
    RoleDetailVO entity2Detail(SysRole entity);
}
