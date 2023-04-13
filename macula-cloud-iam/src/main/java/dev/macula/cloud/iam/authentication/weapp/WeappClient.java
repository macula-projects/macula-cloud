package dev.macula.cloud.iam.authentication.weapp;

import lombok.Data;

/**
 * @author felord.cn
 * @since 1.0.8.RELEASE
 */
@Data
public class WeappClient {
    private String clientId;
    private String appId;
    private String secret;
}
