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

package dev.macula.cloud.tinyid.controller;

import dev.macula.boot.starter.tinyid.base.entity.SegmentId;
import dev.macula.boot.starter.tinyid.base.exception.TinyIdSysException;
import dev.macula.cloud.tinyid.pojo.vo.ErrorCode;
import dev.macula.cloud.tinyid.service.TinyIdIssuingService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证 Controller 只适配成功数据与文本，不包装 Result 或吞没业务异常。
 * @author Rain
 * @since 6.1.0
 */
class IdContronllerTest {
    private final TinyIdIssuingService service = mock(TinyIdIssuingService.class);
    private final IdContronller controller = new IdContronller(service);

    @Test
    void returnsBusinessDataAndFormatsText() {
        when(service.nextIds("order", 2)).thenReturn(List.of(101L, 102L));
        assertThat(controller.nextId("order", 2)).containsExactly(101L, 102L);
        assertThat(controller.nextIdSimple("order", 2).getBody()).isEqualTo("101,102");
        SegmentId segment = new SegmentId();
        segment.setCurrentId(new AtomicLong(200));
        segment.setLoadingId(250);
        segment.setMaxId(300);
        segment.setDelta(2);
        segment.setRemainder(1);
        when(service.nextSegment("order")).thenReturn(segment);
        assertThat(controller.nextSegmentId("order")).isSameAs(segment);
        assertThat(controller.nextSegmentIdSimple("order").getBody()).isEqualTo("200,250,300,2,1");
    }

    @Test
    void letsAdviceHandleFailuresFromEveryEndpoint() {
        var failure = new TinyIdSysException(ErrorCode.BIZ_TYPE_NOT_FOUND, "发号业务不存在");
        when(service.nextIds("order", 1)).thenThrow(failure);
        when(service.nextSegment("order")).thenThrow(failure);
        assertThatThrownBy(() -> controller.nextId("order", 1)).isSameAs(failure);
        assertThatThrownBy(() -> controller.nextIdSimple("order", 1)).isSameAs(failure);
        assertThatThrownBy(() -> controller.nextSegmentId("order")).isSameAs(failure);
        assertThatThrownBy(() -> controller.nextSegmentIdSimple("order")).isSameAs(failure);
    }
}
