package dev.macula.cloud.iam.authentication.sms;

/**
 * 校验手机号短信验证码的服务契约。
 *
 * @author n1
 * @since 6.1.0
 */
public interface SmsService {

    /**
     * verify code
     *
     * @param phone   phone
     * @param rawCode rawCode
     * @return isVerified
     */
    boolean verifyCode(String phone, String rawCode);

    /**
     * 发送验证码
     * @param phone 手机号
     */
    void sendCode(String phone);
}
