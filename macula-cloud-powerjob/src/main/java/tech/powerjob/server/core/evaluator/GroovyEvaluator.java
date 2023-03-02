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

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

/**
 * @author Echo009
 * @since 2021/12/10
 */
@Slf4j
@Component
public class GroovyEvaluator implements Evaluator {

    private static final ScriptEngine ENGINE = new ScriptEngineManager().getEngineByName("groovy");

    @Override
    @SneakyThrows
    public Object evaluate(String expression, Object input) {
        Bindings bindings = ENGINE.createBindings();
        bindings.put("context", input);
        return ENGINE.eval(expression, bindings);
    }

}
