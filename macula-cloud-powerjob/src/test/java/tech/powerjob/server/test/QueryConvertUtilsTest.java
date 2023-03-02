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

import com.alibaba.fastjson.JSONObject;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.time.DateUtils;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import tech.powerjob.common.PowerQuery;
import tech.powerjob.common.response.JobInfoDTO;
import tech.powerjob.server.core.service.JobService;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

/**
 * test QueryConvertUtils
 *
 * @author tjq
 * @since 2021/1/16
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class QueryConvertUtilsTest {

    @Resource
    private JobService jobService;

    @Test
    public void autoConvert() {
        JobInfoQuery jobInfoQuery = new JobInfoQuery();
        jobInfoQuery.setAppIdEq(1L);
        jobInfoQuery.setJobNameLike("DAG");
        jobInfoQuery.setStatusIn(Lists.newArrayList(1));
        jobInfoQuery.setGmtCreateGt(DateUtils.addDays(new Date(), -300));

        List<JobInfoDTO> list = jobService.queryJob(jobInfoQuery);
        System.out.println("size: " + list.size());
        System.out.println(JSONObject.toJSONString(list));
    }

    @Getter
    @Setter
    public static class JobInfoQuery extends PowerQuery {
        private String jobNameLike;
        private Date gmtCreateGt;
        private List<Integer> statusIn;
    }
}