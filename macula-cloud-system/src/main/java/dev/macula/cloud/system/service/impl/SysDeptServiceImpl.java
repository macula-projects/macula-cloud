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

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import dev.macula.boot.constants.SecurityConstants;
import dev.macula.boot.enums.StatusEnum;
import dev.macula.boot.result.Option;
import dev.macula.cloud.system.converter.DeptConverter;
import dev.macula.cloud.system.form.DeptForm;
import dev.macula.cloud.system.mapper.SysDeptMapper;
import dev.macula.cloud.system.pojo.entity.SysDept;
import dev.macula.cloud.system.query.DeptQuery;
import dev.macula.cloud.system.service.SysDeptService;
import dev.macula.cloud.system.vo.dept.DeptVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 部门业务实现类
 *
 * @author haoxr
 * @since 2021-08-22
 */
@Service
@RequiredArgsConstructor
public class SysDeptServiceImpl extends ServiceImpl<SysDeptMapper, SysDept> implements SysDeptService {

    private final DeptConverter deptConverter;

    @Override
    public List<DeptVO> listDepartments(DeptQuery queryParams) {
        // 查询参数
        String keywords = queryParams.getKeywords();
        Integer status = queryParams.getStatus();

        // 查询数据
        List<SysDept> deptList = this.list(
            new LambdaQueryWrapper<SysDept>().like(StrUtil.isNotBlank(keywords), SysDept::getName, keywords)
                .eq(Validator.isNotNull(status), SysDept::getStatus, status).orderByAsc(SysDept::getSort));

        List<DeptVO> list = new ArrayList<>();

        if (CollectionUtil.isNotEmpty(deptList)) {

            Set<Long> cacheDeptIds = deptList.stream().map(SysDept::getId).collect(Collectors.toSet());

            for (SysDept dept : deptList) {
                Long parentId = dept.getParentId();
                // 不在缓存ID列表的parentId是顶级节点ID，以此作为递归开始
                if (!cacheDeptIds.contains(parentId)) {
                    list.addAll(recurDepartments(parentId, deptList));
                    cacheDeptIds.add(parentId); // 避免重复递归
                }
            }
        }

        //  列表为空说明所有的节点都是独立的
        if (list.isEmpty()) {
            return deptList.stream().map(item -> {
                DeptVO deptVO = new DeptVO();
                BeanUtil.copyProperties(item, deptVO);
                return deptVO;
            }).collect(Collectors.toList());
        }

        return list;
    }

    /**
     * 部门下拉选项
     *
     * @return 部门下拉选项
     */
    @Override
    public List<Option<Long>> listDeptOptions() {
        List<SysDept> deptList = this.list(
            new LambdaQueryWrapper<SysDept>().eq(SysDept::getStatus, StatusEnum.ENABLE.getValue())
                .select(SysDept::getId, SysDept::getParentId, SysDept::getName).orderByAsc(SysDept::getSort));

        //        List<Option> options = buildDeptTree(deptList);
        return recurDeptTreeOptions(SecurityConstants.ROOT_NODE_ID, deptList);
    }

    @Override
    public Long saveDept(DeptForm formData) {
        SysDept entity = deptConverter.form2Entity(formData);
        // 部门路径
        String treePath = generateDeptTreePath(formData.getParentId());
        entity.setTreePath(treePath);
        // 保存部门并返回部门ID
        this.save(entity);
        return entity.getId();
    }

    @Override
    public Long updateDept(Long deptId, DeptForm formData) {
        // form->entity
        SysDept entity = deptConverter.form2Entity(formData);
        entity.setId(deptId);
        // 部门路径
        String treePath = generateDeptTreePath(formData.getParentId());
        entity.setTreePath(treePath);
        // 保存部门并返回部门ID
        this.updateById(entity);
        return entity.getId();
    }

    @Override
    public boolean deleteByIds(String ids) {
        // 删除部门及子部门
        Optional.of(Arrays.stream(ids.split(","))).ifPresent(deptIds -> deptIds.forEach(deptId -> this.remove(
            new LambdaQueryWrapper<SysDept>().eq(SysDept::getId, deptId).or()
                .apply("concat (',',tree_path,',') like concat('%,',{0},',%')", deptId))));
        return true;
    }

    @Override
    public DeptForm getDeptForm(Long deptId) {
        SysDept entity = this.getOne(new LambdaQueryWrapper<SysDept>().eq(SysDept::getId, deptId));
        return deptConverter.entity2Form(entity);
    }

    /**
     * 递归生成部门层级列表
     *
     * @param parentId 父ID
     * @param deptList 部门列表
     * @return 部门层级列表
     */
    private List<DeptVO> recurDepartments(Long parentId, List<SysDept> deptList) {
        return deptList.stream().filter(dept -> dept.getParentId().equals(parentId)).map(dept -> {
            DeptVO deptVO = deptConverter.entity2Vo(dept);
            List<DeptVO> children = recurDepartments(dept.getId(), deptList);
            deptVO.setChildren(children);
            return deptVO;
        }).collect(Collectors.toList());
    }

    /**
     * 递归生成部门表格层级列表
     *
     * @param depts 部门列表
     * @return 部门下拉选项
     */
    private List<Option<Long>> buildDeptTree(List<SysDept> depts) {
        if (CollectionUtil.isEmpty(depts)) {
            return new ArrayList<>();
        }
        List<Option<Long>> returnList = new ArrayList<>();
        List<Long> tempList = new ArrayList<Long>();
        for (SysDept dept : depts) {
            tempList.add(dept.getId());
        }
        for (SysDept dept : depts) {
            // 如果是顶级节点, 遍历该父节点的所有子节点
            if (!tempList.contains(dept.getParentId())) {
                Option<Long> option = new Option<>(dept.getId(), dept.getName());
                recursionFn(depts, option);
                returnList.add(option);
            }
        }
        if (returnList.isEmpty()) {
            depts.forEach(dept -> {
                Option<Long> option = new Option<>(dept.getId(), dept.getName());
                returnList.add(option);
            });
        }
        return returnList;
    }

    /**
     * 递归生成部门表格层级列表
     *
     * @param parentId 父ID
     * @param deptList 部门列表
     * @return 部门层级
     */
    private List<Option<Long>> recurDeptTreeOptions(long parentId, List<SysDept> deptList) {
        if (CollectionUtil.isEmpty(deptList)) {
            return new ArrayList<>();
        }

        return deptList.stream().filter(dept -> dept.getParentId().equals(parentId)).map(dept -> {
            Option<Long> option = new Option<>(dept.getId(), dept.getName());
            List<Option<Long>> children = recurDeptTreeOptions(dept.getId(), deptList);
            if (CollectionUtil.isNotEmpty(children)) {
                option.setChildren(children);
            }
            return option;
        }).collect(Collectors.toList());
    }

    /**
     * 递归列表
     */
    private void recursionFn(List<SysDept> list, Option<Long> t) {
        // 得到子节点列表
        List<Option<Long>> childList = getChildList(list, t);
        t.setChildren(childList);
        for (Option<Long> tChild : childList) {
            if (hasChild(list, tChild)) {
                recursionFn(list, tChild);
            }
        }
    }

    /**
     * 得到子节点列表
     */
    private List<Option<Long>> getChildList(List<SysDept> list, Option<Long> t) {
        List<Option<Long>> tlist = new ArrayList<>();
        for (SysDept n : list) {
            if (n.getParentId() != null && n.getParentId().equals(t.getValue())) {
                Option<Long> option = new Option<>(n.getId(), n.getName());
                tlist.add(option);
            }
        }
        return tlist;
    }

    /**
     * 判断是否有子节点
     */
    private boolean hasChild(List<SysDept> list, Option<Long> t) {
        return !getChildList(list, t).isEmpty();
    }

    /**
     * 部门路径生成
     *
     * @param parentId 父ID
     * @return 部门路径
     */
    private String generateDeptTreePath(Long parentId) {
        String treePath = null;
        if (SecurityConstants.ROOT_NODE_ID.equals(parentId)) {
            treePath = parentId + "";
        } else {
            SysDept parent = this.getById(parentId);
            if (parent != null) {
                treePath = parent.getTreePath() + "," + parent.getId();
            }
        }
        return treePath;
    }
}
