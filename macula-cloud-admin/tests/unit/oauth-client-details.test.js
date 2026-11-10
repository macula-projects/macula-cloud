import { describe, it, expect, vi, beforeEach } from "vitest";
import { mount, flushPromises } from "@vue/test-utils";
import { createRouter, createMemoryHistory } from "vue-router";
import ElementPlus from "element-plus";
import Page from "@/views/system/oauth2Client/index.vue";
import OAuthProtocolSettings from "@/views/system/oauth2Client/components/OAuthProtocolSettings.vue";
import { protocolDefaults } from "@/utils/applicationProtocolRegistry";
import api from "@/api/model/system/oauth2Client";
vi.mock("@/api/model/system/oauth2Client", async (original) => {
  const actual = await original();
  return {
    ...actual,
    default: {
      get: vi.fn(),
      list: vi.fn(),
      create: vi.fn(),
      createDraft: vi.fn(),
      update: vi.fn(),
      configurationCatalog: vi.fn(),
      metadata: vi.fn(),
    },
  };
});
vi.mock("@/api/model/system/tenant", () => ({
  default: {
    tenant: {
      options: {
        get: vi.fn(async () => ({
          success: true,
          data: [{ value: 1, label: "测试租户" }],
        })),
      },
    },
  },
}));
const ok = (data) => ({ success: true, data });
const record = () => ({
  purpose:'APPLICATION',protocol:'OIDC',settings:{...protocolDefaults({applicationType:'SPA'},'OIDC'),clientAuthenticationMethods:'none',authorizationGrantTypes:'authorization_code',scopes:'openid',redirectUris:'https://client.example/callback',allowedOrigins:['https://client.example'],requireProofKey:true},
  configurationVersion: "fixture-version",
  id: 42,
  clientId: "existing",
  clientName: "业务应用",
  applicationType: "SPA",
  enabledProtocols:["OIDC","OAUTH2_1"],defaultProtocol:"OIDC",configurationStatus:"ENABLED",
  browserProviderBindings: {},
  miniappPlatform: null,
  hostedLoginMethods: ["PASSWORD"],
  clientAuthenticationMethods: "none",
  authorizationGrantTypes: "authorization_code",
  scopes: "openid",
  redirectUris: "https://client.example/callback",
  requireProofKey: true,
  requireAuthorizationConsent: false,
  reuseRefreshTokens: true,
  tenantId: 1,
  userType: "employee",
  browserLoginProviderTypes: [],
  externalCodeTypes: [],
  externalProviderBindings: {},
  allowedOrigins: ["https://client.example"],
  secretConfigured: false,
});
async function open(query = { id: "42",tab:"application" }) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: "/", component: Page }],
  });
  await router.push({ path: "/", query });
  await router.isReady();
  const wrapper = mount(Page, {
    global: { plugins: [router, ElementPlus], mocks: { $t: (value) => value } },
    attachTo: document.body,
  });
  await flushPromises();
  return { wrapper, router };
}
beforeEach(() => {
  vi.clearAllMocks();
  api.get.mockResolvedValue(ok(record()));
  api.list.mockResolvedValue(ok({ records: [record()], total: 1 }));
  api.configurationCatalog.mockResolvedValue(
    ok({ protocols: [], scopes: [{value:"openid",label:"登录",oidcOnly:true},{value:"profile",label:"资料",oidcOnly:true}], sources: [] })
  );
  api.metadata.mockResolvedValue(
    ok({
      available: true,
      endpoints: {
        issuer: "https://iam.example",
        jwks_uri: "https://iam.example/keys",
        token_endpoint: "https://iam.example/token",
      },
    })
  );
  api.update.mockImplementation(async (id, payload) =>
    ok({ ...record(), ...payload, id })
  );
});
describe("application detail draft", () => {
  it("preserves sibling sections when saving after tab changes and locks existing type", async () => {
    const { wrapper, router } = await open();
    const name = wrapper
      .findAll("input")
      .find((input) => input.element.value === "业务应用");
    await name.setValue("新应用名");
    expect(wrapper.findAll("input").some(input => input.element.value === "existing")).toBe(false);
    expect(wrapper.text()).toContain("保存后只读");
    await router.replace({ query: { id: "42", tab: "protocol" } });
    await flushPromises();
    expect(wrapper.findAll("input").find(input => input.element.value === "existing").attributes("readonly")).toBeDefined();
    const scopeSelect = wrapper.findAllComponents({name:"ElSelect"}).find(select => select.props("multiple"));
    expect(scopeSelect).toBeDefined();
    scopeSelect.vm.$emit("update:modelValue", ["openid", "profile"]);
    await flushPromises();
    await wrapper
      .findAll("button")
      .find((button) => button.text() === "保存并启用")
      .trigger("click");
    await flushPromises();
    expect(api.update).toHaveBeenCalledWith(
      42,
      expect.objectContaining({
        clientName: "新应用名",
        protocol:"OIDC",settings:expect.objectContaining({scopes:"openid,profile",allowedOrigins:["https://client.example"]}),
        hostedLoginMethods: ["PASSWORD"],

        applicationType: "SPA",
      })
    );
    wrapper.unmount();
  });
  it("clears temporary guide secrets when leaving the guide", async () => {
    api.get.mockResolvedValue(
      ok({
        ...record(),
        applicationType: "WEB_SERVER",
        settings:{...record().settings,clientAuthenticationMethods:'client_secret_basic'},
        secretConfigured: true,
      })
    );
    const { wrapper, router } = await open({ id: "42", tab: "quickstart" });
    await wrapper
      .find('input[autocomplete="off"]')
      .setValue("temporary-secret");
    expect(wrapper.text()).not.toContain("temporary-secret");
    await router.replace({ query: { id: "42", tab: "application" } });
    await flushPromises();
    await router.replace({ query: { id: "42", tab: "quickstart" } });
    await flushPromises();
    expect(wrapper.find('input[autocomplete="off"]').element.value).toBe("");
    expect(api.update).not.toHaveBeenCalled();
    wrapper.unmount();
  });
  it("shows retryable metadata failure without inventing endpoints", async () => {
    api.metadata.mockRejectedValue(new Error("offline"));
    const { wrapper } = await open({id:'42',tab:'protocol'});
    await wrapper.findAllComponents({name:'ElCollapseItem'}).find(item => item.props('name') === 'endpoints').find('.el-collapse-item__header').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain("接入地址获取失败");
    expect(wrapper.text()).toContain("重新获取");
    expect(
      wrapper
        .findAll("input")
        .some((input) => input.element.value.includes("iam.example"))
    ).toBe(false);
    wrapper.unmount();
  });
});

describe('OIDC SDK-only page',()=>{
 it('keeps the OIDC SDK grant without adding a browser callback on save',async()=>{
  const row=record();row.settings.authorizationGrantTypes='urn:macula:params:oauth:grant-type:external_code';row.settings.redirectUris='';
  row.externalCodeTypes=['WECHAT_MINIAPP_LOGIN_CODE'];row.externalProviderBindings={WECHAT_MINIAPP_LOGIN_CODE:8};
  row.applicationType='MINIAPP';row.miniappPlatform='WECHAT';
  api.get.mockResolvedValue(ok(row));
  api.configurationCatalog.mockResolvedValue(ok({protocols:[],scopes:[{value:'openid',label:'登录',oidcOnly:true}],sources:[{id:8,name:'小程序测试源',providerType:'WECHAT',sourceType:'WECHAT_MINIAPP',scene:'MINIAPP',available:true,capabilities:[{codeType:'WECHAT_MINIAPP_LOGIN_CODE',label:'身份登录',scene:'MINIAPP'}]}]}));
  const {wrapper,router}=await open({id:'42',tab:'protocol'});
  expect(wrapper.text()).toContain('第三方Code登录');
  await wrapper.findAll('button').find(button=>button.text()==='添加或管理身份源').trigger('click');await flushPromises();
  expect(router.currentRoute.value.query.tab).toBe('login');expect(wrapper.text()).toContain('小程序测试源');
  await wrapper.findAll('button').find(button=>button.text()==='保存并启用').trigger('click');await flushPromises();
  expect(api.update).toHaveBeenCalled();const body=api.update.mock.calls[0][1];expect(body.protocol).toBe('OIDC');
  expect(body.settings.authorizationGrantTypes).toBe('urn:macula:params:oauth:grant-type:external_code');expect(body.settings.redirectUris).toBe('');
  expect(body.externalProviderBindings).toEqual({WECHAT_MINIAPP_LOGIN_CODE:8});wrapper.unmount();
 });
});


it('shows the Client callback allowlist for AppLink without authorization_code', async () => {
  const application={purpose:'APPLICATION',protocol:'OAUTH2_1',applicationType:'NATIVE',externalCodeTypes:['FEISHU_APP_CODE'],
    settings:{clientAuthenticationMethods:'none',authorizationGrantTypes:'urn:macula:params:oauth:grant-type:external_code',
      redirectUris:'',scopes:'profile',requireProofKey:true}};
  const wrapper=mount(OAuthProtocolSettings,{props:{application,protocol:'OAUTH2_1'},
    global:{plugins:[ElementPlus],mocks:{$t:key=>key}}});
  await flushPromises();
  const callback=wrapper.findAll('.el-form-item').find(item=>item.text().includes('授权回调 URI 白名单'));
  expect(callback).toBeTruthy();
  await callback.find('textarea').setValue('https://app.example/callback');
  expect(application.settings.redirectUris).toBe('https://app.example/callback');
  expect(application.settings.authorizationGrantTypes).not.toContain('authorization_code');
  wrapper.unmount();
});
