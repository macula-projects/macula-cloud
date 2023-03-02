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

package tech.powerjob.server.core.evaluator;

/**
 * @author Echo009
 * @since 2021/12/10
 */
public interface Evaluator {
    /**
     * 使用给定输入计算表达式
     *
     * @param expression 可执行的表达式
     * @param input      输入
     * @return 计算结果
     */
    Object evaluate(String expression, Object input);

}
