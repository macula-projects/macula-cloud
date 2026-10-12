package dev.macula.cloud.iam.authentication.sms;

import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.util.Assert;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 将短信登录请求转换为 {@link SmsAuthenticationToken}。
 *
 * @author n1
 * @since 6.1.0
 */
public class SmsAuthenticationFilter extends AbstractAuthenticationProcessingFilter {
    public static final String SPRING_SECURITY_FORM_PHONE_KEY = "phone";

    public static final String SPRING_SECURITY_FORM_CODE_KEY = "code";

    private static final PathPatternRequestMatcher DEFAULT_REQUEST_MATCHER =
        PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/login/sms");

    private String phoneParameter = SPRING_SECURITY_FORM_PHONE_KEY;

    private String codeParameter = SPRING_SECURITY_FORM_CODE_KEY;

    private Converter<HttpServletRequest, SmsAuthenticationToken> smsAuthenticationTokenConverter;

    private boolean postOnly = true;

    public SmsAuthenticationFilter() {
        super(DEFAULT_REQUEST_MATCHER);
        this.smsAuthenticationTokenConverter = defaultConverter();
    }

    public SmsAuthenticationFilter(AuthenticationManager authenticationManager) {
        super(DEFAULT_REQUEST_MATCHER, authenticationManager);
        this.smsAuthenticationTokenConverter = defaultConverter();
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
        throws AuthenticationException {
        if (this.postOnly && !HttpMethod.POST.matches(request.getMethod())) {
            throw new AuthenticationServiceException("Authentication method not supported: " + request.getMethod());
        }

        SmsAuthenticationToken authRequest = smsAuthenticationTokenConverter.convert(request);
        // Allow subclasses to set the "details" property
        setDetails(request, authRequest);
        return this.getAuthenticationManager().authenticate(authRequest);
    }

    private Converter<HttpServletRequest, SmsAuthenticationToken> defaultConverter() {
        return request -> {
            String phone = request.getParameter(this.phoneParameter);
            phone = (phone != null) ? phone.trim() : "";
            String code = request.getParameter(this.codeParameter);
            code = (code != null) ? code.trim() : "";
            return new SmsAuthenticationToken(phone, code);
        };
    }

    protected void setDetails(HttpServletRequest request, SmsAuthenticationToken authRequest) {
        authRequest.setDetails(this.authenticationDetailsSource.buildDetails(request));
    }

    public void setPhoneParameter(String phoneParameter) {
        Assert.hasText(phoneParameter, "Phone parameter must not be empty or null");
        this.phoneParameter = phoneParameter;
    }

    public void setCodeParameter(String codeParameter) {
        Assert.hasText(codeParameter, "Code parameter must not be empty or null");
        this.codeParameter = codeParameter;
    }

    public void setConverter(Converter<HttpServletRequest, SmsAuthenticationToken> converter) {
        Assert.notNull(converter, "Converter must not be null");
        this.smsAuthenticationTokenConverter = converter;
    }

    public void setPostOnly(boolean postOnly) {
        this.postOnly = postOnly;
    }

    public final String getPhoneParameter() {
        return this.phoneParameter;
    }

    public final String getCodeParameter() {
        return this.codeParameter;
    }
}
