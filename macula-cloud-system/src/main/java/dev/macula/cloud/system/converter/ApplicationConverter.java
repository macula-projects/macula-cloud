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
import dev.macula.cloud.system.form.ApplicationForm;
import dev.macula.cloud.system.pojo.bo.ApplicationBO;
import dev.macula.cloud.system.pojo.entity.SysApplication;
import dev.macula.cloud.system.vo.app.ApplicationVO;
import dev.macula.boot.mapstruct.GlobalMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 应用对象转换器，按同名字段传递认证租户。
 *
 * @author Rain
 * @since 6.1.0
 */
@Mapper(config = GlobalMapperConfig.class)
public interface ApplicationConverter {
    /** 将应用表单映射为数据库实体，审计字段由服务端维护。 */
    SysApplication form2Entity(ApplicationForm appForm);
    /** 单个分页结果将历史逗号 URL 转为多行，并保留独立 IP 配置。 */
    @Mapping(target = "accessPath", expression = "java(dev.macula.boot.security.AccessRuleUtils.normalizeUrls(bo.getAccessPath()))")
    ApplicationVO bo2Vo(ApplicationBO bo);

    /** 将应用分页查询结果转换为Date接口模型。 */
    Page<ApplicationVO> bo2Vo(Page<ApplicationBO> bo);

}
