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

package dev.macula.cloud.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import dev.macula.cloud.system.form.ApplicationForm;
import dev.macula.cloud.system.pojo.entity.SysApplication;
import dev.macula.cloud.system.query.ApplicationPageQuery;
import dev.macula.cloud.system.vo.app.ApplicationVO;


/**
 * 应用业务接口
 */
public interface SysApplicationService extends IService<SysApplication> {

    /**
     * 应用分页列表
     *
     * @return
     */
    Page<ApplicationVO> listApplicationPages(ApplicationPageQuery queryParams);

    /**
     * 新增应用
     *
     * @param appForm 应用表单对象
     * @return
     */
    boolean saveApplication(ApplicationForm appForm);

    /**
     * 修改应用
     *
     * @param appId   应用ID
     * @param appForm 应用表单对象
     * @return
     */
    boolean updateApplication(Long appId, ApplicationForm appForm);


    /**
     * 删除应用
     *
     * @param idsStr 应用ID，多个以英文逗号(,)分割
     * @return
     */
    boolean deleteApplications(String idsStr);

    /**
     * 管理维护人
     * @param appId
     * @param appForm
     * @return
     */
    boolean addMaintainer(Long appId, ApplicationForm appForm);
}
