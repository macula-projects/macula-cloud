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
import dev.macula.cloud.system.form.UserForm;
import dev.macula.cloud.system.pojo.bo.UserBO;
import dev.macula.cloud.system.pojo.bo.UserExportBO;
import dev.macula.cloud.system.vo.user.UserExportVO;
import java.util.List;
import dev.macula.cloud.system.pojo.bo.UserFormBO;
import dev.macula.cloud.system.pojo.entity.SysUser;
import dev.macula.cloud.system.vo.user.UserLoginVO;
import dev.macula.cloud.system.vo.user.UserVO;
import org.mapstruct.InheritInverseConfiguration;
import dev.macula.boot.mapstruct.GlobalMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

/**
 * 用户对象转换器
 *
 * @author haoxr
 * @since 2022/6/8
 */
@Mapper(config = GlobalMapperConfig.class)
public interface UserConverter {

    /** 转换用户展示模型，同时解析性别标签和审计时间。 */
    @Mappings({@Mapping(target = "genderLabel",
        expression = "java(dev.macula.boot.base.IBaseEnum.getLabelByValue(po.getGender(), dev.macula.boot.enums.GenderEnum.class))")})
    UserVO po2Vo(UserBO po);
    /** 将数据库用户查询分页转换为接口分页。 */
    Page<UserVO> bo2Vo(Page<UserBO> po);
    /** 将用户编辑查询结果转换为表单。 */
    UserForm bo2Form(UserFormBO po);

    /** 将用户表单映射为数据库实体。 */
    @InheritInverseConfiguration(name = "entity2Form")
    SysUser form2Entity(UserForm entity);

    /** 将用户实体转换为登录信息，不增加数据库字段。 */
    @Mappings({@Mapping(target = "userId", source = "id")})
    UserLoginVO entity2LoginUser(SysUser entity);
    /** 将数据库导出查询结果转换为Date导出模型，复用公共存储时区。 */
    List<UserExportVO> toExportVOs(List<UserExportBO> source);
}
