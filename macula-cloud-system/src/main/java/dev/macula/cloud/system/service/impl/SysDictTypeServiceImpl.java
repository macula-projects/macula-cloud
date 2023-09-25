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

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import dev.macula.boot.result.Option;
import dev.macula.cloud.system.converter.DictTypeConverter;
import dev.macula.cloud.system.form.DictTypeForm;
import dev.macula.cloud.system.mapper.SysDictTypeMapper;
import dev.macula.cloud.system.pojo.entity.SysDictItem;
import dev.macula.cloud.system.pojo.entity.SysDictType;
import dev.macula.cloud.system.query.DictTypePageQuery;
import dev.macula.cloud.system.service.SysDictItemService;
import dev.macula.cloud.system.service.SysDictTypeService;
import dev.macula.cloud.system.vo.dict.DictTypePageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据字典类型业务实现类
 *
 * @author haoxr
 * @since 2022/10/12
 */
@Service
@RequiredArgsConstructor
public class SysDictTypeServiceImpl extends ServiceImpl<SysDictTypeMapper, SysDictType> implements SysDictTypeService {

    private final SysDictItemService dictItemService;
    private final DictTypeConverter dictTypeConverter;

    @Override
    public Page<DictTypePageVO> listDictTypePages(DictTypePageQuery queryParams) {
        // 查询参数
        int pageNum = queryParams.getPageNum();
        int pageSize = queryParams.getPageSize();
        String keywords = queryParams.getKeywords();

        // 查询数据
        Page<SysDictType> dictTypePage = this.page(new Page<>(pageNum, pageSize),
            new LambdaQueryWrapper<SysDictType>().like(StrUtil.isNotBlank(keywords), SysDictType::getName, keywords)
                .or().like(StrUtil.isNotBlank(keywords), SysDictType::getCode, keywords)
                .select(SysDictType::getId, SysDictType::getName, SysDictType::getCode, SysDictType::getStatus));

        // 实体转换
        return dictTypeConverter.entity2Page(dictTypePage);
    }

    @Override
    public DictTypeForm getDictTypeFormData(Long id) {
        // 获取entity
        SysDictType entity = this.getOne(new LambdaQueryWrapper<SysDictType>().eq(SysDictType::getId, id)
            .select(SysDictType::getId, SysDictType::getName, SysDictType::getCode, SysDictType::getStatus,
                SysDictType::getRemark));
        Assert.isTrue(entity != null, "字典类型不存在");

        // 实体转换
        return dictTypeConverter.entity2Form(entity);
    }

    @Override
    public boolean saveDictType(DictTypeForm dictTypeForm) {
        // 实体对象转换 form->entity
        SysDictType entity = dictTypeConverter.form2Entity(dictTypeForm);
        // 持久化
        return this.save(entity);
    }

    @Override
    public boolean updateDictType(Long id, DictTypeForm dictTypeForm) {
        // 获取字典类型
        SysDictType sysDictType = this.getById(id);
        Assert.isTrue(sysDictType != null, "字典类型不存在");

        SysDictType entity = dictTypeConverter.form2Entity(dictTypeForm);
        boolean result = this.updateById(entity);
        if (result) {
            // 字典类型code变化，同步修改字典项的类型code
            String oldCode = sysDictType.getCode();
            String newCode = dictTypeForm.getCode();
            if (!StrUtil.equals(oldCode, newCode)) {
                dictItemService.update(new LambdaUpdateWrapper<SysDictItem>().eq(SysDictItem::getTypeCode, oldCode)
                    .set(SysDictItem::getTypeCode, newCode));
            }
        }
        return result;
    }

    @Override
    @Transactional
    public boolean deleteDictTypes(String idsStr) {

        Assert.isTrue(StrUtil.isNotBlank(idsStr), "删除数据为空");

        List<String> ids = Arrays.stream(idsStr.split(",")).collect(Collectors.toList());

        // 删除字典数据项
        List<String> dictTypeCodes =
            this.list(new LambdaQueryWrapper<SysDictType>().in(SysDictType::getId, ids).select(SysDictType::getCode))
                .stream().map(SysDictType::getCode).collect(Collectors.toList());
        if (CollectionUtil.isNotEmpty(dictTypeCodes)) {
            dictItemService.remove(new LambdaQueryWrapper<SysDictItem>().in(SysDictItem::getTypeCode, dictTypeCodes));
        }
        // 删除字典类型
        return this.removeByIds(ids);
    }

    @Override
    public List<Option<String>> listDictItemsByTypeCode(String typeCode) {
        // 数据字典项
        List<SysDictItem> dictItems = dictItemService.list(
            new LambdaQueryWrapper<SysDictItem>().eq(SysDictItem::getTypeCode, typeCode)
                .select(SysDictItem::getValue, SysDictItem::getName));

        // 转换下拉数据
        return CollectionUtil.emptyIfNull(dictItems).stream()
            .map(dictItem -> new Option<>(dictItem.getValue(), dictItem.getName())).collect(Collectors.toList());
    }
}




