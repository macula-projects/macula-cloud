package dev.macula.cloud.iam.authentication.weapp;

import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.HttpSecurityBuilder;
import org.springframework.security.config.annotation.web.builders.FilterOrderRegUtils;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractAuthenticationFilterConfigurer;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.Assert;

import static org.springframework.security.config.annotation.web.builders.FilterOrderRegUtils.registryFilterOrder;

public class WeappLoginFilterConfigurer<H extends HttpSecurityBuilder<H>>
    extends AbstractAuthenticationFilterConfigurer<H, WeappLoginFilterConfigurer<H>, WeappAuthenticationFilter> {
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
    public void init(H http) {
        http.authenticationProvider(postProcess(authenticationProvider(http)));
        registryFilterOrder((HttpSecurity)http, this.getAuthenticationFilter(),
            UsernamePasswordAuthenticationFilter.class, FilterOrderRegUtils.Position.AFTER);
        initPreAuthenticationFilter(http);

        super.init(http);
    }

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
        return PathPatternRequestMatcher.pathPattern(HttpMethod.POST, loginProcessingUrl);
    }

    private <T> T getBeanOrNull(ApplicationContext applicationContext, Class<T> beanType) {
        return applicationContext == null ? null : applicationContext.getBeanProvider(beanType).getIfUnique();
    }
}
