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

package dev.macula.cloud.system.handler;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import dev.macula.boot.result.ApiResultCode;
import dev.macula.boot.result.Result;
import dev.macula.cloud.system.vo.user.UserLoginVO;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户接口降级逻辑
 *
 * @author haoxr
 * @createTime 2021/4/23 23:30
 */
@Slf4j
public class UserBlockHandler {

    /**
     * 获取当前登录用户信息的熔断降级处理
     *
     * @param blockException
     * @return
     */
    public static Result<UserLoginVO> handleGetCurrentUserBlock(BlockException blockException) {
        return Result.success(new UserLoginVO());
    }

    public static Result handleGetUserByUsernameBlock(String username, BlockException blockException) {
        log.info("降级了：{}", username);
        return Result.failed(ApiResultCode.DEGRADATION);
    }
}
