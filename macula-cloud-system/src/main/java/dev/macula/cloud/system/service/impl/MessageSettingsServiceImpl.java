/*
 * Copyright (c) 2026 Macula
 *   macula.dev, China
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package dev.macula.cloud.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.macula.boot.starter.security.utils.SecurityUtils;
import dev.macula.cloud.system.enums.MessageProviderType;
import dev.macula.cloud.system.enums.MessageTemplateType;
import dev.macula.cloud.system.form.*;
import dev.macula.cloud.system.mapper.*;
import dev.macula.cloud.system.message.*;
import dev.macula.cloud.system.pojo.entity.*;
import dev.macula.cloud.system.service.MessageSettingsService;
import dev.macula.cloud.system.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理每个渠道唯一的平台级配置、固定场景模板及真实测试投递。
 *
 * @author Rain
 * @since 2026-10-17
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageSettingsServiceImpl implements MessageSettingsService {
    private static final Set<String> LANGUAGES = Set.of("zh-CN", "en-US");
    private final JsonMapper json = JsonMapper.builder().build();
    private final SysMessageProviderMapper providerMapper;
    private final SysMessageTemplateMapper templateMapper;
    private final TemplateRenderer renderer;
    private final MailHtmlSanitizer sanitizer;
    private final MailTestSender mailSender;
    private final AliyunSmsTestSender aliyunSender;
    private final TencentSmsTestSender tencentSender;
    private final StringRedisTemplate redisTemplate;
    private final MessageAuditHasher auditHasher;

    @Override
    public MessageProviderVO provider(String channel) {
        checkChannel(channel);
        SysMessageProvider entity = currentProvider(channel);
        return entity == null ? new MessageProviderVO(channel, null, null, null, null,
            null, null, null, null, false, false) : toProviderVO(entity);
    }

    @Override
    @Transactional
    public MessageProviderVO saveProvider(String channel, MessageProviderForm form) {
        checkChannel(channel);
        MessageProviderType type;
        try { type = MessageProviderType.require(channel, form.getProviderType()); }
        catch (IllegalArgumentException ex) { bad(ex.getMessage()); return null; }
        SysMessageProvider entity = currentProvider(channel);
        boolean creating = entity == null;
        boolean providerChanged = !creating && !Objects.equals(entity.getProviderType(), type.code());
        if (creating) entity = new SysMessageProvider();
        configureProvider(entity, channel, type, form);
        if (StringUtils.hasText(form.getCredential())) entity.setCredential(form.getCredential());
        if (providerChanged && !StringUtils.hasText(form.getCredential())) bad("切换服务商时必须重新输入凭据");
        if (!StringUtils.hasText(entity.getCredential())) bad("服务凭据不能为空");
        if (creating) providerMapper.insert(entity); else providerMapper.updateById(entity);
        return toProviderVO(entity);
    }

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
            int port = type.host() == null ? Objects.requireNonNullElse(form.getPort(), 0) : type.port();
            String mode = type.host() == null ? form.getSecurityMode() : type.securityMode();
            if (!StringUtils.hasText(endpoint) || port < 1 || port > 65535 || !StringUtils.hasText(mode) ||
                !Set.of("NONE", "SSL").contains(mode)) {
                bad("SMTP 主机、端口或安全模式无效");
            }
            if (!StringUtils.hasText(form.getAccount()) || !StringUtils.hasText(form.getSender())) {
                bad("邮件账号与发件人邮箱不能为空");
            }
            entity.setEndpoint(endpoint);
            entity.setAccount(form.getAccount());
            entity.setSender(form.getSender());
            entity.setConfigJson(writeJson(Map.of("port", port, "securityMode", mode)));
        } else {
            if (!StringUtils.hasText(form.getAccount()) || !StringUtils.hasText(form.getSender())) {
                bad("短信访问标识与签名不能为空");
            }
            if ("TENCENT".equals(type.code()) && (!StringUtils.hasText(form.getRegionCode()) ||
                !StringUtils.hasText(form.getSmsSdkAppId()))) bad("腾讯短信地域与应用 ID 不能为空");
            entity.setEndpoint(null);
            entity.setAccount(form.getAccount());
            entity.setSender(form.getSender());
            entity.setRegionCode("TENCENT".equals(type.code()) ? form.getRegionCode() : null);
            if ("TENCENT".equals(type.code())) {
                entity.setConfigJson(writeJson(Map.of("smsSdkAppId", form.getSmsSdkAppId())));
            }
        }
    }

    @Override
    public List<MessageTemplateVO> templates(String channel, String providerType, String languageCode) {
        checkChannel(channel);
        checkLanguage(languageCode);
        if (StringUtils.hasText(providerType)) requireProviderType(channel, providerType);
        return Arrays.stream(MessageTemplateType.values()).filter(type -> type.channel().equals(channel))
            .map(type -> effective(type, providerType, languageCode)).toList();
    }

    @Override
    @Transactional
    public MessageTemplateVO saveTemplate(String channel, String scene, MessageTemplateForm form) {
        MessageTemplateType type = requireType(channel, scene);
        String language = StringUtils.hasText(form.getLanguageCode()) ? form.getLanguageCode() : "zh-CN";
        checkLanguage(language);
        String provider = StringUtils.hasText(form.getProviderType()) ? form.getProviderType() : "";
        if ("SMS".equals(channel)) {
            requireProviderType(channel, provider);
            if (!StringUtils.hasText(form.getProviderTemplateCode())) bad("短信模板编码不能为空");
        } else {
            provider = "";
            if (form.isEnabled() && (!StringUtils.hasText(form.getSenderName()) ||
                !StringUtils.hasText(form.getTitle()) || !StringUtils.hasText(form.getContent()))) {
                bad("自定义邮件发件人、标题和内容不能为空");
            }
            if (form.isEnabled()) renderer.render(type, form.getContent(), samples(type));
        }
        SysMessageTemplate entity = lookup(type, provider, language);
        if (entity == null) entity = new SysMessageTemplate();
        entity.setChannel(channel);
        entity.setTemplateKey(type.name());
        entity.setProviderType(provider);
        entity.setLanguageCode(language);
        entity.setSenderName(form.getSenderName());
        entity.setTitle(form.getTitle());
        entity.setContent(StringUtils.hasText(form.getContent()) ? form.getContent() : type.content());
        entity.setProviderTemplateCode(form.getProviderTemplateCode());
        entity.setEnabled(form.isEnabled());
        if (entity.getId() == null) templateMapper.insert(entity); else templateMapper.updateById(entity);
        return toTemplateVO(type, entity);
    }

    @Override
    public String preview(String scene, Map<String, String> variables) {
        MessageTemplateType type = requireType("MAIL", scene);
        MessageTemplateVO template = effective(type, "", "zh-CN");
        Map<String, String> values = variables == null || variables.isEmpty() ? samples(type) : variables;
        validateVariables(values);
        return sanitizer.sanitize(renderer.render(type, template.getContent(), values));
    }

    @Override
    public void test(MessageTestForm form) {
        MessageTemplateType type;
        try { type = MessageTemplateType.valueOf(form.getScene()); }
        catch (RuntimeException ex) { bad("模板类型不支持"); return; }
        SysMessageProvider provider = null;
        try {
            provider = currentProvider(type.channel());
            if (provider == null || !Boolean.TRUE.equals(provider.getEnabled())) bad("渠道未启用");
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
                if (!form.getTarget().matches("^\\+?[0-9]{6,20}$")) bad("手机号格式无效");
                renderer.render(type, template.getContent(), values);
                switch (provider.getProviderType()) {
                    case "ALIYUN" -> aliyunSender.send(provider, form.getTarget(),
                        template.getProviderTemplateCode(), values);
                    case "TENCENT" -> tencentSender.send(provider, form.getTarget(),
                        template.getProviderTemplateCode(), type, values);
                    default -> bad("短信服务类型不支持");
                }
            }
            audit(type, provider, form.getTarget(), "SUBMITTED");
        } catch (RuntimeException ex) {
            audit(type, provider, form.getTarget(), "FAILED");
            throw ex;
        }
    }

    private void audit(MessageTemplateType type, SysMessageProvider provider, String target, String result) {
        log.info("消息发送审计 caller={} channel={} scene={} provider={} result={} targetHash={}",
            safe(SecurityUtils.getCurrentUser()), type.channel(), type.name(),
            provider == null ? "UNRESOLVED" : safe(provider.getProviderType()), result,
            auditHasher.hash(target));
    }

    private SysMessageProvider currentProvider(String channel) {
        List<SysMessageProvider> rows = providerMapper.selectList(new LambdaQueryWrapper<SysMessageProvider>()
            .eq(SysMessageProvider::getChannel, channel));
        if (rows.size() > 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "渠道配置不唯一");
        return rows.isEmpty() ? null : rows.get(0);
    }

    private SysMessageTemplate lookup(MessageTemplateType type, String provider, String language) {
        return templateMapper.selectOne(new LambdaQueryWrapper<SysMessageTemplate>()
            .eq(SysMessageTemplate::getChannel, type.channel()).eq(SysMessageTemplate::getTemplateKey, type.name())
            .eq(SysMessageTemplate::getProviderType, provider).eq(SysMessageTemplate::getLanguageCode, language));
    }

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

    private MessageTemplateVO toTemplateVO(MessageTemplateType type, SysMessageTemplate entity) {
        return new MessageTemplateVO(type.name(), type.channel(), entity.getProviderType(), entity.getLanguageCode(),
            entity.getSenderName(),
            entity.getTitle(), entity.getContent(), entity.getProviderTemplateCode(),
            Boolean.TRUE.equals(entity.getEnabled()), true, type.variables());
    }

    private MessageProviderVO toProviderVO(SysMessageProvider entity) {
        Map<?, ?> options = StringUtils.hasText(entity.getConfigJson())
            ? json.readValue(entity.getConfigJson(), Map.class) : Map.of();
        Integer port = options.get("port") instanceof Number value ? value.intValue() : null;
        return new MessageProviderVO(entity.getChannel(), entity.getProviderType(),
            entity.getEndpoint(), port, (String)options.get("securityMode"), entity.getAccount(),
            entity.getRegionCode(), entity.getSender(), (String)options.get("smsSdkAppId"),
            Boolean.TRUE.equals(entity.getEnabled()), StringUtils.hasText(entity.getCredential()));
    }

    private MessageTemplateType requireType(String channel, String scene) {
        checkChannel(channel);
        try {
            MessageTemplateType type = MessageTemplateType.valueOf(scene);
            if (!channel.equals(type.channel())) bad("模板与通道不匹配");
            return type;
        } catch (IllegalArgumentException ex) { bad("模板类型不支持"); throw ex; }
    }

    private void requireProviderType(String channel, String value) {
        try { MessageProviderType.require(channel, value); }
        catch (IllegalArgumentException ex) { bad(ex.getMessage()); }
    }

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

    private String writeJson(Object value) {
        try { return json.writeValueAsString(value); }
        catch (RuntimeException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "服务参数无效"); }
    }

    private void validateVariables(Map<String, String> variables) {
        if (variables.size() > 8 || variables.entrySet().stream().anyMatch(entry ->
            entry.getKey() == null || entry.getKey().length() > 64 || entry.getValue() == null ||
                entry.getValue().length() > 1000)) {
            bad("模板变量数量或长度无效");
        }
    }

    private void checkChannel(String channel) { if (!Set.of("MAIL", "SMS").contains(channel)) bad("消息通道无效"); }
    private void checkLanguage(String value) { if (!LANGUAGES.contains(value)) bad("语言编码无效"); }
    private String safe(String value) { return value == null ? "unknown" : value.replaceAll("[\\p{Cntrl}]", "_"); }
    private static void bad(String reason) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason); }
}
