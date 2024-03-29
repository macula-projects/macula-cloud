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
     * @return 应用列表
     */
    Page<ApplicationVO> listApplicationPages(ApplicationPageQuery queryParams);

    /**
     * 新增应用
     *
     * @param appForm 应用表单对象
     * @return 新增是否成功
     */
    boolean saveApplication(ApplicationForm appForm);

    /**
     * 修改应用
     *
     * @param appId   应用ID
     * @param appForm 应用表单对象
     * @return 修改是否成功
     */
    boolean updateApplication(Long appId, ApplicationForm appForm);

    /**
     * 删除应用
     *
     * @param idsStr 应用ID，多个以英文逗号(,)分割
     * @return 删除是否成功
     */
    boolean deleteApplications(String idsStr);

    /**
     * 管理维护人
     *
     * @param appId 应用ID
     * @param appForm 应用表单
     * @return 添加维护人是否成功
     */
    boolean addMaintainer(Long appId, ApplicationForm appForm);

    /**
     * 刷新应用缓存, 应用信息变更及system重启时调用
     */
    void refreshApplicationCache();

    /**
     * 验证新增或编辑的应用编码是否合法
     *
     * @param appId 应用ID
     * @param appCode 应用CODE
     * @return 校验是否成功
     */
    boolean validatorAppCode(Long appId, String appCode);
}
