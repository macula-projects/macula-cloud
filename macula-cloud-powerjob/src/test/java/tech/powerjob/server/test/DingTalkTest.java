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

package tech.powerjob.server.test;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tech.powerjob.server.extension.defaultimpl.alarm.impl.DingTalkUtils;

/**
 * 测试钉钉消息工具
 *
 * @author tjq
 * @since 2020/8/8
 */
public class DingTalkTest {

    private static final Long AGENT_ID = 847044348L;
    private static final DingTalkUtils dingTalkUtils =
        new DingTalkUtils("dingauqwkvxxnqskknfv", "XWrEPdAZMPgJeFtHuL0LH73LRj-74umF2_0BFcoXMfvnX0pCQvt0rpb1JOJU_HLl");

    @Test
    @Disabled
    public void testFetchUserId() throws Exception {
        /**
         System.out.println(dingTalkUtils.fetchUserIdByMobile("38353"));
         **/
    }

    @Test
    @Disabled
    public void testSendMarkdown() throws Exception {
        /**
         String userId = "2159453017839770,1234";

         List<DingTalkUtils.MarkdownEntity> mds = Lists.newLinkedList();
         mds.add(new DingTalkUtils.MarkdownEntity("t111","hahahahahahahha1"));
         mds.add(new DingTalkUtils.MarkdownEntity("t2222","hahahahahahahha2"));
         mds.add(new DingTalkUtils.MarkdownEntity("t3333","hahahahahahahha3"));

         dingTalkUtils.sendMarkdownAsync("PowerJob AlarmService", mds, userId, AGENT_ID);
         **/
    }

}
