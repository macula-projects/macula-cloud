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
package dev.macula.cloud.tinyid.listener;

import dev.macula.boot.starter.auditlog.event.OperLogEvent;
import dev.macula.cloud.tinyid.config.DynamicDataSource;
import dev.macula.cloud.tinyid.mapper.TinyIdAuditLogMapper;
import dev.macula.cloud.tinyid.pojo.entity.TinyIdAuditLog;
import org.junit.jupiter.api.Test;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Verifies that audit persistence never stores raw JDBC errors or credentials.
 *
 * @author Rain
 * @since 6.1.0
 */
class TinyIdAuditLogEventListenerTest {

    @Test
    void replacesRawSqlAndTokenErrorWithFixedSummary() {
        TinyIdAuditLogMapper mapper = mock(TinyIdAuditLogMapper.class);
        DynamicDataSource routingDataSource = new DynamicDataSource();
        routingDataSource.setDataSourceKeys(List.of("datasource-0", "datasource-1"));
        OperLogEvent event = new OperLogEvent();
        event.setOperUrl("/api/v1/admin/apps");
        event.setRequestMethod("POST");
        event.setStatus(1);
        event.setErrorMsg("PreparedStatement SQL [insert into tiny_id_token] Duplicate entry 'secret-token-order'");

        new TinyIdAuditLogEventListener(mapper, routingDataSource).save(event);

        org.mockito.ArgumentCaptor<TinyIdAuditLog> captor = org.mockito.ArgumentCaptor.forClass(TinyIdAuditLog.class);
        verify(mapper).insert(captor.capture());
        assertFalse(captor.getValue().isSuccess());
        assertEquals("TinyID management request failed", captor.getValue().getErrorSummary());
    }

    @Test
    void followsPlatformAsyncEventListenerContract() throws NoSuchMethodException {
        var method = TinyIdAuditLogEventListener.class.getMethod("save", OperLogEvent.class);

        assertEquals(true, method.isAnnotationPresent(Async.class));
        assertEquals(true, method.isAnnotationPresent(EventListener.class));
    }
}
