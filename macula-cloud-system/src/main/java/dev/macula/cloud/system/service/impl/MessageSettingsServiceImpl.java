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
package dev.macula.cloud.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.macula.boot.starter.security.utils.SecurityUtils;
import dev.macula.cloud.system.enums.MessageProviderType;
import dev.macula.cloud.system.enums.MessageTemplateType;
import dev.macula.cloud.system.form.MessageProviderForm;
import dev.macula.cloud.system.form.MessageTemplateForm;
import dev.macula.cloud.system.form.MessageTestForm;
import dev.macula.cloud.system.mapper.SysMessageProviderMapper;
import dev.macula.cloud.system.mapper.SysMessageTemplateMapper;
import dev.macula.cloud.system.message.MailHtmlSanitizer;
import dev.macula.cloud.system.message.MessageAuditHasher;
import dev.macula.cloud.system.message.TemplateRenderer;
import dev.macula.cloud.system.message.mail.MailSender;
import dev.macula.cloud.system.message.sms.SmsSender;
import dev.macula.cloud.system.message.sms.support.CustomSmsOptions;
import dev.macula.cloud.system.enums.MailSecurityMode;
import dev.macula.cloud.system.pojo.entity.SysMessageProvider;
import dev.macula.cloud.system.pojo.entity.SysMessageTemplate;
import dev.macula.cloud.system.service.MessageSettingsService;
import dev.macula.cloud.system.vo.MessageProviderVO;
import dev.macula.cloud.system.vo.MessageTemplateVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理每个渠道唯一的平台级配置、固定场景模板及真实测试投递。
 *
 * @author Rain
 * @since 2026-10-17
 */
@Service
@Slf4j
public class MessageSettingsServiceImpl implements MessageSettingsService {
    private static final Set<String> LANGUAGES = Set.of("zh-CN", "en-US");
    private final JsonMapper json = JsonMapper.builder().build();
    private final SysMessageProviderMapper providerMapper;
    private final SysMessageTemplateMapper templateMapper;
    private final TemplateRenderer renderer;
    private final MailHtmlSanitizer sanitizer;
    private final MailSender mailSender;
    private final Map<String, SmsSender> smsSenders;
    private final StringRedisTemplate redisTemplate;
    private final MessageAuditHasher auditHasher;

    /** 显式注册短信策略，重复供应商不能由 Bean 顺序决定投递目标。 */
    public MessageSettingsServiceImpl(SysMessageProviderMapper providerMapper, SysMessageTemplateMapper templateMapper,
            TemplateRenderer renderer, MailHtmlSanitizer sanitizer, MailSender mailSender,
            List<SmsSender> adapters, StringRedisTemplate redisTemplate, MessageAuditHasher auditHasher) {
        this.providerMapper = providerMapper;
        this.templateMapper = templateMapper;
        this.renderer = renderer;
        this.sanitizer = sanitizer;
        this.mailSender = mailSender;
        this.redisTemplate = redisTemplate;
        this.auditHasher = auditHasher;
        Map<String, SmsSender> registry = new HashMap<>();
        for (SmsSender adapter : adapters) {
            if (registry.putIfAbsent(adapter.providerType(), adapter) != null) {
                throw new IllegalStateException("重复短信供应商");
            }
        }
        smsSenders = Map.copyOf(registry);
    }


    /** 返回不含凭据的渠道配置。 */
    @Override
    public MessageProviderVO provider(String channel) {
        checkChannel(channel);
        SysMessageProvider entity = currentProvider(channel);
        return entity == null ? new MessageProviderVO(channel, null, null, null, null,
            null, null, null, null, false, false, false) : toProviderVO(entity);
    }

    /** 校验并保存唯一渠道配置，空白凭据保留。 */
    @Override
    @Transactional
    public MessageProviderVO saveProvider(String channel, MessageProviderForm form) {
        checkChannel(channel);
        MessageProviderType type;
        try {
            type = MessageProviderType.require(channel, form.getProviderType());
        } catch (IllegalArgumentException ex) {
            bad(ex.getMessage());
            return null;
        }
        SysMessageProvider entity = currentProvider(channel);
        boolean creating = entity == null;
        boolean providerChanged = !creating && !Objects.equals(entity.getProviderType(), type.code());
        if (creating) {
            entity = new SysMessageProvider();
        }
        validateApplicableFields(channel, type, form);
        boolean customSms = type == MessageProviderType.CUSTOM_HTTP_SMS;
        String replacement;
        try {
            replacement = customSms
                    ? (form.getAuthHeaders() == null || form.getAuthHeaders().isEmpty() ? null
                            : CustomSmsOptions.encodeHeaders(form.getAuthHeaders()))
                    : form.getCredential();
        } catch (IllegalArgumentException ex) {
            bad(ex.getMessage());
            return null;
        }
        // 必须先验证替换凭据，再改写类型，避免新供应商继承不同用途的旧密钥。
        if (providerChanged && !StringUtils.hasText(replacement)) {
            bad("切换服务商时必须重新输入凭据");
        }
        if (!StringUtils.hasText(replacement) && !StringUtils.hasText(entity.getCredential())) {
            bad("服务凭据不能为空");
        }
        configureProvider(entity, channel, type, form);
        if (StringUtils.hasText(replacement)) {
            entity.setCredential(replacement);
        }
        if (customSms) {
            CustomSmsOptions.parse(entity.getEndpoint(), entity.getCredential());
        }
        if (creating) {
            providerMapper.insert(entity);
        } else {
            providerMapper.updateById(entity);
        }
        return toProviderVO(entity);
    }

    /** 拒绝不适用于当前供应商的实质配置；空字段兼容现有表单模型。 */
    private void validateApplicableFields(String channel, MessageProviderType type, MessageProviderForm form) {
        boolean customSms = type == MessageProviderType.CUSTOM_HTTP_SMS;
        boolean mail = "MAIL".equals(channel);
        if ((!customSms && form.getAuthHeaders() != null && !form.getAuthHeaders().isEmpty())
                || (customSms && (StringUtils.hasText(form.getAccount()) || StringUtils.hasText(form.getCredential())))
                || (!mail && (form.getPort() != null || StringUtils.hasText(form.getSecurityMode())))
                || (!mail && !customSms && StringUtils.hasText(form.getEndpoint()))
                || ((mail || type != MessageProviderType.TENCENT_SMS)
                    && (StringUtils.hasText(form.getRegionCode()) || StringUtils.hasText(form.getSmsSdkAppId())))) {
            bad("服务配置包含不适用字段");
        }
    }

    /** 按供应商配置字段，清理不属于当前渠道的持久化选项。 */
    private void configureProvider(SysMessageProvider entity, String channel, MessageProviderType type,
        MessageProviderForm form) {
        entity.setChannel(channel);
        entity.setProviderType(type.code());
        entity.setName(channel);
        entity.setEnabled(Boolean.TRUE.equals(form.getEnabled()));
        entity.setRegionCode(null);
        entity.setConfigJson(null);
        if ("MAIL".equals(channel)) {
            String endpoint = type.host() == null ? form.getEndpoint() : type.host();
            int port = type.host() == null ? Objects.requireNonNullElse(form.getPort(), type.port()) : type.port();
            String mode = type.host() == null
                    ? (StringUtils.hasText(form.getSecurityMode()) ? form.getSecurityMode() : type.securityMode())
                    : type.securityMode();
            if (!StringUtils.hasText(endpoint) || port < 1 || port > 65535 || !StringUtils.hasText(mode) ||
                !MailSecurityMode.supports(mode)) {
                bad("SMTP 主机、端口或安全模式无效");
            }
            if (!StringUtils.hasText(form.getAccount()) || !StringUtils.hasText(form.getSender())) {
                bad("邮件账号与发件人邮箱不能为空");
            }
            entity.setEndpoint(endpoint);
            entity.setAccount(form.getAccount());
            entity.setSender(form.getSender());
            entity.setConfigJson(writeJson(Map.of("port", port, "securityMode", mode)));
        } else if (type == MessageProviderType.CUSTOM_HTTP_SMS) {
            try {
                entity.setEndpoint(CustomSmsOptions.endpoint(form.getEndpoint()).toString());
            } catch (IllegalArgumentException ex) {
                bad(ex.getMessage());
            }
            if (!StringUtils.hasText(form.getSender())) {
                bad("短信签名不能为空");
            }
            entity.setAccount(null);
            entity.setSender(form.getSender());
        } else {
            if (!StringUtils.hasText(form.getAccount()) || !StringUtils.hasText(form.getSender())) {
                bad("短信访问标识与签名不能为空");
            }
            if ("TENCENT".equals(type.code()) && (!StringUtils.hasText(form.getRegionCode()) ||
                !StringUtils.hasText(form.getSmsSdkAppId()))) {
                bad("腾讯短信地域与应用 ID 不能为空");
            }
            entity.setEndpoint(null);
            entity.setAccount(form.getAccount());
            entity.setSender(form.getSender());
            entity.setRegionCode("TENCENT".equals(type.code()) ? form.getRegionCode() : null);
            if ("TENCENT".equals(type.code())) {
                entity.setConfigJson(writeJson(Map.of("smsSdkAppId", form.getSmsSdkAppId())));
            }
        }
    }

    /** 列出固定场景及当前供应商的模板覆盖。 */
    @Override
    public List<MessageTemplateVO> templates(String channel, String providerType, String languageCode) {
        checkChannel(channel);
        checkLanguage(languageCode);
        if (StringUtils.hasText(providerType)) {
            requireProviderType(channel, providerType);
        }
        return Arrays.stream(MessageTemplateType.values()).filter(type -> type.channel().equals(channel))
            .map(type -> effective(type, providerType, languageCode)).toList();
    }

    /** 保存场景模板，不允许浏览器引入未知供应商。 */
    @Override
    @Transactional
    public MessageTemplateVO saveTemplate(String channel, String scene, MessageTemplateForm form) {
        MessageTemplateType type = requireType(channel, scene);
        String language = StringUtils.hasText(form.getLanguageCode()) ? form.getLanguageCode() : "zh-CN";
        checkLanguage(language);
        String provider = StringUtils.hasText(form.getProviderType()) ? form.getProviderType() : "";
        if ("SMS".equals(channel)) {
            requireProviderType(channel, provider);
            if (!StringUtils.hasText(form.getProviderTemplateCode())) {
                bad("短信模板编码不能为空");
            }
        } else {
            provider = "";
            if (form.isEnabled() && (!StringUtils.hasText(form.getSenderName()) ||
                !StringUtils.hasText(form.getTitle()) || !StringUtils.hasText(form.getContent()))) {
                bad("自定义邮件发件人、标题和内容不能为空");
            }
            if (form.isEnabled()) {
                renderer.render(type, form.getContent(), samples(type));
            }
        }
        SysMessageTemplate entity = lookup(type, provider, language);
        if (entity == null) {
            entity = new SysMessageTemplate();
        }
        entity.setChannel(channel);
        entity.setTemplateKey(type.name());
        entity.setProviderType(provider);
        entity.setLanguageCode(language);
        entity.setSenderName(form.getSenderName());
        entity.setTitle(form.getTitle());
        entity.setContent(StringUtils.hasText(form.getContent()) ? form.getContent() : type.content());
        entity.setProviderTemplateCode(form.getProviderTemplateCode());
        entity.setEnabled(form.isEnabled());
        if (entity.getId() == null) {
            templateMapper.insert(entity);
        } else {
            templateMapper.updateById(entity);
        }
        return toTemplateVO(type, entity);
    }

    /** 校验变量后渲染并清理邮件预览。 */
    @Override
    public String preview(String scene, Map<String, String> variables) {
        MessageTemplateType type = requireType("MAIL", scene);
        MessageTemplateVO template = effective(type, "", "zh-CN");
        Map<String, String> values = variables == null || variables.isEmpty() ? samples(type) : variables;
        validateVariables(values);
        return sanitizer.sanitize(renderer.render(type, template.getContent(), values));
    }

    /** 按已保存渠道执行有频率限制的测试投递。 */
    @Override
    public void test(MessageTestForm form) {
        MessageTemplateType type;
        try {
            type = MessageTemplateType.valueOf(form.getScene());
        } catch (RuntimeException ex) {
            bad("模板类型不支持");
            return;
        }
        SysMessageProvider provider = null;
        try {
            provider = currentProvider(type.channel());
            if (provider == null || !Boolean.TRUE.equals(provider.getEnabled())) {
                bad("渠道未启用");
            }
            String language = StringUtils.hasText(form.getLanguageCode()) ? form.getLanguageCode() : "zh-CN";
            checkLanguage(language);
            MessageTemplateVO template = effective(type,
                "SMS".equals(type.channel()) ? provider.getProviderType() : "", language);
            if ("SMS".equals(type.channel()) && (!template.isEnabled() ||
                !StringUtils.hasText(template.getProviderTemplateCode()))) {
                bad("短信模板未启用或配置不完整");
            }
            Map<String, String> values = form.getVariables() == null ? Map.of() : form.getVariables();
            validateVariables(values);
            String key = "system:message:test:" + SecurityUtils.getCurrentUser() + ":" + type.name();
            if (!Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(30)))) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "测试发送过于频繁");
            }
            if ("MAIL".equals(type.channel())) {
                mailSender.send(provider, form.getTarget(), template.getSenderName(), template.getTitle(),
                    sanitizer.sanitize(renderer.render(type, template.getContent(), values)));
            } else {
                if (!form.getTarget().matches("^\\+?[0-9]{6,20}$")) {
                    bad("手机号格式无效");
                }
                renderer.render(type, template.getContent(), values);
                SmsSender sender = smsSenders.get(provider.getProviderType());
                if (sender == null) {
                    bad("短信服务类型不支持");
                }
                sender.send(provider, form.getTarget(), template.getProviderTemplateCode(), type, language, values);
            }
            audit(type, provider, form.getTarget(), "SUBMITTED");
        } catch (RuntimeException ex) {
            audit(type, provider, form.getTarget(), "FAILED");
            throw ex;
        }
    }

    /** 只记录独立 HMAC 摘要和稳定状态，不包含投递内容。 */
    private void audit(MessageTemplateType type, SysMessageProvider provider, String target, String result) {
        log.info("消息发送审计 caller={} channel={} scene={} provider={} result={} targetHash={}",
            safe(SecurityUtils.getCurrentUser()), type.channel(), type.name(),
            provider == null ? "UNRESOLVED" : safe(provider.getProviderType()), result,
            auditHasher.hash(target));
    }

    /** 读取唯一平台渠道，重复配置时拒绝选择。 */
    private SysMessageProvider currentProvider(String channel) {
        List<SysMessageProvider> rows = providerMapper.selectList(new LambdaQueryWrapper<SysMessageProvider>()
            .eq(SysMessageProvider::getChannel, channel));
        if (rows.size() > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "渠道配置不唯一");
        }
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 按场景、供应商及语言读取模板覆盖。 */
    private SysMessageTemplate lookup(MessageTemplateType type, String provider, String language) {
        return templateMapper.selectOne(new LambdaQueryWrapper<SysMessageTemplate>()
            .eq(SysMessageTemplate::getChannel, type.channel()).eq(SysMessageTemplate::getTemplateKey, type.name())
            .eq(SysMessageTemplate::getProviderType, provider).eq(SysMessageTemplate::getLanguageCode, language));
    }

    /** 合并默认模板与启用的覆盖配置。 */
    private MessageTemplateVO effective(MessageTemplateType type, String provider, String language) {
        String key = provider == null ? "" : provider;
        SysMessageTemplate override = lookup(type, key, language);
        if ("MAIL".equals(type.channel()) && (override == null || !Boolean.TRUE.equals(override.getEnabled()))) {
            override = null;
        }
        return override == null ? new MessageTemplateVO(type.name(), type.channel(), key, language, "Macula Cloud",
            type.title(), type.content(), null, "MAIL".equals(type.channel()), false, type.variables())
            : toTemplateVO(type, override);
    }

    /** 映射模板展示信息，不包含渠道凭据。 */
    private MessageTemplateVO toTemplateVO(MessageTemplateType type, SysMessageTemplate entity) {
        return new MessageTemplateVO(type.name(), type.channel(), entity.getProviderType(), entity.getLanguageCode(),
            entity.getSenderName(),
            entity.getTitle(), entity.getContent(), entity.getProviderTemplateCode(),
            Boolean.TRUE.equals(entity.getEnabled()), true, type.variables());
    }

    /** 只返回凭据配置状态，不返回敏感值。 */
    private MessageProviderVO toProviderVO(SysMessageProvider entity) {
        Map<?, ?> options = StringUtils.hasText(entity.getConfigJson())
            ? json.readValue(entity.getConfigJson(), Map.class) : Map.of();
        Integer port = options.get("port") instanceof Number value ? value.intValue() : null;
        return new MessageProviderVO(entity.getChannel(), entity.getProviderType(),
            entity.getEndpoint(), port, (String)options.get("securityMode"), entity.getAccount(),
            entity.getRegionCode(), entity.getSender(), (String)options.get("smsSdkAppId"),
            Boolean.TRUE.equals(entity.getEnabled()), StringUtils.hasText(entity.getCredential()),
            CustomSmsOptions.PROVIDER_TYPE.equals(entity.getProviderType())
                && StringUtils.hasText(entity.getCredential()));
    }

    /** 确保场景与渠道一致。 */
    private MessageTemplateType requireType(String channel, String scene) {
        checkChannel(channel);
        try {
            MessageTemplateType type = MessageTemplateType.valueOf(scene);
            if (!channel.equals(type.channel())) {
                bad("模板与通道不匹配");
            }
            return type;
        } catch (IllegalArgumentException ex) {
            bad("模板类型不支持");
            throw ex;
        }
    }

    /** 拒绝当前渠道不支持的供应商。 */
    private void requireProviderType(String channel, String value) {
        try {
            MessageProviderType.require(channel, value);
        } catch (IllegalArgumentException ex) {
            bad(ex.getMessage());
        }
    }

    /** 生成不含真实账号的预览变量。 */
    private Map<String, String> samples(MessageTemplateType type) {
        return type.variables().stream().collect(Collectors.toMap(key -> key,
            key -> switch (key) {
                case "code" -> "123456";
                case "expire_days" -> "7";
                case "expire_time" -> "5";
                case "user_email" -> "user@example.com";
                case "now_time" -> "2026-10-18 10:00";
                default -> "Macula Cloud";
            }));
    }

    /** 序列化受控配置，失败转换为安全错误。 */
    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "服务参数无效");
        }
    }

    /** 限制模板变量数量及长度。 */
    private void validateVariables(Map<String, String> variables) {
        if (variables.size() > 8 || variables.entrySet().stream().anyMatch(entry ->
            entry.getKey() == null || entry.getKey().length() > 64 || entry.getValue() == null ||
                entry.getValue().length() > 1000)) {
            bad("模板变量数量或长度无效");
        }
    }

    /** 限制可管理的消息渠道。 */
    private void checkChannel(String channel) {
        if (!Set.of("MAIL", "SMS").contains(channel)) {
            bad("消息通道无效");
        }
    }
    /** 限制模板语言编码。 */
    private void checkLanguage(String value) {
        if (!LANGUAGES.contains(value)) {
            bad("语言编码无效");
        }
    }
    /** 清理审计标识中的控制字符。 */
    private String safe(String value) { return value == null ? "unknown" : value.replaceAll("[\\p{Cntrl}]", "_"); }
    /** 统一拒绝无效配置，不返回供应商原始响应。 */
    private static void bad(String reason) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason); }
}
