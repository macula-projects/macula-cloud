package dev.macula.cloud.iam.authentication.weapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.util.Assert;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;

public class WeappAuthenticationFilter extends AbstractAuthenticationProcessingFilter {
    private static final AntPathRequestMatcher DEFAULT_ANT_PATH_REQUEST_MATCHER =
        new AntPathRequestMatcher("/login/weapp", "POST");
    private final ObjectMapper om = new ObjectMapper();
    private Converter<HttpServletRequest, WeappAuthenticationToken> weappAuthenticationTokenConverter;
    private boolean postOnly = true;

    public WeappAuthenticationFilter() {
        super(DEFAULT_ANT_PATH_REQUEST_MATCHER);
        this.weappAuthenticationTokenConverter = defaultConverter();
    }

    public WeappAuthenticationFilter(AuthenticationManager authenticationManager) {
        super(DEFAULT_ANT_PATH_REQUEST_MATCHER, authenticationManager);
        this.weappAuthenticationTokenConverter = defaultConverter();
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
        throws AuthenticationException, IOException, ServletException {
        if (this.postOnly && !HttpMethod.POST.matches(request.getMethod())) {
            throw new AuthenticationServiceException("Authentication method not supported: " + request.getMethod());
        }
        WeappAuthenticationToken authRequest = weappAuthenticationTokenConverter.convert(request);
        if (authRequest == null) {
            throw new BadCredentialsException("fail to extract miniapp authentication request params");
        }
        setDetails(request, authRequest);
        return this.getAuthenticationManager().authenticate(authRequest);
    }

    protected void setDetails(HttpServletRequest request, WeappAuthenticationToken authRequest) {
        authRequest.setDetails(this.authenticationDetailsSource.buildDetails(request));
    }

    public void setConverter(Converter<HttpServletRequest, WeappAuthenticationToken> converter) {
        Assert.notNull(converter, "Converter must not be null");
        this.weappAuthenticationTokenConverter = converter;
    }

    public void setPostOnly(boolean postOnly) {
        this.postOnly = postOnly;
    }

    private Converter<HttpServletRequest, WeappAuthenticationToken> defaultConverter() {
        return request -> {
            try (BufferedReader reader = request.getReader()) {
                WeappRequest weappRequest = this.om.readValue(reader, WeappRequest.class);
                return new WeappAuthenticationToken(weappRequest);
            } catch (IOException e) {
                return null;
            }
        };
    }
}
