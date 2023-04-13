package dev.macula.cloud.iam.authentication.captcha;

import org.springframework.security.config.annotation.web.configurers.AbstractLoginFilterConfigurer;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.HttpSecurityBuilder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.Assert;

public class CaptchaLoginFilterConfigurer<H extends HttpSecurityBuilder<H>>
    extends AbstractLoginFilterConfigurer<H, CaptchaLoginFilterConfigurer<H>, CaptchaAuthenticationFilter> {

    private CaptchaUserDetailsService captchaUserDetailsService;

    private CaptchaService captchaService;

    public CaptchaLoginFilterConfigurer() {
        super(new CaptchaAuthenticationFilter(), "/login/captcha");
    }

    public CaptchaLoginFilterConfigurer<H> captchaUserDetailsService(
        CaptchaUserDetailsService captchaUserDetailsService) {
        this.captchaUserDetailsService = captchaUserDetailsService;
        return this;
    }

    public CaptchaLoginFilterConfigurer<H> captchaService(CaptchaService captchaService) {
        this.captchaService = captchaService;
        return this;
    }

    @Override
    protected RequestMatcher createLoginProcessingUrlMatcher(String loginProcessingUrl) {
        return new AntPathRequestMatcher(loginProcessingUrl, "POST");
    }

    @Override
    protected AuthenticationProvider authenticationProvider(H http) {
        ApplicationContext applicationContext = http.getSharedObject(ApplicationContext.class);
        CaptchaUserDetailsService captchaUserDetailsService =
            this.captchaUserDetailsService != null ? this.captchaUserDetailsService
                : getBeanOrNull(applicationContext, CaptchaUserDetailsService.class);
        Assert.notNull(captchaUserDetailsService, "captchaUserDetailsService is required");

        CaptchaService captchaService =
            this.captchaService != null ? this.captchaService : getBeanOrNull(applicationContext, CaptchaService.class);
        Assert.notNull(captchaService, "captchaService is required");

        return new CaptchaAuthenticationProvider(captchaUserDetailsService, captchaService);
    }

    @Override
    public void configure(H http) throws Exception {
        http.addFilterAfter(this.getAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        super.configure(http);
    }
}
