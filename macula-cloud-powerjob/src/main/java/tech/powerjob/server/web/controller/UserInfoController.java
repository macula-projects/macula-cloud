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

package tech.powerjob.server.web.controller;

import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;
import tech.powerjob.common.response.ResultDTO;
import tech.powerjob.server.core.service.UserService;
import tech.powerjob.server.persistence.remote.model.UserInfoDO;
import tech.powerjob.server.persistence.remote.repository.UserInfoRepository;
import tech.powerjob.server.web.request.ModifyUserInfoRequest;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户信息控制层
 *
 * @author tjq
 * @since 2020/4/12
 */
@RestController
@RequestMapping("/user")
public class UserInfoController {

    @Resource
    private UserService userService;
    @Resource
    private UserInfoRepository userInfoRepository;

    @PostMapping("save")
    public ResultDTO<Void> save(@RequestBody ModifyUserInfoRequest request) {
        UserInfoDO userInfoDO = new UserInfoDO();
        BeanUtils.copyProperties(request, userInfoDO);
        userService.save(userInfoDO);
        return ResultDTO.success(null);
    }

    @GetMapping("list")
    public ResultDTO<List<UserItemVO>> list(@RequestParam(required = false) String name) {

        List<UserInfoDO> result;
        if (StringUtils.isEmpty(name)) {
            result = userInfoRepository.findAll();
        } else {
            result = userInfoRepository.findByUsernameLike("%" + name + "%");
        }
        return ResultDTO.success(convert(result));
    }

    private static List<UserItemVO> convert(List<UserInfoDO> data) {
        if (CollectionUtils.isEmpty(data)) {
            return Lists.newLinkedList();
        }
        return data.stream().map(x -> new UserItemVO(x.getId(), x.getUsername())).collect(Collectors.toList());
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static final class UserItemVO {
        private Long id;
        private String username;
    }
}
