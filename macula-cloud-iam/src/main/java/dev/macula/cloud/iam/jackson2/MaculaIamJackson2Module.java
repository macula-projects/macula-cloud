package dev.macula.cloud.iam.jackson2;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.module.SimpleModule;
import dev.macula.cloud.iam.authentication.captcha.CaptchaAuthenticationToken;
import dev.macula.cloud.iam.authentication.weapp.WeappAuthenticationToken;
import org.springframework.security.jackson2.SecurityJackson2Modules;

/**
 * @author felord.cn
 * @since 1.0.0
 */
public class MaculaIamJackson2Module extends SimpleModule {

    public MaculaIamJackson2Module() {
        super(MaculaIamJackson2Module.class.getName(), new Version(1, 0, 0, null, null, null));
    }

    @Override
    public void setupModule(SetupContext context) {
        SecurityJackson2Modules.enableDefaultTyping(context.getOwner());
        context.setMixInAnnotations(CaptchaAuthenticationToken.class, CaptchaAuthenticationTokenMixin.class);
        context.setMixInAnnotations(WeappAuthenticationToken.class, WeappAuthenticationTokenMixin.class);
        context.setMixInAnnotations(Long.class, LongMixin.class);
    }
}
