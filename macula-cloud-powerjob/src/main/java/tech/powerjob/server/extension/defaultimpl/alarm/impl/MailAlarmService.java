/*
 * Copyright (c) 2023 Macula
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

package tech.powerjob.server.extension.defaultimpl.alarm.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import tech.powerjob.server.extension.Alarmable;
import tech.powerjob.server.extension.defaultimpl.alarm.module.Alarm;
import tech.powerjob.server.persistence.remote.model.UserInfoDO;

import javax.annotation.Resource;
import java.util.List;
import java.util.Objects;

/**
 * 邮件通知服务
 *
 * @author tjq
 * @since 2020/4/30
 */
@Slf4j
@Service
public class MailAlarmService implements Alarmable {

    @Resource
    private Environment environment;

    private JavaMailSender javaMailSender;

    @Value("${spring.mail.username:''}")
    private String from;

    @Override
    public void onFailed(Alarm alarm, List<UserInfoDO> targetUserList) {
        if (CollectionUtils.isEmpty(targetUserList) || javaMailSender == null || StringUtils.isEmpty(from)) {
            return;
        }

        SimpleMailMessage sm = new SimpleMailMessage();
        try {
            sm.setFrom(from);
            sm.setTo(targetUserList.stream().map(UserInfoDO::getEmail).filter(Objects::nonNull).toArray(String[]::new));
            sm.setSubject(alarm.fetchTitle());
            sm.setText(alarm.fetchContent());

            javaMailSender.send(sm);
        } catch (Exception e) {
            log.warn("[MailAlarmService] send mail failed, reason is {}", e.getMessage());
        }
    }

    @Autowired(required = false)
    public void setJavaMailSender(JavaMailSender javaMailSender) {
        this.javaMailSender = javaMailSender;
    }

}
