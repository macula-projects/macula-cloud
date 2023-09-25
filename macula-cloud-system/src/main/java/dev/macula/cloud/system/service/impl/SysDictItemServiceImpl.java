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
import org.springframework.transaction.annotation.Transactional;

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
                .eq(StrUtil.isNotBlank(typeCode), SysDictItem::getTypeCode, typeCode));

        // 实体转换
        return dictItemConverter.entity2Page(dictItemPage);
    }

    @Override
    public DictItemForm getDictItemForm(Long id) {
        // 获取entity
        SysDictItem entity = this.getOne(new LambdaQueryWrapper<SysDictItem>().eq(SysDictItem::getId, id));
        Assert.isTrue(entity != null, "字典数据项不存在");

        // 实体转换
        return dictItemConverter.entity2Form(entity);
    }

    @Override
    public boolean saveDictItem(DictItemForm dictItemForm) {
        // 实体对象转换 form->entity
        SysDictItem entity = dictItemConverter.form2Entity(dictItemForm);
        // 持久化
        return this.save(entity);
    }

    @Override
    public boolean updateDictItem(Long id, DictItemForm dictItemForm) {
        SysDictItem entity = dictItemConverter.form2Entity(dictItemForm);
        return this.updateById(entity);
    }

    @Override
    @Transactional
    public boolean deleteDictItems(String idsStr) {
        Assert.isTrue(StrUtil.isNotBlank(idsStr), "删除数据为空");
        //
        List<Long> ids = Arrays.stream(idsStr.split(",")).map(Long::parseLong).collect(Collectors.toList());

        // 删除字典数据项
        return this.removeByIds(ids);
    }

}




