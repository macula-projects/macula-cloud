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
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 应用对象转换器
 *
 */
@Mapper(componentModel = "spring")
public interface ApplicationConverter {

    SysApplication form2Entity(ApplicationForm appForm);

    Page<ApplicationVO> bo2Vo(Page<ApplicationBO> bo);

    List<ApplicationVO> bo2Vo(List<ApplicationBO> bo);

}