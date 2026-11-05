/*
 * Copyright (c) 2026 Macula
 * macula.dev, China
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

import dev.macula.boot.mapstruct.GlobalMapperConfig;
import dev.macula.cloud.system.form.PermissionForm;
import dev.macula.cloud.system.pojo.entity.SysPermission;
import dev.macula.cloud.system.vo.perm.PermissionVO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 隔离权限接口与数据库实体，审计字段只能由服务端维护。
 * @author Rain
 * @since 6.1.0
 */
@Mapper(config = GlobalMapperConfig.class)
public interface PermissionConverter {
    /** 将权限实体转换为接口详情，按公共时区解释审计时间。 */
    PermissionVO entity2VO(SysPermission entity);

    /** 转换权限列表，保留每项原有业务字段。 */
    List<PermissionVO> entities2VOs(List<SysPermission> entities);

    /** 写入只映射表单业务字段，禁止客户端覆盖审计字段。 */
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "lastUpdateTime", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "lastUpdateBy", ignore = true)
    SysPermission form2Entity(PermissionForm form);
}
