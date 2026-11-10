import {describe,it,expect} from 'vitest';
import {ensureProtocol,selectProtocol,enabledProtocolCodes,normalizeProtocol,managementProtocol,projectProtocol} from '@/utils/applicationProtocolRegistry';
const app=()=>({applicationType:'WEB_SERVER',purpose:'APPLICATION',clientSecret:'shared-secret'});
describe('single application protocol',()=>{
 it('replaces settings and source bindings when selecting another protocol',()=>{
  const client=app();selectProtocol(client,'OIDC');client.settings.redirectUris='https://old.example/cb';client.settings.accessTokenTimeToLive=120;client.externalCodeTypes=['old'];
  selectProtocol(client,'OAUTH2_1');expect(enabledProtocolCodes(client)).toEqual(['OAUTH2_1']);expect(client.settings.redirectUris).toBe('');expect(client.settings.accessTokenTimeToLive).toBe(3600);expect(client.externalCodeTypes).toEqual([]);
  expect(client.protocolConfigurations).toBeUndefined();expect(()=>ensureProtocol(client,'OIDC')).toThrow();
 });
 it('permits OAuth profile but strips openid and unsafe public grants',()=>{
  const client=app();selectProtocol(client,'OAUTH2_1');Object.assign(client.settings,{clientAuthenticationMethods:'none',authorizationGrantTypes:'authorization_code,client_credentials,refresh_token',scopes:'openid,profile,system:access',requireProofKey:false});
  normalizeProtocol(client);expect(client.settings.scopes).toBe('profile,system:access');expect(client.settings.authorizationGrantTypes).toBe('authorization_code');expect(client.settings.requireProofKey).toBe(true);expect(client.clientSecret).toBe('shared-secret');
 });
 it('separates resource-server management from application grants',()=>{
  const client=app();selectProtocol(client,'OIDC');expect(managementProtocol(client)).toBe(null);client.purpose='RESOURCE_SERVER';expect(managementProtocol(client)).toBe('RESOURCE_SERVER');
  expect(()=>selectProtocol(client,'SAML')).toThrow();client.applicationType='SERVICE';expect(()=>selectProtocol(client,'OIDC')).toThrow();
 });
 it('projects only the current settings and removes user profile from service grants',()=>{
  const client=app();selectProtocol(client,'OAUTH2_1');client.settings.allowedOrigins=['https://app.example'];expect(projectProtocol(client).allowedOrigins).toEqual(['https://app.example']);expect(projectProtocol(client,'OIDC').scopes).toBe('');
  client.applicationType='SERVICE';normalizeProtocol(client);expect(client.settings.scopes).not.toContain('profile');expect(client.settings.authorizationGrantTypes).toBe('client_credentials');
 });
});

describe('OIDC SDK application',()=>{
 const external='urn:macula:params:oauth:grant-type:external_code';
 it('preserves SDK-only OIDC grants and bindings without inserting a browser callback',()=>{
  const client={applicationType:'SPA',purpose:'APPLICATION'};selectProtocol(client,'OIDC');
  client.settings.authorizationGrantTypes=external;client.externalCodeTypes=['WECOM_APP_CODE'];client.externalProviderBindings={WECOM_APP_CODE:17};
  normalizeProtocol(client);expect(client.settings.authorizationGrantTypes).toBe(external);expect(client.settings.redirectUris).toBe('');
  expect(projectProtocol(client).externalProviderBindings).toEqual({WECOM_APP_CODE:17});expect(projectProtocol(client).scopes).toBe('openid,profile');
  client.settings.authorizationGrantTypes='';normalizeProtocol(client);expect(client.settings.authorizationGrantTypes).toBe('');
 });
 it('offers OIDC defaults for miniapps with SDK code and no browser grant',()=>{
  const client={applicationType:'MINIAPP',purpose:'APPLICATION'};selectProtocol(client,'OIDC');normalizeProtocol(client);
  expect(client.settings.authorizationGrantTypes).toBe(external);expect(client.settings.clientAuthenticationMethods).toBe('none');expect(client.settings.redirectUris).toBe('');expect(client.settings.scopes).toContain('openid');
 });
});
