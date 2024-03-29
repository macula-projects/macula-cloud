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

package dev.macula.cloud.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import dev.macula.cloud.system.form.DictItemForm;
import dev.macula.cloud.system.pojo.entity.SysDictItem;
import dev.macula.cloud.system.query.DictItemPageQuery;
import dev.macula.cloud.system.vo.dict.DictItemPageVO;

/**
 *
 */
public interface SysDictItemService extends IService<SysDictItem> {
    /**
     * 字典数据项分页列表
     *
     * @param queryParams 查询参数
     * @return 字典数据项列表
     */
    Page<DictItemPageVO> listDictItemPages(DictItemPageQuery queryParams);

    /**
     * 字典数据项表单详情
     *
     * @param id 字典数据项ID
     * @return 字典数据项详情
     */
    DictItemForm getDictItemForm(Long id);

    /**
     * 新增字典数据项
     *
     * @param dictItemForm 字典数据项表单
     * @return 新增是否成功
     */
    boolean saveDictItem(DictItemForm dictItemForm);

    /**
     * 修改字典数据项
     *
     * @param id           字典数据项ID
     * @param dictItemForm 字典数据项表单
     * @return 更新是否成功
     */
    boolean updateDictItem(Long id, DictItemForm dictItemForm);

    /**
     * 删除字典数据项
     *
     * @param idsStr 字典数据项ID，多个以英文逗号(,)分割
     * @return 删除是否成功
     */
    boolean deleteDictItems(String idsStr);
}
