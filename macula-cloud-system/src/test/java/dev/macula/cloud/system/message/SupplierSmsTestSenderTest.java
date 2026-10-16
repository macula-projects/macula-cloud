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
package dev.macula.cloud.system.message;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.dysmsapi20170525.models.SendSmsResponseBody;
import com.aliyun.teaopenapi.models.Config;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.sms.v20210111.SmsClient;
import com.tencentcloudapi.sms.v20210111.models.SendStatus;
import com.sun.net.httpserver.HttpServer;
import dev.macula.cloud.system.enums.MessageTemplateType;
import dev.macula.cloud.system.pojo.entity.SysMessageProvider;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 验证阿里与腾讯 SDK 的模板参数映射和供应商拒绝响应。
 *
 * @author Rain
 * @since 2026-10-13
 */
class SupplierSmsTestSenderTest {
    @Test
    void aliyunSdkSendsToLocalServiceAndHandlesRejection() throws Exception {
        AtomicReference<String> request = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            request.set(exchange.getRequestURI() + " " +
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"Code\":\"OK\",\"Message\":\"OK\",\"RequestId\":\"fixture\"}"
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            AliyunSmsTestSender sender = new AliyunSmsTestSender() {
                @Override Client createClient(Config config) throws Exception {
                    return new Client(config.setProtocol("HTTP")
                        .setEndpoint("127.0.0.1:" + server.getAddress().getPort())
                        .setConnectTimeout(2000).setReadTimeout(2000));
                }
            };
            sender.send(provider(), "13800000000", "TPL-1", Map.of("code", "123456"));
            assertNotNull(request.get());
            assertTrue(request.get().contains("13800000000"));
            assertTrue(request.get().contains("TPL-1"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void tencentSdkSendsToLocalService() throws Exception {
        AtomicReference<String> request = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"Response\":{\"SendStatusSet\":[{\"Code\":\"Ok\",\"Message\":\"success\"}],\"RequestId\":\"fixture\"}}"
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            TencentSmsTestSender sender = new TencentSmsTestSender() {
                @Override SmsClient createClient(Credential credential, String region) {
                    HttpProfile http = new HttpProfile();
                    http.setProtocol(HttpProfile.REQ_HTTP);
                    http.setEndpoint("127.0.0.1:" + server.getAddress().getPort());
                    return new SmsClient(credential, region, new ClientProfile(ClientProfile.SIGN_TC3_256, http));
                }
            };
            SysMessageProvider provider = provider();
            provider.setConfigJson("{\"smsSdkAppId\":\"app-1\"}");
            sender.send(provider, "13800000000", "TPL-1", MessageTemplateType.SMS_VERIFY,
                Map.of("code", "123456"));
            assertNotNull(request.get());
            assertTrue(request.get().contains("13800000000"));
            assertTrue(request.get().contains("TPL-1"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void aliyunMapsTemplateAndRejectsNonOkResponse() throws Exception {
        Client client = mock(Client.class);
        AliyunSmsTestSender sender = new AliyunSmsTestSender() {
            @Override Client createClient(Config config) { return client; }
        };
        SysMessageProvider provider = provider();
        when(client.sendSms(any(SendSmsRequest.class))).thenReturn(new SendSmsResponse()
            .setBody(new SendSmsResponseBody().setCode("OK")));
        assertDoesNotThrow(() -> sender.send(provider, "13800000000", "TPL-1", Map.of("code", "123456")));
        var request = org.mockito.ArgumentCaptor.forClass(SendSmsRequest.class);
        verify(client).sendSms(request.capture());
        assertEquals("13800000000", request.getValue().getPhoneNumbers());
        assertEquals("TPL-1", request.getValue().getTemplateCode());
        assertTrue(request.getValue().getTemplateParam().contains("123456"));
        when(client.sendSms(any(SendSmsRequest.class))).thenReturn(new SendSmsResponse()
            .setBody(new SendSmsResponseBody().setCode("InvalidParameter")));
        assertThrows(ResponseStatusException.class,
            () -> sender.send(provider, "13800000000", "TPL-1", Map.of("code", "123456")));
    }

    @Test
    void tencentOrdersVariablesAndRejectsFailedStatus() throws Exception {
        SmsClient client = mock(SmsClient.class);
        TencentSmsTestSender sender = new TencentSmsTestSender() {
            @Override SmsClient createClient(Credential credential, String region) { return client; }
        };
        SysMessageProvider provider = provider();
        provider.setConfigJson("{\"smsSdkAppId\":\"app-1\"}");
        SendStatus accepted = new SendStatus();
        accepted.setCode("Ok");
        var response = new com.tencentcloudapi.sms.v20210111.models.SendSmsResponse();
        response.setSendStatusSet(new SendStatus[] {accepted});
        when(client.SendSms(any(com.tencentcloudapi.sms.v20210111.models.SendSmsRequest.class)))
            .thenReturn(response);
        assertDoesNotThrow(() -> sender.send(provider, "13800000000", "TPL-1",
            MessageTemplateType.SMS_VERIFY, Map.of("code", "123456")));
        var request = org.mockito.ArgumentCaptor.forClass(
            com.tencentcloudapi.sms.v20210111.models.SendSmsRequest.class);
        verify(client).SendSms(request.capture());
        assertArrayEquals(new String[] {"13800000000"}, request.getValue().getPhoneNumberSet());
        assertArrayEquals(new String[] {"123456"}, request.getValue().getTemplateParamSet());
        assertEquals("app-1", request.getValue().getSmsSdkAppId());
        SendStatus rejected = new SendStatus();
        rejected.setCode("Failed");
        response.setSendStatusSet(new SendStatus[] {rejected});
        assertThrows(ResponseStatusException.class, () -> sender.send(provider, "13800000000", "TPL-1",
            MessageTemplateType.SMS_VERIFY, Map.of("code", "123456")));
    }

    private static SysMessageProvider provider() {
        SysMessageProvider provider = new SysMessageProvider();
        provider.setAccount("fixture-account");
        provider.setCredential("fixture-secret");
        provider.setSender("fixture-sign");
        return provider;
    }
}
