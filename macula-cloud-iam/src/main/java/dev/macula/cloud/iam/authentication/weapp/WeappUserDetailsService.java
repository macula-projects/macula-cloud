package dev.macula.cloud.iam.authentication.weapp;

import org.springframework.security.core.userdetails.UserDetails;

/**
 * The interface Channel user details service.
 *
 * @author felord.cn
 * @since 1.0.8.RELEASE
 */
public interface WeappUserDetailsService {

    /**
     * 小程序在微信登录成功后发起后端登录用来注册的方法
     *
     * @param request the request
     * @return the user details
     */
    UserDetails register(WeappRequest request);

    /**
     * openid登录
     * <p>
     * clientId和openId决定唯一性
     *
     * @param clientId the client id
     * @param openId   the open id
     * @return the user details
     */
    UserDetails loadByOpenId(String clientId, String openId);

}
