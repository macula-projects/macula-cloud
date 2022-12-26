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

package dev.macula.cloud.tinyid.vo;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import dev.macula.boot.result.ResultCode;

import java.io.Serializable;

/**
 * @author du_imba
 */

@AllArgsConstructor
@NoArgsConstructor
public enum ErrorCode implements ResultCode, Serializable {
    /**
     * token is wrong
     */
    TOKEN_ERR("ID500", "token is error"),
    /**
     * server internal error
     */
    SYS_ERR("ID502", "sys error");


    private String code;

    private String msg;

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMsg() {
        return msg;
    }

}


