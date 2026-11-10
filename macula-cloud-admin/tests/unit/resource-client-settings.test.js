import {describe,it,expect} from 'vitest'
import {mount} from '@vue/test-utils'
import ElementPlus from 'element-plus'
import ResourceServerSettings from '@/views/system/oauth2Client/components/ResourceServerSettings.vue'
import {clientPayload,configurationIssues,usesSecret} from '@/utils/oauthClientConfiguration'
const client=()=>({id:1,clientId:'resource',clientName:'网关内省',purpose:'RESOURCE_SERVER',managedKey:'platform-gateway',configurationStatus:'ENABLED',configurationVersion:'fixture',settings:{kind:'oauth2',clientAuthenticationMethods:'client_secret_basic',authorizationGrantTypes:'',scopes:''}})
describe('resource-server settings',()=>{
 it('shows only authentication and introspection settings without login grants',()=>{
  const wrapper=mount(ResourceServerSettings,{props:{client:client()},global:{plugins:[ElementPlus]}})
  expect(wrapper.text()).toContain('资源服务器')
  expect(wrapper.text()).toContain('不可删除、停用')
  expect(wrapper.findAll('.el-form-item__label').map(label=>label.text())).not.toContain('用户类型')
  expect(wrapper.findAll('.el-form-item__label').map(label=>label.text())).not.toContain('授权方式')
  expect(wrapper.text()).not.toContain('允许内省的资源')
  wrapper.unmount()
 })
 it('requires a confidential identity and emits no managed ownership or fake protocol',()=>{
  const row=client();row.clientSecret='temporary-test-secret';expect(usesSecret(row)).toBe(true)
  expect(configurationIssues(row)).toEqual([])
  const payload=clientPayload(row)
  expect(payload.purpose).toBe('RESOURCE_SERVER')
  expect(payload).not.toHaveProperty('managedKey')
  expect(payload).not.toHaveProperty('protocolConfigurations')
  row.settings.clientAuthenticationMethods='none'
  expect(configurationIssues(row).length).toBeGreaterThan(0)
 })
})
