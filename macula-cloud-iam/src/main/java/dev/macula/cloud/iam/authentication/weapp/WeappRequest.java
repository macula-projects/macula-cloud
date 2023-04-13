package dev.macula.cloud.iam.authentication.weapp;

import lombok.Data;

/**
 * @author n1
 * @since 2021/6/25 11:19
 */
@Data
public class WeappRequest {
    private String clientId;
    private String openId;
    private String unionId;
    private String iv;
    private String encryptedData;
}
