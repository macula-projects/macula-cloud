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

package dev.macula.cloud.system.service.impl;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import dev.macula.cloud.system.converter.DictItemConverter;
import dev.macula.cloud.system.form.DictItemForm;
import dev.macula.cloud.system.mapper.SysDictItemMapper;
import dev.macula.cloud.system.pojo.entity.SysDictItem;
import dev.macula.cloud.system.query.DictItemPageQuery;
import dev.macula.cloud.system.service.SysDictItemService;
import dev.macula.cloud.system.vo.dict.DictItemPageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据字典项业务实现类
 *
 * @author haoxr
 * @since 2022/10/12
 */
@Service
@RequiredArgsConstructor
public class SysDictItemServiceImpl extends ServiceImpl<SysDictItemMapper, SysDictItem> implements SysDictItemService {

    private final DictItemConverter dictItemConverter;

    /**
     * 字典数据项分页列表
     *
     * @param queryParams
     * @return
     */
    @Override
    public Page<DictItemPageVO> listDictItemPages(DictItemPageQuery queryParams) {
        // 查询参数
        int pageNum = queryParams.getPageNum();
        int pageSize = queryParams.getPageSize();
        String keywords = queryParams.getKeywords();
        String typeCode = queryParams.getTypeCode();

        // 查询数据
        Page<SysDictItem> dictItemPage = this.page(new Page<>(pageNum, pageSize),
            new LambdaQueryWrapper<SysDictItem>().like(StrUtil.isNotBlank(keywords), SysDictItem::getName, keywords)
                .eq(StrUtil.isNotBlank(typeCode), SysDictItem::getTypeCode, typeCode)
                .select(SysDictItem::getId, SysDictItem::getName, SysDictItem::getValue, SysDictItem::getStatus));

        // 实体转换
        Page<DictItemPageVO> pageResult = dictItemConverter.entity2Page(dictItemPage);
        return pageResult;
    }

    /**
     * 字典数据项表单详情
     *
     * @param id 字典数据项ID
     * @return
     */
    @Override
    public DictItemForm getDictItemForm(Long id) {
        // 获取entity
        SysDictItem entity = this.getOne(new LambdaQueryWrapper<SysDictItem>().eq(SysDictItem::getId, id)
            .select(SysDictItem::getId, SysDictItem::getTypeCode, SysDictItem::getName, SysDictItem::getValue,
                SysDictItem::getStatus, SysDictItem::getSort, SysDictItem::getRemark));
        Assert.isTrue(entity != null, "字典数据项不存在");

        // 实体转换
        DictItemForm dictItemForm = dictItemConverter.entity2Form(entity);
        return dictItemForm;
    }

    /**
     * 新增字典数据项
     *
     * @param dictItemForm 字典数据项表单
     * @return
     */
    @Override
    public boolean saveDictItem(DictItemForm dictItemForm) {
        // 实体对象转换 form->entity
        SysDictItem entity = dictItemConverter.form2Entity(dictItemForm);
        // 持久化
        boolean result = this.save(entity);
        return result;
    }

    /**
     * 修改字典数据项
     *
     * @param id           字典数据项ID
     * @param dictItemForm 字典数据项表单
     * @return
     */
    @Override
    public boolean updateDictItem(Long id, DictItemForm dictItemForm) {
        SysDictItem entity = dictItemConverter.form2Entity(dictItemForm);
        boolean result = this.updateById(entity);
        return result;
    }

    /**
     * 删除字典数据项
     *
     * @param idsStr 字典数据项ID，多个以英文逗号(,)分割
     * @return
     */
    @Override
    public boolean deleteDictItems(String idsStr) {
        Assert.isTrue(StrUtil.isNotBlank(idsStr), "删除数据为空");
        //
        List<Long> ids =
            Arrays.asList(idsStr.split(",")).stream().map(id -> Long.parseLong(id)).collect(Collectors.toList());

        // 删除字典数据项
        boolean result = this.removeByIds(ids);
        return result;
    }

}




