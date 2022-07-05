package org.macula.cloud.tinyid.vo;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.macula.boot.api.ResultCode;

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


