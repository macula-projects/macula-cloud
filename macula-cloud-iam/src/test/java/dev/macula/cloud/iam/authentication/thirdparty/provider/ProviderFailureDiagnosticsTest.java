/*
 * Copyright (c) 2026 Macula
 *   macula.dev, China
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.macula.cloud.iam.authentication.thirdparty.provider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证平台不可信响应只能投影有界错误码与固定权限，不能注入认证材料。
 *
 * @author Rain
 * @since 6.1.0
 */
@ExtendWith(OutputCaptureExtension.class)
class ProviderFailureDiagnosticsTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderFailureDiagnosticsTest.class);

    /** 显式子码优先，任意正文和未知权限始终丢弃。 */
    @Test
    void dingtalkAcceptsOnlyNumericSubcodeAndKnownScopes(CapturedOutput output) {
        ProviderFailureDiagnostics.dingtalk(LOGGER, "member", 200, ProviderFailureDiagnostics.response("""
                {"errcode":88,"sub_code":"60011","errmsg":"private-message-marker subcode=99999",
                "accessdenieddetail":{"requiredScopes":["Contact.User.Read","private-secret-marker"]}}
                """));
        assertTrue(output.toString().contains("errcode=88 subcode=60011"));
        assertTrue(output.toString().contains("Contact.User.Read"));
        assertFalse(output.toString().contains("private-"));
        assertFalse(output.toString().contains("99999"));
    }

    /** 错误对象、换行与超长数字不能被转换为日志文本。 */
    @Test
    void malformedFieldsAndInjectedNewlinesAreDiscarded(CapturedOutput output) {
        ProviderFailureDiagnostics.dingtalk(LOGGER, "member", 400, ProviderFailureDiagnostics.response("""
                {"errcode":{"secret":"private-secret-marker"},"sub_code":"60011\\nFORGED_MARKER",
                "errmsg":"private-response-marker [subcode=1234567890123,submsg=private-user-marker]"}
                """));
        assertTrue(output.toString().contains("errcode=unknown subcode=unknown"));
        assertFalse(output.toString().contains("private-"));
        assertFalse(output.toString().contains("FORGED_MARKER"));
        assertFalse(output.toString().contains("1234567890123"));
    }

    /** HTTP错误非法或超限时不能解析或输出原文，也不能替换原认证异常。 */
    @Test
    void malformedAndOversizedResponsesStayUnknown(CapturedOutput output) {
        assertNull(ProviderFailureDiagnostics.response("private-response-marker"));
        assertNull(ProviderFailureDiagnostics.response("x".repeat(16385)));
        assertNull(ProviderFailureDiagnostics.response(null));
        ProviderFailureDiagnostics.feishu(LOGGER, "directory", 502, null);
        assertTrue(output.toString().contains("status=502 code=unknown"));
        assertFalse(output.toString().contains("private-response-marker"));
    }

    /** Feishu仅接受已知subject，不输出msg、授权URL、用户ID或未知权限。 */
    @Test
    void feishuPermissionsAreAnExplicitAllowlist(CapturedOutput output) {
        ProviderFailureDiagnostics.feishu(LOGGER, "directory", 400, ProviderFailureDiagnostics.response("""
                {"code":99991672,"msg":"private-message-marker","error":{
                "permission_violations":[{"subject":"contact:user.id:readonly"},
                {"subject":"syntactically.valid.unknown.scope"},{"subject":"private-user-marker"}],
                "helps":[{"url":"https://private-url-marker?token=private-token-marker"}]}}
                """));
        assertTrue(output.toString().contains("code=99991672"));
        assertTrue(output.toString().contains("contact:user.id:readonly"));
        assertFalse(output.toString().contains("syntactically.valid.unknown.scope"));
        assertFalse(output.toString().contains("private-"));
        assertFalse(output.toString().contains("https://"));
    }

    /** 权限数组只看前16项，超长正文不触发正则提取。 */
    @Test
    void permissionListsAndEmbeddedMessagesAreBounded(CapturedOutput output) {
        String values = "{\"subject\":\"unknown\"},".repeat(16)
                + "{\"subject\":\"contact:user.id:readonly\"}";
        ProviderFailureDiagnostics.feishu(LOGGER, "directory", 400, ProviderFailureDiagnostics.response(
                "{\"code\":99991672,\"error\":{\"permission_violations\":[" + values + "]}}"));
        ProviderFailureDiagnostics.dingtalk(LOGGER, "member", 200, ProviderFailureDiagnostics.response(
                "{\"errcode\":88,\"errmsg\":\"" + "x".repeat(4097) + "[subcode=60011]\"}"));
        assertFalse(output.toString().contains("contact:user.id:readonly"));
        assertFalse(output.toString().contains("subcode=60011"));
        assertTrue(output.toString().contains("requiredScopes=[]"));
    }
    /** 代码位置仅接受已知类和方法，正文、文件路径、非法元数据不能注入日志。 */
    @Test
    void bindingLocationRejectsUnknownMetadataAndNeverReadsMessage(CapturedOutput output) {
        var failure = new BadCredentialsException("private-password-token-marker");
        failure.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("private-secret-marker", "account", "private-file-marker", 12),
                new StackTraceElement("dev.macula.cloud.iam.service.identity.IdentityProviderUserBindingService",
                        "private-method-marker", "private-file-marker", 13),
                new StackTraceElement("dev.macula.cloud.iam.service.identity.IdentityProviderUserBindingService",
                        "requireClient", "private-file-marker", -1)});
        ProviderFailureDiagnostics.bindingFailure(LOGGER, "private-stage-marker", failure);
        assertTrue(output.toString().contains("stage=unknown"));
        assertTrue(output.toString().contains("site=unknown"));
        assertFalse(output.toString().contains("private-"));
    }
    /** 跳过统一denied帮助方法，只投影实际拒绝调用点。 */
    @Test
    void bindingLocationSkipsExceptionFactory(CapturedOutput output) {
        var failure = new BadCredentialsException("private-secret-marker");
        failure.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("dev.macula.cloud.iam.service.identity.IdentityProviderUserBindingService",
                        "denied", "private-file-marker", 350),
                new StackTraceElement("dev.macula.cloud.iam.service.identity.IdentityProviderUserBindingService",
                        "requireClient", "private-file-marker", 156)});
        ProviderFailureDiagnostics.bindingFailure(LOGGER, "binding_write", failure);
        assertTrue(output.toString().contains("site=IdentityProviderUserBindingService.requireClient:156"));
        assertFalse(output.toString().contains("private-"));
        assertFalse(output.toString().contains("denied"));
    }
}
