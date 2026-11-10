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
package dev.macula.cloud.system.enums;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * 首次绑定映射集合的稳定线格式与严格校验，不依赖枚举名或Record默认字段名。
 * @author Rain
 * @since 6.1.0
 */
public final class IdentityBindingMappings {
    private static final int MAX_MAPPINGS = java.util.Arrays.stream(IdentityBindingSourceField.values())
        .mapToInt(field -> IdentityBindingMapping.targets(field).size()).sum();
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    /** 只提供共享合同，不创建实例。 */
    private IdentityBindingMappings() { }
    /** 解析请求中的稳定字段；重复项、未知字段和半组不进入认证策略。 */
    public static List<IdentityBindingMapping> fromWire(List<Entry> entries) {
        if (entries == null || entries.size() > MAX_MAPPINGS) { throw new IllegalArgumentException("绑定规则集合无效"); }
        List<IdentityBindingMapping> result = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry == null || entry.getSourceField() == null || entry.getTargetField() == null) {
                throw new IllegalArgumentException("每条绑定必须有源字段和IAM字段");
            }
            var mapping = IdentityBindingMapping.of(entry.getSourceField(), entry.getTargetField());
            if (result.contains(mapping)) { throw new IllegalArgumentException("绑定规则重复"); }
            result.add(mapping);
        }
        return List.copyOf(result);
    }
    /** 列NULL才兼容旧单项，损坏或显式空集合不回退旧规则。 */
    public static List<IdentityBindingMapping> decode(String value, String source, String target, String legacy) {
        var previous = IdentityBindingMapping.resolve(source, target, legacy);
        if (value == null) { return previous == null ? List.of() : List.of(previous); }
        List<Entry> entries;
        try { entries = JSON.readValue(value, new tools.jackson.core.type.TypeReference<List<Entry>>() { }); }
        catch (tools.jackson.core.JacksonException ex) { throw new IllegalArgumentException("绑定规则JSON格式无效"); }
        var result = fromWire(entries);
        if (previous != null && (result.size() != 1 || !previous.equals(result.get(0)))) {
            throw new IllegalArgumentException("新旧绑定规则不一致");
        }
        return result;
    }
    /** 持久化和VO使用同一稳定值，不公开Record的source/target属性。 */
    public static List<Map<String, String>> toWire(List<IdentityBindingMapping> mappings) {
        return mappings.stream().map(item -> Map.of("sourceField", item.source().value(), "targetField", item.target().value())).toList();
    }
    /** 写入可跨System与IAM解释的有界JSON集合。 */
    public static String encode(List<IdentityBindingMapping> mappings) { return JSON.writeValueAsString(toWire(mappings)); }
    /** 初次匹配和锁内匹配使用固定顺序，减少多规则范围锁的反序竞争。 */
    public static List<IdentityBindingMapping> ordered(List<IdentityBindingMapping> mappings) {
        return mappings.stream().sorted(Comparator.comparing((IdentityBindingMapping item) -> item.target().value())
            .thenComparing(item -> item.source().value())).toList();
    }
    /**
     * 请求与存储的线格式条目，拒绝重复JSON键和未知属性。
     * @since 6.1.0
     */
    public static final class Entry {
        private String sourceField;
        private String targetField;
        private boolean sourcePresent;
        private boolean targetPresent;
        /** Jackson通过显式setter解码稳定属性名。 */
        public Entry() { }
        /** 返回稳定源字段值。 */
        public String getSourceField() { return sourceField; }
        /** 返回稳定目标字段值。 */
        public String getTargetField() { return targetField; }
        /** 不允许后出现的重复源字段覆盖先前值。 */
        public void setSourceField(Object value) {
            if (sourcePresent) { throw new IllegalArgumentException("源字段重复"); }
            sourcePresent = true;
            if (!(value instanceof String text)) { throw new IllegalArgumentException("源字段必须为字符串"); }
            sourceField = text;
        }
        /** 不允许后出现的重复目标字段覆盖先前值。 */
        public void setTargetField(Object value) {
            if (targetPresent) { throw new IllegalArgumentException("IAM字段重复"); }
            targetPresent = true;
            if (!(value instanceof String text)) { throw new IllegalArgumentException("IAM字段必须为字符串"); }
            targetField = text;
        }
        /** 映射不能扩展为任意列、SQL或表达式。 */
        @JsonAnySetter
        public void rejectUnknown(String name, Object value) { throw new IllegalArgumentException("未知绑定字段"); }
    }
}
