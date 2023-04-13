package dev.macula.cloud.iam.authentication.weapp;

/**
 * The interface Mini app client service.
 *
 * @author felord.cn
 * @since 1.0.8.RELEASE
 */
@FunctionalInterface
public interface WeappClientService {
    /**
     * Get mini app client.
     *
     * @param clientId the client id
     * @return {@link WeappClient}
     * @see WeappClient#getAppId() MiniAppClient#getAppId()
     * @see WeappClient#getSecret() MiniAppClient#getSecret()
     */
    WeappClient get(String clientId);
}
