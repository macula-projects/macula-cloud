import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import Page from '@/views/system/identityProvider/index.vue';
import api from '@/api/model/system/identityProvider';
vi.mock('@/api/model/system/identityProvider', () => ({ default: {
  catalog: vi.fn(), list: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn(),
} }));
const catalog = {
  categories: [{ code: 'ENTERPRISE', label: '企业' }],
  providers: [{ code: 'WECOM', label: '企微', category: 'ENTERPRISE' },
    { code: 'FEISHU', label: '飞书', category: 'ENTERPRISE' }],
  sources: [{ code: 'WECOM_SDK', providerType: 'WECOM', scene: 'APP', label: '企微SDK', fields: [], bindingRules: [],
    flows: [{ value: 'WECOM_SDK_V1', label: 'SDK v1', available: true }],
    directoryPolicies: ['IDENTITY_ONLY', 'OPTIONAL', 'REQUIRED', 'BACKGROUND'] },
  { code: 'FEISHU_APPLINK', providerType: 'FEISHU', scene: 'APP', label: 'App Link', fields: [], bindingRules: [],
    flows: [{ value: 'FEISHU_APPLINK_V3', label: 'App Link v3', available: false, providerPkce: true }],
    directoryPolicies: ['IDENTITY_ONLY', 'REQUIRED'] }],
};
async function page() {
  const wrapper = mount(Page, { global: { plugins: [ElementPlus], mocks: { $t: key => key },
    stubs: { scTable: true, scSelectFilter: true, 'el-table-column': true } } });
  await flushPromises();
  return wrapper;
}
beforeEach(() => {
  vi.clearAllMocks();
  api.catalog.mockResolvedValue({ success: true, data: catalog });
});
describe('identity source version and directory policy', () => {
  it('clears source-specific settings when choosing a different source', async () => {
    const wrapper = await page();
    const state = wrapper.vm.$.setupState;
    state.open();
    Object.assign(state.form, { sourceType: 'WECOM_SDK', directoryPolicy: 'REQUIRED', organizationId: 'old-corp',
      oauthRedirectUri: 'https://old.example/callback', autoBindEnabled: true, bindingRule: 'old-rule' });
    state.changeSource();
    expect(state.form.identityFlow).toBe('WECOM_SDK_V1');
    expect(state.form.directoryPolicy).toBe('IDENTITY_ONLY');
    expect(state.form.organizationId).toBeNull();
    expect(state.form.oauthRedirectUri).toBeNull();
    expect(state.form.autoBindEnabled).toBe(false);
    wrapper.unmount();
  });
  it('preserves disabled unverified records and refuses enabling them', async () => {
    const wrapper = await page();
    const state = wrapper.vm.$.setupState;
    state.open({ id: 8, providerType: 'FEISHU', sourceType: 'FEISHU_APPLINK', name: 'pending source',
      identityFlow: 'FEISHU_APPLINK_V3', directoryPolicy: 'IDENTITY_ONLY', enabled: false, config: {},
      oauthRedirectUri: 'https://app.example/callback' });
    expect(state.payload()).not.toHaveProperty('identityFlow');
    state.form.enabled = true;
    expect(() => state.payload()).toThrow('尚待核实');
    wrapper.unmount();
  });
  it('requires fixed organization and preserves policy metadata on status updates', async () => {
    const wrapper = await page();
    const state = wrapper.vm.$.setupState;
    const row = { id: 7, name: 'corp source', providerType: 'WECOM', sourceType: 'WECOM_SDK',
      identityFlow: 'WECOM_SDK_V1', directoryPolicy: 'REQUIRED', organizationId: 'corp',
      version: 3, enabled: true, visible: true, config: { corpId: 'corp' } };
    state.open(row);
    expect(state.originalOrganizationId).toBe('corp');
    state.form.organizationId = null;
    expect(state.payload()).not.toHaveProperty('directoryPolicy');
    api.update.mockResolvedValue({ success: true, data: true });
    await state.toggle(row);
    expect(api.update).toHaveBeenCalledWith(7, expect.objectContaining({ organizationId: 'corp', version: 3, enabled: false }));
    wrapper.unmount();
  });
});


describe('identity source list failures and scene guidance', () => {
  it('surfaces the backend failure before the generic table parser reads null data', async () => {
    const wrapper = await page();
    const state = wrapper.vm.$.setupState;
    api.list.mockResolvedValue({ success: false, code: '409', msg: '身份源附加密钥不是有效 JSON 对象', data: null });
    await expect(state.tableApi.get({ pageNum: 1 })).rejects.toMatchObject({
      message: '身份源附加密钥不是有效 JSON 对象', statusText: '身份源附加密钥不是有效 JSON 对象',
    });
    api.list.mockResolvedValue({ success: true, code: '00000', data: { records: [], total: 0 } });
    await expect(state.tableApi.get({ pageNum: 1 })).resolves.toMatchObject({ data: { records: [], total: 0 } });
    api.list.mockResolvedValue({ success: true, data: null });
    await expect(state.tableApi.get({})).rejects.toMatchObject({ statusText: '认证源列表数据无效' });
    wrapper.unmount();
  });
  it('explains separate WeChat credentials and both shared WeCom webpage modes', async () => {
    const wrapper = await page();
    const state = wrapper.vm.$.setupState;
    for (const [source, key] of [
      ['WECHAT_WEBSITE', 'wechatWebsiteHelp'], ['WECHAT_OFFICIAL_ACCOUNT', 'wechatOfficialHelp'],
      ['WECOM_WEB', 'wecomWebHelp'],
    ]) {
      state.form.sourceType = source;
      expect(state.sourceSceneHelpKey).toBe(`identitySourceIntegration.${key}`);
    }
    state.form.sourceType = 'WECOM_SDK';
    expect(state.sourceSceneHelpKey).toBeUndefined();
    wrapper.unmount();
  });
});


describe('controlled source to IAM field mapping', () => {
  it('preserves an explicit mapping and omits internal strategy fields', async () => {
    const wrapper = await page();
    const state = wrapper.vm.$.setupState;
    state.sources[0].bindingMappings = [
      {sourceField:'member_id',sourceLabel:'成员 ID',targetField:'username',targetLabel:'用户名'},
    ];
    state.open({id:7,name:'mapped source',providerType:'WECOM',sourceType:'WECOM_SDK',autoBindEnabled:true,
      bindingSourceField:'member_id',bindingTargetField:'username',identityFlow:'WECOM_SDK_V1',directoryPolicy:'REQUIRED',config:{},enabled:true});
    expect(state.payload()).toMatchObject({bindingMappings:[{sourceField:'member_id',targetField:'username'}]});
    expect(state.payload()).not.toHaveProperty('bindingRule');
    expect(state.payload()).not.toHaveProperty('identityFlow');
    expect(state.payload()).not.toHaveProperty('directoryPolicy');
    state.form.bindingMappings[0].targetField = 'email';
    expect(() => state.payload()).toThrow('源字段和IAM字段');
    wrapper.unmount();
  });
});


describe('concrete app registration and multiple login capabilities', () => {
  it('renders shared WeCom credentials once and submits several explicit capabilities', async () => {
    const fields = [{key:'corpId',label:'企业ID',required:true},{key:'agentId',label:'Agent ID',required:true},
      {key:'appSecret',label:'应用Secret',required:true,secret:true}];
    const source = {...catalog.sources[0],code:'WECOM_WEB',scene:'BROWSER',fields,defaultAvailable:true};
    const capabilities = [{code:'WECOM_BROWSER',label:'PC网页扫码',available:true,browser:true},
      {code:'WECOM_WEBVIEW',label:'企微端内网页',available:true,browser:true},
      {code:'WECOM_MINIAPP',label:'关联小程序登录',available:true,browser:false}];
    api.catalog.mockResolvedValue({success:true,data:{...catalog,sources:[source,
      {code:'FEISHU_WEB',providerType:'FEISHU',fields:[],defaultAvailable:true}],credentials:[{
      code:'WECOM_APPLICATION',label:'企业微信应用',providerType:'WECOM',sourceType:'WECOM_WEB',fields,capabilities},
      {code:'FEISHU_APPLICATION',label:'飞书应用',providerType:'FEISHU',sourceType:'FEISHU_WEB',fields:[],
        capabilities:[{code:'FEISHU_WEB',label:'网页授权',available:true,browser:true}]}]}});
    const wrapper = await page(); const state = wrapper.vm.$.setupState;
    state.open();
    expect(state.form.credentialType).toBe('WECOM_APPLICATION');
    expect(state.form.sourceType).toBe('WECOM_WEB');
    expect(state.form.loginCapabilities).toEqual(['WECOM_BROWSER']);
    await flushPromises();
    expect(wrapper.findAll('.el-form-item__label').some(item => item.text() === '应用类型')).toBe(false);
    Object.assign(state.form,{name:'统一企微应用',loginCapabilities:capabilities.map(item=>item.code),
      config:{corpId:'corp',agentId:'1001',appSecret:'fixture-secret'}});
    await flushPromises();
    expect(state.fields).toHaveLength(3);
    expect(state.payload()).toMatchObject({credentialType:'WECOM_APPLICATION',loginCapabilities:capabilities.map(item=>item.code),
      config:{corpId:'corp',agentId:'1001',appSecret:'fixture-secret'}});
    state.form.loginCapabilities = ['WECOM_MINIAPP'];
    expect(state.payload().config.appSecret).toBe('fixture-secret');
    expect(state.payload().loginCapabilities).toEqual(['WECOM_MINIAPP']);
    state.form.providerType = 'FEISHU'; state.changeType();
    expect(state.form.credentialType).toBe('FEISHU_APPLICATION');
    expect(state.form.sourceType).toBe('FEISHU_WEB');
    expect(state.form.config).toEqual({});
    expect(state.form.loginCapabilities).toEqual(['FEISHU_WEB']);
    const existing = {id:7,name:'已有企微',providerType:'WECOM',credentialType:'WECOM_APPLICATION',sourceType:'WECOM_SDK',
      loginCapabilities:['WECOM_MINIAPP'],config:{corpId:'corp',agentId:'1001'},configuredSecrets:{appSecret:true},
      organizationId:'fixed-corp',identityFlow:'WECOM_SDK_V1',autoBindEnabled:true,bindingSourceField:'member_id',bindingTargetField:'username'};
    state.open(existing);
    expect(state.form.sourceType).toBe('WECOM_SDK');
    expect(state.form.loginCapabilities).toEqual(['WECOM_MINIAPP']);
    expect(state.form.config).toEqual(existing.config);
    expect(state.form.organizationId).toBe('fixed-corp');
    expect(state.form.bindingSourceField).toBe('member_id');
    expect(state.configuredSecrets).toEqual({appSecret:true});
    wrapper.unmount();
  });
  it('keeps four WeChat registrations separate and clears credentials on creation type change', async () => {
    const types=['WECHAT_WEBSITE','WECHAT_MOBILE_APP','WECHAT_OFFICIAL_ACCOUNT','WECHAT_MINIAPP'];
    api.catalog.mockResolvedValue({success:true,data:{categories:[{code:'SOCIAL',label:'社交'}],
      providers:[{code:'WECHAT',category:'SOCIAL',label:'微信'}],sources:types.map(code=>({code,providerType:'WECHAT',fields:[],defaultAvailable:true})),
      credentials:types.map(code=>({code,label:code,providerType:'WECHAT',sourceType:code,fields:[],capabilities:[{code:code==='WECHAT_MINIAPP'?'WECHAT_MINIAPP_LOGIN':code,available:true}]}))}});
    const wrapper=await page();const state=wrapper.vm.$.setupState;state.open();
    expect(state.form.credentialType).toBe('');
    await flushPromises();
    expect(wrapper.findAll('.el-form-item__label').some(item => item.text() === '应用类型')).toBe(true);
    state.form.credentialType='WECHAT_WEBSITE';state.changeCredential();state.form.config={appId:'website',appSecret:'website-secret'};
    state.form.credentialType='WECHAT_MOBILE_APP';state.changeCredential();
    expect(state.form.config).toEqual({});
    expect(state.form.sourceType).toBe('WECHAT_MOBILE_APP');
    expect(state.form.loginCapabilities).toEqual(['WECHAT_MOBILE_APP']);
    wrapper.unmount();
  });
});


it('offers web, mobile SDK and in-app login for one DingTalk registration with one credential set', async () => {
  const fields=[{key:'appKey',label:'Client ID / AppKey',required:true},{key:'appSecret',label:'AppSecret',secret:true,required:true},{key:'corpId',label:'企业ID',required:true}];
  const capabilities=[{code:'DINGTALK_WEB',label:'网页授权',available:true,browser:true},
    {code:'DINGTALK_SDK',label:'移动SDK授权登录',available:true,browser:false},
    {code:'DINGTALK_WEBVIEW',label:'端内免登',available:true,browser:false}];
  api.catalog.mockResolvedValue({success:true,data:{categories:[{code:'enterprise',label:'企业'}],
    providers:[{code:'DINGTALK',label:'钉钉',category:'enterprise'}],
    sources:[{code:'DINGTALK_WEB',providerType:'DINGTALK',fields,defaultAvailable:true}],
    credentials:[{code:'DINGTALK_APPLICATION',providerType:'DINGTALK',sourceType:'DINGTALK_WEB',fields,capabilities}]}});
  const wrapper=await page();const state=wrapper.vm.$.setupState;state.open();await flushPromises();
  expect(state.form.credentialType).toBe('DINGTALK_APPLICATION');
  const mobile=wrapper.findAll('.el-checkbox').find(item=>item.text().includes('移动SDK授权登录'));
  expect(mobile.find('input').attributes('disabled')).toBeUndefined();
  await mobile.find('input').setValue(true);
  expect(state.form.loginCapabilities).toContain('DINGTALK_SDK');
  Object.assign(state.form,{name:'统一钉钉',loginCapabilities:capabilities.map(item=>item.code),config:{appKey:'ding-client',appSecret:'fixture-secret',corpId:'corp'}});
  expect(state.payload()).toMatchObject({credentialType:'DINGTALK_APPLICATION',loginCapabilities:capabilities.map(item=>item.code),config:{appKey:'ding-client',appSecret:'fixture-secret',corpId:'corp'}});
  wrapper.unmount();
});


describe('Feishu AppLink callback belongs to app code', () => {
  it('omits the source callback even when a legacy record contains an invalid value', async () => {
    const wrapper = await page(); const state = wrapper.vm.$.setupState;
    state.open({id:8,providerType:'FEISHU',sourceType:'FEISHU_APPLINK',name:'callback fixture',
      identityFlow:'FEISHU_APPLINK_V3',enabled:false,config:{},organizationId:' tenant-fixture ',
      oauthRedirectUri:' http://127.0.0.1:9010/login/feishu'});
    await flushPromises();
    expect(state.payload()).toMatchObject({organizationId:'tenant-fixture',bindingMappings:[]});
    expect(state.payload()).not.toHaveProperty('oauthRedirectUri');
    expect(wrapper.find('input[placeholder="https://app.example/callback"]').exists()).toBe(false);
    wrapper.unmount();
  });
  it('edits multiple mappings without losing either row and rejects duplicates', async () => {
    api.catalog.mockResolvedValue({success:true,data:{...catalog,sources:[{
      code:'FEISHU_WEB',providerType:'FEISHU',fields:[],defaultAvailable:true,
      bindingMappings:[{sourceField:'mobile',sourceLabel:'手机号',targetField:'mobile',targetLabel:'手机号'},
        {sourceField:'email',sourceLabel:'邮箱',targetField:'email',targetLabel:'邮箱'}],
    }]}});
    const wrapper = await page(); const state = wrapper.vm.$.setupState;
    const mappings=[{sourceField:'mobile',targetField:'mobile'},{sourceField:'email',targetField:'email'}];
    state.open({id:8,providerType:'FEISHU',sourceType:'FEISHU_WEB',name:'mapped fixture',enabled:true,
      autoBindEnabled:true,organizationId:'tenant',config:{},bindingMappings:mappings});
    await flushPromises();
    expect(wrapper.findAll('[data-binding-rule]')).toHaveLength(2);
    expect(state.payload().bindingMappings).toEqual(mappings);
    state.form.bindingMappings[1]={sourceField:'mobile',targetField:'mobile'};
    expect(() => state.payload()).toThrow('重复');
    expect(mappings[1].sourceField).toBe('email');
    state.form.bindingMappings.pop();
    expect(state.payload().bindingMappings).toEqual([mappings[0]]);
    wrapper.unmount();
  });
});
