package test.dev.macula.cloud.system;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.macula.cloud.system.MaculaSystemApplication;
import dev.macula.cloud.system.form.ApplicationForm;
import dev.macula.cloud.system.mapper.SysApplicationMapper;
import dev.macula.cloud.system.pojo.bo.ApplicationBO;
import dev.macula.cloud.system.pojo.entity.SysApplication;
import dev.macula.cloud.system.query.ApplicationPageQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.core.Is.is;

@ActiveProfiles("default")
@SpringBootTest(classes = MaculaSystemApplication.class)
@AutoConfigureMockMvc
public class TestSysApplicationRestByAnonymous {
    // macula默认使用macula-boot-starter-security进行相关路径权限验证，为方便进行接口测试，替换默认的SecurityFilterChain，
    // 实现所有接口支持无权限访问
    @MockBean
    private SecurityFilterChain securityFilterChain;
    @SpyBean
    private SysApplicationMapper sysApplicationMapper;
    @Autowired
    private MockMvc mvc;


    @Test
    public void testListApplications() throws Exception{
        Page<ApplicationBO> res = new Page<>();
        res.setSize(1);
        res.setTotal(1);
        List<ApplicationBO> records = new ArrayList<>();
        ApplicationBO sys = new ApplicationBO();
        sys.setApplicationName("bff-abd");
        sys.setSk("test");
        sys.setCode("bff-abd");
        sys.setManager("admin");
        sys.setUseAttrs(false);
        sys.setMaintainer("admin");
        records.add(sys);
        res.setRecords(records);
        doReturn(res).when(sysApplicationMapper).listApplicationPages(any(Page.class), any(ApplicationPageQuery.class));

        mvc.perform(get("/api/v1/app?pageNo=1&pageSize=1&keywords=bff")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.total", is("1")))
                .andExpect(jsonPath("$.data.records[0].applicationName", containsString("bff")))
                .andDo(print());
    }

    @Test
    public void testSaveApplication() throws Exception{
        doReturn(1).when(sysApplicationMapper).insert(any(SysApplication.class));

        ApplicationForm applicationForm = new ApplicationForm();
        applicationForm.setApplicationName("test");
        applicationForm.setCode("test");
        applicationForm.setSk("test");
        applicationForm.setManager("admin");
        applicationForm.setMobile("13800138000");
        applicationForm.setUseAttrs(false);
        mvc.perform(post("/api/v1/app")
                        .contentType(MediaType.APPLICATION_JSON).content(JSONObject.toJSONString(applicationForm)))
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success", is(true)))
                .andDo(print());
    }

    @Test
    public void testUpdateApplication() throws Exception{
        doReturn(1).when(sysApplicationMapper).updateById(any(SysApplication.class));

        ApplicationForm applicationForm = new ApplicationForm();
        applicationForm.setApplicationName("test1");
        applicationForm.setCode("test1");
        applicationForm.setSk("test1");
        applicationForm.setManager("admin");
        applicationForm.setUseAttrs(false);
        mvc.perform(put("/api/v1/app/1")
                        .contentType(MediaType.APPLICATION_JSON).content(JSONObject.toJSONString(applicationForm)))
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success", is(true)))
                .andDo(print());
    }

    @Test
    public void testDeleteApplications() throws Exception{
        doReturn(1).when(sysApplicationMapper).deleteById(any(SysApplication.class));

        mvc.perform(delete("/api/v1/app/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success", is(true)))
                .andDo(print());
    }

    @Test
    public void testAddMaintainer() throws Exception{
        doReturn(1).when(sysApplicationMapper).updateById(any(SysApplication.class));
        SysApplication sys = new SysApplication();
        sys.setId(1L);
        doReturn(sys).when(sysApplicationMapper).selectById(1L);

        ApplicationForm applicationForm = new ApplicationForm();
        applicationForm.setApplicationName("test1");
        applicationForm.setCode("test1");
        applicationForm.setSk("test1");
        applicationForm.setManager("admin");
        applicationForm.setUseAttrs(false);
        applicationForm.setMaintainer("admin");
        mvc.perform(put("/api/v1/app/addMaintainer/1")
                        .contentType(MediaType.APPLICATION_JSON).content(JSONObject.toJSONString(applicationForm)))
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success", is(true)))
                .andDo(print());
    }

    @Test
    public void testValidtorAppCode() throws Exception{
        doReturn(0L).when(sysApplicationMapper).selectCount(any(Wrapper.class));

        mvc.perform(get("/api/v1/app/validtor/appCode?appId=1&appCode=test")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", is(true)))
                .andDo(print());
    }
}
