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

package tech.powerjob.server.remote.server.redirector;

import com.alibaba.fastjson.JSONObject;
import org.springframework.util.ReflectionUtils;
import tech.powerjob.server.common.utils.SpringUtils;

import java.lang.reflect.Method;

/**
 * process remote request
 *
 * @author tjq
 * @since 2021/2/19
 */
public class RemoteRequestProcessor {

    public static Object processRemoteRequest(RemoteProcessReq req) throws ClassNotFoundException {
        Object[] args = req.getArgs();
        String[] parameterTypes = req.getParameterTypes();
        Class<?>[] parameters = new Class[parameterTypes.length];

        for (int i = 0; i < parameterTypes.length; i++) {
            parameters[i] = Class.forName(parameterTypes[i]);
            Object arg = args[i];
            if (arg != null) {
                args[i] = JSONObject.parseObject(JSONObject.toJSONBytes(arg), parameters[i]);
            }
        }

        Class<?> clz = Class.forName(req.getClassName());

        Object bean = SpringUtils.getBean(clz);
        Method method = ReflectionUtils.findMethod(clz, req.getMethodName(), parameters);

        assert method != null;
        return ReflectionUtils.invokeMethod(method, bean, args);
    }
}
