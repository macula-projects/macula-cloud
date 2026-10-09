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
package dev.macula.cloud.tinyid.config;

import dev.macula.boot.result.ApiResultCode;
import dev.macula.boot.result.Result;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 保留 Spring MVC 请求错误及显式 HTTP 异常的状态；业务异常仍交给 Macula 统一处理器。
 *
 * @author Rain
 * @since 6.1.0
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TinyIdHttpExceptionAdvice extends ResponseEntityExceptionHandler {

    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception exception, @Nullable Object body,
        HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var code = status.is5xxServerError() ? ApiResultCode.SYS_ERROR : ApiResultCode.VALIDATE_ERROR;
        String detail = code.getMsg();
        if (!status.is5xxServerError() && exception instanceof ErrorResponse error) {
            detail = error.getBody().getDetail();
        }
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);
        return super.handleExceptionInternal(exception, Result.failed(code, detail), responseHeaders, status, request);
    }
}
