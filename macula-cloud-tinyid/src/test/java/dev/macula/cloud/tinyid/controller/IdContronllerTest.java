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
package dev.macula.cloud.tinyid.controller;

import dev.macula.boot.result.Result;
import dev.macula.boot.starter.tinyid.base.entity.SegmentId;
import dev.macula.boot.starter.tinyid.base.factory.IdGeneratorFactory;
import dev.macula.boot.starter.tinyid.base.generator.IdGenerator;
import dev.macula.boot.starter.tinyid.base.service.SegmentIdService;
import dev.macula.cloud.tinyid.service.TinyIdTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Protects the four existing TinyID issuing contracts while token authorization changes internally.
 *
 * @author Rain
 * @since 6.1.0
 */
class IdContronllerTest {

    private final IdGeneratorFactory generatorFactory = mock(IdGeneratorFactory.class);
    private final SegmentIdService segmentIdService = mock(SegmentIdService.class);
    private final TinyIdTokenService tokenService = mock(TinyIdTokenService.class);
    private final IdGenerator generator = mock(IdGenerator.class);
    private final IdContronller controller = new IdContronller(generatorFactory, segmentIdService, tokenService);

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "batchSizeMax", 100);
        when(generatorFactory.getIdGenerator("order")).thenReturn(generator);
        when(tokenService.canVisit("order", "valid")).thenReturn(true);
    }

    @Test
    void keepsBatchIssuingContractsAndCapsBatchSize() {
        when(generator.nextId(100)).thenReturn(List.of(101L, 102L));
        when(generator.nextId()).thenReturn(103L);

        Result<List<Long>> result = controller.nextId("order", 999, "valid");

        assertTrue(result.isSuccess());
        assertEquals(List.of(101L, 102L), result.getData());
        assertEquals("103", controller.nextIdSimple("order", null, "valid"));
        verify(generator).nextId(100);
    }

    @Test
    void rejectsUnauthorizedBatchIssuingWithoutAllocatingIds() {
        Result<List<Long>> result = controller.nextId("order", 10, "invalid");

        assertFalse(result.isSuccess());
        assertEquals("", controller.nextIdSimple("order", 10, "invalid"));
        verify(generatorFactory, never()).getIdGenerator("order");
    }

    @Test
    void keepsSegmentIssuingContractsForAuthorizedTokens() {
        SegmentId segment = new SegmentId();
        segment.setCurrentId(new AtomicLong(200));
        segment.setLoadingId(250);
        segment.setMaxId(300);
        segment.setDelta(2);
        segment.setRemainder(1);
        when(segmentIdService.getNextSegmentId("order")).thenReturn(segment);

        Result<SegmentId> result = controller.nextSegmentId("order", "valid");

        assertTrue(result.isSuccess());
        assertEquals(segment, result.getData());
        assertEquals("200,250,300,2,1", controller.nextSegmentIdSimple("order", "valid"));
    }

    @Test
    void rejectsUnauthorizedSegmentIssuingWithoutAllocatingSegments() {
        Result<SegmentId> result = controller.nextSegmentId("order", "invalid");

        assertFalse(result.isSuccess());
        assertEquals("", controller.nextSegmentIdSimple("order", "invalid"));
        verify(segmentIdService, never()).getNextSegmentId("order");
    }
}
