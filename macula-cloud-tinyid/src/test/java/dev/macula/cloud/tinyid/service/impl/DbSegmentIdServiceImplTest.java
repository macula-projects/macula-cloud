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
import dev.macula.cloud.tinyid.common.Constants;
import dev.macula.cloud.tinyid.mapper.TinyIdInfoMapper;
import dev.macula.cloud.tinyid.pojo.entity.TinyIdInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 验证数据库号段异常结果码以及乐观锁重试和成功转换不变。
 * @author Rain
 * @since 6.1.0
 */
class DbSegmentIdServiceImplTest {

    private final TinyIdInfoMapper mapper = mock(TinyIdInfoMapper.class);
    private final DbSegmentIdServiceImpl service = new DbSegmentIdServiceImpl(mapper);

    @Test
    void reportsMissingBusinessWithoutUpdating() {
        assertThatThrownBy(() -> service.getNextSegmentId("missing"))
            .isInstanceOfSatisfying(TinyIdSysException.class, error -> assertThat(error.getCode()).isEqualTo("ID503"));
        verify(mapper).selectOne(any());
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void reportsConflictAfterExistingRetryLimit() {
        when(mapper.selectOne(any())).thenReturn(info());
        assertThatThrownBy(() -> service.getNextSegmentId("order"))
            .isInstanceOfSatisfying(TinyIdSysException.class, error -> assertThat(error.getCode()).isEqualTo("ID504"));
        verify(mapper, times(Constants.RETRY)).updateMaxId(1L, 200L, 100L, 0L, "order");
        verify(mapper, times(Constants.RETRY)).selectOne(any());
    }

    @Test
    void retainsStorageCauseWithoutExposingItsDetails() {
        var cause = new IllegalStateException("test-only internal storage details");
        when(mapper.selectOne(any())).thenThrow(cause);
        assertThatThrownBy(() -> service.getNextSegmentId("order"))
            .isInstanceOfSatisfying(TinyIdSysException.class, error -> {
                assertThat(error.getCode()).isEqualTo("ID502");
                assertThat(error).hasMessage("号段申请失败").hasCause(cause);
            });
    }

    @Test
    void preservesSuccessfulSegmentAfterTransientConflict() {
        when(mapper.selectOne(any())).thenReturn(info());
        when(mapper.updateMaxId(1L, 200L, 100L, 0L, "order")).thenReturn(0, 1);
        var segment = service.getNextSegmentId("order");
        assertThat(segment.getCurrentId().get()).isEqualTo(100);
        assertThat(segment.getMaxId()).isEqualTo(200);
        assertThat(segment.getLoadingId()).isEqualTo(120);
        assertThat(segment.getDelta()).isEqualTo(2);
        assertThat(segment.getRemainder()).isEqualTo(1);
        verify(mapper, times(2)).updateMaxId(1L, 200L, 100L, 0L, "order");
    }

    private static TinyIdInfo info() {
        TinyIdInfo info = new TinyIdInfo();
        info.setId(1L);
        info.setBizType("order");
        info.setMaxId(100L);
        info.setStep(100);
        info.setVersion(0L);
        info.setDelta(2);
        info.setRemainder(1);
        return info;
    }
}
