package dev.macula.cloud.iam.authentication.weapp;

/**
 * 缓存sessionKey
 *
 * @author felord.cn
 * @since 1.0.8.RELEASE
 */
public interface WeappSessionKeyCache {

    /**
     * Put sessionKey.
     *
     * @param cacheKey   {@code clientId::openId}
     * @param sessionKey the session key
     */
    void put(String cacheKey, String sessionKey);

    /**
     * Get sessionKey.
     *
     * @param cacheKey {@code clientId::openId}
     * @return sessionKey
     */
    String get(String cacheKey);
}
