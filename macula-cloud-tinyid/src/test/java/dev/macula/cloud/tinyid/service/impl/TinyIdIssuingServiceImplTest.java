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

package dev.macula.cloud.tinyid.service.impl;

import dev.macula.boot.starter.tinyid.base.exception.TinyIdSysException;
import dev.macula.boot.starter.tinyid.base.factory.IdGeneratorFactory;
import dev.macula.boot.starter.tinyid.base.generator.IdGenerator;
import dev.macula.boot.starter.tinyid.base.service.SegmentIdService;
import dev.macula.cloud.tinyid.pojo.vo.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证发号入口的参数校验、批量约束及异常传播；不连接存储。
 * @author Rain
 * @since 6.1.0
 */
class TinyIdIssuingServiceImplTest {
    private final IdGeneratorFactory factory = mock(IdGeneratorFactory.class);
    private final SegmentIdService segments = mock(SegmentIdService.class);
    private final TinyIdIssuingServiceImpl service = new TinyIdIssuingServiceImpl(factory, segments);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "batchSizeMax", 100);
    }

    @Test
    void rejectsBlankBusinessBeforeIssuing() {
        assertThatThrownBy(() -> service.nextIds(" ", 1)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.nextSegment(null)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(factory, segments);
    }

    @Test
    void retainsDefaultAndMaximumBatchSizes() {
        var generator = mock(IdGenerator.class);
        when(factory.getIdGenerator("order")).thenReturn(generator);
        when(generator.nextId(1)).thenReturn(List.of(101L));
        when(generator.nextId(100)).thenReturn(List.of(102L));
        assertThat(service.nextIds("order", null)).containsExactly(101L);
        assertThat(service.nextIds("order", 999)).containsExactly(102L);
        verify(generator).nextId(1);
        verify(generator).nextId(100);
    }

    @Test
    void propagatesBusinessFailures() {
        var failure = new TinyIdSysException(ErrorCode.BIZ_TYPE_NOT_FOUND, "发号业务不存在");
        when(segments.getNextSegmentId("missing")).thenThrow(failure);
        assertThatThrownBy(() -> service.nextSegment("missing")).isSameAs(failure);
        assertThatThrownBy(() -> service.nextSegment(" ")).isInstanceOf(ResponseStatusException.class);
        when(factory.getIdGenerator("missing")).thenThrow(failure);
        assertThatThrownBy(() -> service.nextIds("missing", 1)).isSameAs(failure);
    }
}
