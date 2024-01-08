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

package dev.macula.cloud.gpt.test;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.generation.models.QwenParam;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.MessageManager;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.utils.JsonUtils;
import org.junit.jupiter.api.Test;

/**
 * {@code QwenTest} 通义千问
 *
 * @author rain
 * @since 2023/12/27 13:46
 */
public class QwenTest {
    private static final String DASHSCOPE_API_KEY = "sk-18d61c4d863e4160b331fcce600c9302";

    @Test
    public void testClassfiy() throws NoApiKeyException, InputRequiredException {
        // 使用通义千问SDK调用Qwen
        Generation gen = new Generation();
        MessageManager msgManager = new MessageManager(10);
        Message systemMsg = Message.builder().role(Role.SYSTEM.getValue()).content(
                "我会给你几个问题类型，请参考额外的背景知识（可能为空）和对话内容，判断我本次的问题类型，并返回对应类型的 ID，格式为 JSON 字符串:\n" + "\"\"\"\n" + "'{\"问题类型\":\"类型的 ID\"}'\n" + "\"\"\"\n" + "\n" + "\n" + "<问题类型>\n" + "NO-1:盘点人\n" + "NO-2:目标看板\n" + "NO-3:购物\n" + "NO-4:询问天气\n" + "NO-99:其他问题\n" + "</问题类型>\n" + "\n" + "\n" + "<背景知识>\n" + "</背景知识>")
            .build();
        Message qMsg = Message.builder().role(Role.USER.getValue())
            .content("<对话内容>\n" + "我要购买增健口服液\n" + "</对话内容>").build();
        msgManager.add(systemMsg);
        msgManager.add(qMsg);
        QwenParam param =
            QwenParam.builder().apiKey(DASHSCOPE_API_KEY).model(Generation.Models.QWEN_TURBO).messages(msgManager.get())
                .resultFormat(QwenParam.ResultFormat.MESSAGE).topP(0.8).enableSearch(false).build();
        GenerationResult result = gen.call(param);
        System.out.println(result.getOutput().getChoices().get(0).getMessage().getContent());
        System.out.println(JsonUtils.toJson(result));
    }
}
