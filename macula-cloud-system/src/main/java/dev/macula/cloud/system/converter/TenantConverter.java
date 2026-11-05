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

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.macula.cloud.system.form.TenantForm;
import dev.macula.cloud.system.pojo.bo.TenantBO;
import dev.macula.cloud.system.pojo.entity.SysTenantInfo;
import dev.macula.cloud.system.vo.tenant.TenantPageVO;
import org.mapstruct.InheritInverseConfiguration;
import dev.macula.boot.mapstruct.GlobalMapperConfig;
import org.mapstruct.Mapper;

/**
 * 转换数据库查询对象与接口对象，统一使用公共日期时区规则。
 * @author Rain
 * @since 6.1.0
 */
@Mapper(config = GlobalMapperConfig.class)
public interface TenantConverter {

    /** 将租户可维护字段映射为数据库实体。 */
    @InheritInverseConfiguration(name = "entity2Form")
    SysTenantInfo form2Entity(TenantForm entity);
    /** 将租户查询分页及负责人列表转换为接口模型。 */
    Page<TenantPageVO> bo2Page(Page<TenantBO> page);
}
