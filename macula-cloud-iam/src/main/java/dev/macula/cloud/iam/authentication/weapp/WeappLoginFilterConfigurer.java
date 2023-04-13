package dev.macula.cloud.iam.authentication.weapp;

import org.springframework.security.config.annotation.web.configurers.AbstractLoginFilterConfigurer;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.HttpSecurityBuilder;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.Assert;

public class WeappLoginFilterConfigurer<H extends HttpSecurityBuilder<H>>
    extends AbstractLoginFilterConfigurer<H, WeappLoginFilterConfigurer<H>, WeappAuthenticationFilter> {
    private WeappUserDetailsService weappUserDetailsService;

    private WeappClientService weappClientService;

    private WeappSessionKeyCache weappSessionKeyCache;

    public WeappLoginFilterConfigurer() {
        super(new WeappAuthenticationFilter(), "/login/miniapp");
    }

    public WeappLoginFilterConfigurer<H> weappUserDetailsService(WeappUserDetailsService weappUserDetailsService) {
        this.weappUserDetailsService = weappUserDetailsService;
        return this;
    }

    public WeappLoginFilterConfigurer<H> weappClientService(WeappClientService weappClientService) {
        this.weappClientService = weappClientService;
        return this;
    }

    public WeappLoginFilterConfigurer<H> weappSessionKeyCache(WeappSessionKeyCache weappSessionKeyCache) {
        this.weappSessionKeyCache = weappSessionKeyCache;
        return this;
    }

    @Override
    public void init(H http) throws Exception {
        super.init(http);
        initPreAuthenticationFilter(http);
    }

    @Override
    protected AuthenticationProvider authenticationProvider(H http) {
        ApplicationContext applicationContext = http.getSharedObject(ApplicationContext.class);
        WeappUserDetailsService weappUserDetailsService =
            this.weappUserDetailsService != null ? this.weappUserDetailsService
                : getBeanOrNull(applicationContext, WeappUserDetailsService.class);
        Assert.notNull(weappUserDetailsService, "weappUserDetailsService is required");
        return new WeappAuthenticationProvider(weappUserDetailsService);
    }

    private void initPreAuthenticationFilter(H http) {
        ApplicationContext applicationContext = http.getSharedObject(ApplicationContext.class);

        WeappClientService weappClientService = this.weappClientService != null ? this.weappClientService
            : getBeanOrNull(applicationContext, WeappClientService.class);
        Assert.notNull(weappClientService, "weappClientService is required");

        WeappSessionKeyCache weappSessionKeyCache = this.weappSessionKeyCache != null ? this.weappSessionKeyCache
            : getBeanOrNull(applicationContext, WeappSessionKeyCache.class);
        Assert.notNull(weappSessionKeyCache, "weappSessionKeyCache is required");

        WeappPreAuthenticationFilter weappPreAuthenticationFilter =
            new WeappPreAuthenticationFilter(weappClientService, weappSessionKeyCache);
        http.addFilterBefore(postProcess(weappPreAuthenticationFilter), LogoutFilter.class);
    }

    @Override
    protected RequestMatcher createLoginProcessingUrlMatcher(String loginProcessingUrl) {
        return new AntPathRequestMatcher(loginProcessingUrl, "POST");
    }
}
