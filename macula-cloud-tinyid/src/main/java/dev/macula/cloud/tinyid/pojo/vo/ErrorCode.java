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

package dev.macula.cloud.tinyid.pojo.vo;

import dev.macula.boot.result.ResultCode;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * TinyID 发号接口返回的业务错误码。
 *
 * @author Rain
 * @since 6.1.0
 */

@AllArgsConstructor
@NoArgsConstructor
public enum ErrorCode implements ResultCode, Serializable {
    /**
     * server internal error
     */
    SYS_ERR("ID502", "sys error"),
    /** 发号业务尚未配置。 */
    BIZ_TYPE_NOT_FOUND("ID503", "发号业务不存在"),
    /** 乐观锁重试耗尽。 */
    SEGMENT_CONFLICT("ID504", "号段更新冲突，请稍后重试");

    /** 错误码。 */
    private String code;

    /** 错误提示。 */
    private String msg;

    /** {@inheritDoc} */
    @Override
    public String getCode() {
        return code;
    }

    /** {@inheritDoc} */
    @Override
    public String getMsg() {
        return msg;
    }

}
