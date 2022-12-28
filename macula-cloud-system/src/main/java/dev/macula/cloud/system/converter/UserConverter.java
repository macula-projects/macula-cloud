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
import dev.macula.cloud.system.form.UserForm;
import dev.macula.cloud.system.pojo.bo.UserBO;
import dev.macula.cloud.system.pojo.bo.UserFormBO;
import dev.macula.cloud.system.pojo.entity.SysUser;
import dev.macula.cloud.system.vo.user.UserLoginVO;
import dev.macula.cloud.system.vo.user.UserVO;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

/**
 * 用户对象转换器
 *
 * @author haoxr
 * @since 2022/6/8
 */
@Mapper(componentModel = "spring")
public interface UserConverter {

    @Mappings({
            @Mapping(target = "genderLabel", expression = "java(dev.macula.boot.base.IBaseEnum.getLabelByValue(po.getGender(), dev.macula.boot.enums.GenderEnum.class))")
    })
    UserVO po2Vo(UserBO po);

    Page<UserVO> bo2Vo(Page<UserBO> po);

    UserForm bo2Form(UserFormBO po);

    @InheritInverseConfiguration(name = "entity2Form")
    SysUser form2Entity(UserForm entity);

    @Mappings({
            @Mapping(target = "userId", source = "id")
    })
    UserLoginVO entity2LoginUser(SysUser entity);
}
