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
import dev.macula.cloud.system.enums.MessageTemplateType;
import dev.macula.cloud.system.form.MessageProviderForm;
import dev.macula.cloud.system.form.MessageTemplateForm;
import dev.macula.cloud.system.form.MessageTestForm;
import dev.macula.cloud.system.mapper.SysMessageProviderMapper;
import dev.macula.cloud.system.mapper.SysMessageTemplateMapper;
import dev.macula.cloud.system.message.AliyunSmsTestSender;
import dev.macula.cloud.system.message.CustomSmsTestSender;
import dev.macula.cloud.system.message.MailTestSender;
import dev.macula.cloud.system.message.TemplateRenderer;
import dev.macula.cloud.system.message.TencentSmsTestSender;
import dev.macula.cloud.system.pojo.entity.SysMessageProvider;
import dev.macula.cloud.system.pojo.entity.SysMessageTemplate;
import dev.macula.cloud.system.service.MessageSettingsService;
import dev.macula.cloud.system.vo.MessageProviderVO;
import dev.macula.cloud.system.vo.MessageTemplateVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 管理供应商和模板覆盖，并提交管理员发起的真实测试消息。
 *
 * @author Rain
 * @since 2026-10-13
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageSettingsServiceImpl implements MessageSettingsService {
    private static final Set<String> MAIL = Set.of("CUSTOM", "EXCHANGE", "ALIYUN", "TENCENT", "NETEASE");
    private static final Set<String> SMS = Set.of("CUSTOM", "ALIYUN", "TENCENT");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]");
    private final JsonMapper json = JsonMapper.builder().build();
    private final SysMessageProviderMapper providerMapper;
    private final SysMessageTemplateMapper templateMapper;
    private final TemplateRenderer renderer;
    private final MailTestSender mailSender;
    private final AliyunSmsTestSender aliyunSender;
    private final TencentSmsTestSender tencentSender;
    private final CustomSmsTestSender customSender;
    private final StringRedisTemplate redisTemplate;

    @Override
    public List<MessageProviderVO> providers(String channel) {
        checkChannel(channel);
        return providerMapper.selectList(new LambdaQueryWrapper<SysMessageProvider>()
            .eq(SysMessageProvider::getChannel, channel).orderByDesc(SysMessageProvider::getId))
            .stream().map(this::toProviderVO).toList();
    }

    @Override
    public MessageProviderVO provider(Long id) {
        return toProviderVO(requireProvider(id));
    }

    @Override
    @Transactional
    public MessageProviderVO saveProvider(Long id, MessageProviderForm form) {
        checkChannel(form.getChannel());
        if (!("MAIL".equals(form.getChannel()) ? MAIL : SMS).contains(form.getProviderType())) bad("供应商类型不支持");
        if (("MAIL".equals(form.getChannel()) || "CUSTOM".equals(form.getProviderType())) &&
            !StringUtils.hasText(form.getEndpoint())) bad("服务地址不能为空");
        boolean customSms = "SMS".equals(form.getChannel()) && "CUSTOM".equals(form.getProviderType());
        if (!customSms && (!StringUtils.hasText(form.getAccount()) ||
            !StringUtils.hasText(form.getSender()))) bad("服务连接配置不完整");
        if (StringUtils.hasText(form.getConfigJson())) {
            Map<?, ?> config;
            try { config = json.readValue(form.getConfigJson(), Map.class); }
            catch (RuntimeException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "服务参数必须为有效 JSON");
            }
            if (containsSensitiveConfigKey(config, true)) bad("服务参数不得包含明文密钥，鉴权值请填写凭据字段");
        }
        if (customSms) {
            if (!StringUtils.hasText(form.getConfigJson())) bad("自定义短信参数映射不能为空");
            CustomSmsTestSender.Options options;
            try { options = json.readValue(form.getConfigJson(), CustomSmsTestSender.Options.class); }
            catch (RuntimeException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "自定义短信参数映射无效");
            }
            if (!StringUtils.hasText(options.getPhoneParameter()) ||
                !StringUtils.hasText(options.getContentParameter())) bad("自定义短信参数映射不完整");
            if (!StringUtils.hasText(options.getAuthHeader())) bad("自定义短信必须配置鉴权请求头");
            if (!options.getAuthHeader().matches("^[A-Za-z0-9-]+$")) bad("鉴权请求头名称无效");
            if (options.getHeaders() != null && options.getHeaders().entrySet().stream().anyMatch(entry ->
                !"Accept".equalsIgnoreCase(entry.getKey()) ||
                    !Set.of("application/json", "text/plain").contains(entry.getValue()))) {
                bad("固定请求头仅支持非敏感的 Accept 类型；鉴权值请填写凭据字段");
            }
            if (StringUtils.hasText(options.getAuthPrefix()) &&
                !options.getAuthPrefix().matches("^[A-Za-z][A-Za-z0-9._-]{0,31} $")) {
                bad("鉴权前缀只能是方案名称和空格，鉴权值请填写凭据字段");
            }
            URI uri;
            try { uri = URI.create(form.getEndpoint()); }
            catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "短信接口 URL 无效"); }
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) bad("自定义短信接口必须使用 HTTPS");
        }
        if ("SMS".equals(form.getChannel()) && "TENCENT".equals(form.getProviderType()) &&
            !StringUtils.hasText(form.getConfigJson())) bad("腾讯短信 App ID 不能为空");
        if (providerMapper.selectCount(new LambdaQueryWrapper<SysMessageProvider>()
            .eq(SysMessageProvider::getChannel, form.getChannel()).eq(SysMessageProvider::getName, form.getName())
            .ne(id != null, SysMessageProvider::getId, id)) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "同通道服务名称已存在");
        }
        SysMessageProvider entity = id == null ? new SysMessageProvider() : requireProvider(id);
        if (id != null && !entity.getChannel().equals(form.getChannel())) bad("服务通道不可修改");
        entity.setChannel(form.getChannel());
        entity.setProviderType(form.getProviderType());
        entity.setName(form.getName());
        entity.setEndpoint(form.getEndpoint());
        entity.setAccount(form.getAccount());
        entity.setRegionCode(form.getRegionCode());
        entity.setSender(form.getSender());
        entity.setConfigJson(form.getConfigJson());
        entity.setEnabled(Boolean.TRUE.equals(form.getEnabled()));
        if (StringUtils.hasText(form.getCredential())) entity.setCredential(form.getCredential());
        if (!StringUtils.hasText(entity.getCredential())) bad("服务凭据不能为空");
        if (id == null) providerMapper.insert(entity); else providerMapper.updateById(entity);
        return toProviderVO(entity);
    }

    @Override
    @Transactional
    public void deleteProvider(Long id) {
        if (providerMapper.deleteById(id) == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "消息服务不存在");
    }

    @Override
    public List<MessageTemplateVO> templates(String channel, String providerType) {
        checkChannel(channel);
        return Arrays.stream(MessageTemplateType.values()).filter(type -> type.channel().equals(channel))
            .map(type -> effective(type, providerType)).toList();
    }

    @Override
    @Transactional
    public MessageTemplateVO saveTemplate(String channel, MessageTemplateForm form) {
        MessageTemplateType type = requireType(channel, form.getTemplateKey());
        if (form.getContent().length() > 10000 || ("SMS".equals(channel) && form.getContent().length() > 1000)) {
            bad("模板内容过长");
        }
        Map<String, String> sample = type.variables().stream()
            .collect(java.util.stream.Collectors.toMap(key -> key, key -> "sample"));
        renderer.render(type, form.getContent(), sample);
        String providerType = form.getProviderType() == null ? "" : form.getProviderType();
        if (!providerType.isEmpty() && !("MAIL".equals(channel) ? MAIL : SMS).contains(providerType)) {
            bad("模板服务商类型不支持");
        }
        String language = StringUtils.hasText(form.getLanguageCode()) ? form.getLanguageCode() : "zh-CN";
        if (!language.matches("^[a-zA-Z]{2,3}(-[a-zA-Z]{2,4})?$")) bad("语言编码无效");
        SysMessageTemplate entity = lookup(type, providerType, language);
        if (entity == null) entity = new SysMessageTemplate();
        entity.setChannel(channel);
        entity.setTemplateKey(type.name());
        entity.setProviderType(providerType);
        entity.setLanguageCode(language);
        entity.setTitle(form.getTitle());
        entity.setContent(form.getContent());
        entity.setProviderTemplateCode(form.getProviderTemplateCode());
        entity.setEnabled(form.isEnabled());
        if (entity.getId() == null) templateMapper.insert(entity); else templateMapper.updateById(entity);
        return toTemplateVO(type, entity);
    }

    @Override
    public String preview(String channel, String templateKey, String providerType, Map<String, String> variables) {
        MessageTemplateType type = requireType(channel, templateKey);
        MessageTemplateVO template = effective(type, providerType);
        if (!template.isEnabled()) bad("模板未启用");
        return renderer.render(type, template.getContent(), variables);
    }

    @Override
    public void test(MessageTestForm form) {
        SysMessageProvider provider = requireProvider(form.getProviderId());
        if (!Boolean.TRUE.equals(provider.getEnabled())) bad("消息服务未启用");
        MessageTemplateType type = requireType(provider.getChannel(), form.getTemplateKey());
        MessageTemplateVO template = effective(type, provider.getProviderType());
        if (!template.isEnabled()) bad("模板未启用");
        Map<String, String> variables = form.getVariables() == null ? Map.of() : form.getVariables();
        String content = renderer.render(type, template.getContent(), variables);
        String key = "system:message:test:" + SecurityUtils.getCurrentUser() + ":" + provider.getId();
        if (!Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(30)))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "测试发送过于频繁");
        }
        if ("MAIL".equals(provider.getChannel())) {
            try { mailSender.send(provider, form.getTarget(), template.getTitle(), content); }
            catch (ResponseStatusException ex) { throw ex; }
            catch (Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "邮件提交失败"); }
            auditTestSubmission(provider, type);
            return;
        }
        if (!form.getTarget().matches("^\\+?[0-9]{6,20}$")) bad("手机号格式无效");
        switch (provider.getProviderType()) {
            case "ALIYUN" -> aliyunSender.send(provider, form.getTarget(), template.getProviderTemplateCode(), variables);
            case "TENCENT" -> tencentSender.send(provider, form.getTarget(), template.getProviderTemplateCode(), type, variables);
            case "CUSTOM" -> customSender.send(provider, form.getTarget(), content, template.getProviderTemplateCode());
            default -> bad("短信服务类型不支持");
        }
        auditTestSubmission(provider, type);
    }

    private void auditTestSubmission(SysMessageProvider provider, MessageTemplateType type) {
        String actor = SecurityUtils.getCurrentUser();
        actor = actor == null ? "unknown" : actor.replaceAll("[\\p{Cntrl}]", "_");
        log.info("管理员消息测试已提交 actor={} providerId={} templateKey={}",
            actor, provider.getId(), type.name());
    }

    private SysMessageProvider requireProvider(Long id) {
        SysMessageProvider provider = providerMapper.selectById(id);
        if (provider == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "消息服务不存在");
        return provider;
    }

    private SysMessageTemplate lookup(MessageTemplateType type, String providerType, String language) {
        return templateMapper.selectOne(new LambdaQueryWrapper<SysMessageTemplate>()
            .eq(SysMessageTemplate::getChannel, type.channel()).eq(SysMessageTemplate::getTemplateKey, type.name())
            .eq(SysMessageTemplate::getProviderType, providerType)
            .eq(SysMessageTemplate::getLanguageCode, language));
    }

    private MessageTemplateVO effective(MessageTemplateType type, String providerType) {
        String provider = providerType == null ? "" : providerType;
        SysMessageTemplate override = lookup(type, provider, "zh-CN");
        if (override == null && !provider.isEmpty()) override = lookup(type, "", "zh-CN");
        return override == null ? new MessageTemplateVO(type.name(), type.channel(), provider, "zh-CN",
            type.title(), type.content(), null, true, false, type.variables()) : toTemplateVO(type, override);
    }

    private MessageTemplateVO toTemplateVO(MessageTemplateType type, SysMessageTemplate entity) {
        return new MessageTemplateVO(type.name(), type.channel(), entity.getProviderType(), entity.getLanguageCode(),
            entity.getTitle(), entity.getContent(), entity.getProviderTemplateCode(),
            Boolean.TRUE.equals(entity.getEnabled()), true, type.variables());
    }

    private MessageProviderVO toProviderVO(SysMessageProvider provider) {
        return new MessageProviderVO(provider.getId(), provider.getChannel(), provider.getProviderType(),
            provider.getName(), provider.getEndpoint(), provider.getAccount(), provider.getRegionCode(),
            provider.getSender(), provider.getConfigJson(), Boolean.TRUE.equals(provider.getEnabled()),
            StringUtils.hasText(provider.getCredential()));
    }

    private MessageTemplateType requireType(String channel, String key) {
        checkChannel(channel);
        try {
            MessageTemplateType type = MessageTemplateType.valueOf(key);
            if (!type.channel().equals(channel)) bad("模板与通道不匹配");
            return type;
        } catch (IllegalArgumentException ex) {
            bad("模板类型不支持");
            throw ex;
        }
    }

    private void checkChannel(String channel) {
        if (!Set.of("MAIL", "SMS").contains(channel)) bad("消息通道无效");
    }

    private boolean containsSensitiveConfigKey(Object value, boolean root) {
        if (value instanceof Map<?, ?> entries) {
            for (Map.Entry<?, ?> entry : entries.entrySet()) {
                String key = String.valueOf(entry.getKey());
                String normalized = NON_ALPHANUMERIC.matcher(key.toLowerCase(java.util.Locale.ROOT)).replaceAll("");
                if (!(root && Set.of("authHeader", "authPrefix").contains(key)) &&
                    (normalized.contains("secret") || normalized.contains("pass") ||
                        normalized.contains("pwd") || normalized.contains("token") ||
                        normalized.contains("cred") || normalized.contains("auth") ||
                        normalized.contains("key") || normalized.contains("access") ||
                        normalized.contains("sign"))) return true;
                if (containsSensitiveConfigKey(entry.getValue(), false)) return true;
            }
        } else if (value instanceof List<?> values) {
            return values.stream().anyMatch(item -> containsSensitiveConfigKey(item, false));
        }
        return false;
    }

    private static void bad(String reason) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
