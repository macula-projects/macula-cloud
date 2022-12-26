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

import com.alibaba.csp.sentinel.adapter.spring.webmvc.callback.BlockExceptionHandler;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.macula.boot.result.Result;
import dev.macula.boot.result.ApiResultCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author haoxr
 * @description 自定义流控异常
 * @createTime 2021/4/12 22:57
 */
@Component
public class DefaultBlockExceptionHandler implements BlockExceptionHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, BlockException e) throws Exception {
        response.setStatus(HttpStatus.OK.value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=utf-8");

        ObjectMapper objectMapper = new ObjectMapper();

        if (e instanceof FlowException) {
            // objectMapper.writeValue 用于将java对象转位JSON格式返回调用方
            objectMapper.writeValue(response.getWriter(), Result.failed(ApiResultCode.FLOW_LIMITING));
        } else if (e instanceof DegradeException) {
            objectMapper.writeValue(response.getWriter(), Result.failed(ApiResultCode.DEGRADATION));
        }
    }
}
