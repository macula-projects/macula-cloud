package dev.macula.cloud.iam.authentication.sms;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * 按手机号加载短信登录用户的服务契约。
 *
 * @author felord
 * @since 6.1.0
 */
public interface SmsUserDetailsService {
    /**
     * load user by phone
     *
     * @param phone phone
     * @return userDetails
     * @throws UsernameNotFoundException not found user
     */
    UserDetails loadUserByPhone(String phone) throws UsernameNotFoundException;
}
