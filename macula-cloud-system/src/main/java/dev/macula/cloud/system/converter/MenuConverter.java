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

import dev.macula.cloud.system.dto.MenuDTO;
import dev.macula.cloud.system.pojo.bo.MenuBO;
import dev.macula.cloud.system.pojo.entity.SysMenu;
import dev.macula.cloud.system.vo.menu.MenuVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

/**
 * 菜单对象转换器
 *
 * @author haoxr
 * @since 2022/7/29
 */
@Mapper(componentModel = "spring")
public interface MenuConverter {

    MenuVO entity2VO(SysMenu entity);

    @Mappings({
            @Mapping(source = "meta.title", target = "name"),
            @Mapping(source = "meta.type", target = "type"),
            @Mapping(source = "meta.icon", target = "icon"),
            @Mapping(source = "meta.tag", target = "tag"),
            @Mapping(target = "visible", expression = "java(toInt(dto.getMeta().getVisible()))"),
            @Mapping(target = "fullPage", expression = "java(toInt(dto.getMeta().getFullpage()))")
    })
    SysMenu MenuDTO2Entity(MenuDTO dto);

    @Mappings({
            @Mapping(source = "name", target = "meta.title"),
            @Mapping(source = "type", target = "meta.type"),
            @Mapping(source = "icon", target = "meta.icon"),
            @Mapping(source = "tag", target = "meta.tag"),
            @Mapping(target = "meta.visible", expression = "java(toBool(sysMenu.getVisible()))"),
            @Mapping(target = "meta.fullpage", expression = "java(toBool(sysMenu.getFullPage()))")
    })
    MenuBO entity2BO(SysMenu sysMenu);

    default Integer toInt(Boolean bool) {
        return bool == null ? 0 : (bool ? 1 : 0);
    }

    default Boolean toBool(Integer integer) {
        return integer == null || integer.equals(0) ? false : true;
    }
}
