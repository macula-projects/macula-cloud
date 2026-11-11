<template><section class="configuration-panel protocol-settings">
<ClientCredentials :client="application" :settings="settings" @copy="value => $emit('copy', value)" @authentication-change="authChanged" />
<div class="section-heading"><h3>授权与接入</h3></div>
    <el-form-item label="授权方式"
      ><div class="grants">
        <template v-for="grant in mainGrants" :key="grant.value"
          ><el-tooltip
            :content="reason(grant.value) || grant.description"
            :enterable="false"
            ><el-checkbox
              :model-value="selected.includes(grant.value)"
              :disabled="disabled(grant.value)"
              :data-grant="grant.value"
              @change="(checked) => toggle(grant.value, checked)"
              >{{ grant.label }} <code>{{ grant.value }}</code></el-checkbox
            ></el-tooltip
          ></template
        >
      </div></el-form-item
    >
    <div v-if="['NATIVE','MINIAPP','IN_APP'].includes(application.applicationType)" class="sdk-login">
      <h4>第三方Code登录</h4>
      <p class="hint">支持飞书、企业微信等SDK及小程序的一次性Code；关联具体身份源后自动启用，客户端认证使用none。</p>
      <el-tag :type="selected.includes(EXTERNAL_CODE_GRANT) ? 'success' : 'info'">{{ selected.includes(EXTERNAL_CODE_GRANT) ? '已启用' : '尚未关联SDK身份源' }}</el-tag>
      <el-button link type="primary" @click="$emit('manage-sources')">添加或管理身份源</el-button>
    </div>
    <p v-for="unknown in unknownGrants" :key="unknown" class="retired-grant">
      已退役或未知授权：{{ unknown }}。请确认接入整改后显式移除。
      <el-button link type="danger" @click="toggle(unknown, false)">移除</el-button>
    </p>
    <el-alert
      v-for="issue in issues"
      :key="issue"
      :title="issue"
      type="error"
      :closable="false"
    />

    <template v-if="application.applicationType !== 'SERVICE'">
    <div class="section-heading"><h3>{{$t("applicationProtocols.callbacksTitle")}}</h3></div>
    <div class="form-grid">
    <el-form-item v-if="selected.includes('authorization_code') || application.externalCodeTypes?.includes('FEISHU_APP_CODE')"
      :label="application.externalCodeTypes?.includes('FEISHU_APP_CODE') ? '授权回调 URI 白名单' : '登录回调 URI'" required>
      <el-input v-model="settings.redirectUris" type="textarea" :rows="2" placeholder="精确回调地址，多个以英文逗号分隔" />
      <p v-if="application.externalCodeTypes?.includes('FEISHU_APP_CODE')" class="hint">{{$t('identitySourceIntegration.appLinkClientCallback')}}</p>
    </el-form-item>
    <el-form-item label="浏览器 Origin"><el-input :model-value="(settings.allowedOrigins || []).join('\n')" type="textarea" :rows="2"
      placeholder="精确 Origin，例如 https://app.example.com" @update:model-value="value=>settings.allowedOrigins=value.split(/[\n,]/).map(item=>item.trim()).filter(Boolean)" /></el-form-item>

    </div>
    </template>
    <div class="section-heading"><h3>{{$t("applicationProtocols.permissionsTitle")}}</h3></div>
    <el-form-item label="Scope" :required="selected.includes('authorization_code')">

      <el-select multiple :model-value="csvValues(client.scopes)" :loading="catalogLoading" :disabled="!catalogLoaded || !availableScopes.length"
        :placeholder="protocol === 'OIDC' ? '选择身份资料权限' : '选择已登记的业务权限'" @update:model-value="values => settings.scopes = values.join(',')">
        <el-option v-for="scope in availableScopes" :key="scope.value" :value="scope.value" :label="scope.label + ' · ' + scope.value" :disabled="protocol === 'OIDC' && scope.value === 'openid'">
          <span>{{ scope.label }} · {{ scope.value }}</span><small class="hint"> {{ scope.description }}</small>
        </el-option>
      </el-select>

      <p v-if="protocol === 'OIDC'" class="hint">{{$t('applicationProtocols.oidcScopeHelp')}}</p>
      <p v-else class="hint">{{$t('applicationProtocols.oauthScopeHelp')}}</p>
      <el-alert v-if="catalogLoaded && !availableScopes.length && !catalogError"
        :title="$t('applicationProtocols.oauthScopeEmpty')" type="info" :closable="false" class="scope-empty" />

      <p v-if="catalogError" class="hint">{{ catalogError }}</p>
      <el-button link @click="$emit('reload')">重新加载目录</el-button>
    </el-form-item>
    <div v-if="application.applicationType !== 'SERVICE'" class="form-grid">
    <el-form-item label="S256 PKCE"
      ><el-switch
        v-model="settings.requireProofKey"
        :disabled="selected.includes('authorization_code') || client.clientAuthenticationMethods === 'none'"
      /><span class="hint">所有授权码流程必须使用S256。</span></el-form-item
    >
    <el-form-item label="授权确认"
      ><el-switch v-model="settings.requireAuthorizationConsent"
    /></el-form-item>
    </div>
    <div class="section-heading"><h3>令牌设置</h3><p>有效期单位为秒。</p></div>
    <div class="form-grid token-policy">
        <el-form-item v-if="selected.includes('authorization_code')" label="授权码有效期"
          ><el-input-number
            v-model="settings.authorizationCodeTimeToLive"
            :min="30"
            :max="3600"
        /></el-form-item>
        <el-form-item label="访问令牌有效期"
          ><el-input-number
            v-model="settings.accessTokenTimeToLive"
            :min="60"
            :max="86400"
        /></el-form-item>
        <el-form-item label="访问令牌格式"
          ><el-radio-group v-model="settings.tokenFormat"><el-radio value="self-contained">JWT</el-radio><el-radio value="reference">不透明令牌</el-radio></el-radio-group></el-form-item>
        <template v-if="selected.includes('refresh_token')"
          ><el-form-item label="刷新令牌有效期"
            ><el-input-number
              v-model="settings.refreshTokenTimeToLive"
              :min="300"
              :max="31536000" /></el-form-item
          ><el-form-item label="复用刷新令牌"
            ><el-switch v-model="settings.reuseRefreshTokens" /></el-form-item
        ></template>
        <el-form-item v-if="protocol === 'OIDC'" label="ID Token 算法"
          ><el-radio-group v-model="settings.idTokenSignatureAlgorithm"><el-radio v-for="alg in algorithms" :key="alg" :value="alg">{{ alg }}</el-radio></el-radio-group></el-form-item>
    </div>
</section></template>
<script setup>
import {computed,watch} from 'vue'
import {ElMessage} from 'element-plus'
import ClientCredentials from './ClientCredentials.vue'
import {AUTHORIZATION_GRANTS,csvValues,configurationIssues,grantIssue,grantDependency} from '@/utils/oauthClientConfiguration'
import {EXTERNAL_CODE_GRANT} from '@/api/model/system/oauth2Client'
import {ensureProtocol,projectProtocol,normalizeProtocol} from '@/utils/applicationProtocolRegistry'
const props=defineProps({application:{type:Object,required:true},protocol:{type:String,required:true},catalog:{type:Object,default:()=>({scopes:[]})},catalogLoaded:Boolean,catalogLoading:Boolean,catalogError:String})
defineEmits(['reload','manage-sources','copy'])
const settings=computed(()=>ensureProtocol(props.application,props.protocol).settings)
const client=computed(()=>projectProtocol(props.application,props.protocol))
const algorithms=['RS256','RS384','RS512']
const mainGrants=computed(()=>AUTHORIZATION_GRANTS.slice(0,3).filter(item=>props.application.applicationType==='SERVICE'?item.value==='client_credentials':item.value!=='client_credentials' && (props.application.applicationType!=='IN_APP' || item.value==='authorization_code')))
const selected=computed(()=>csvValues(settings.value.authorizationGrantTypes))
const unknownGrants=computed(()=>selected.value.filter(value=>!AUTHORIZATION_GRANTS.some(item=>item.value===value)))
const availableScopes=computed(()=>(props.catalog.scopes||[]).filter(scope=>(!scope.oidcOnly || props.protocol==='OIDC') &&
 (!scope.applicationTypes?.length || scope.applicationTypes.includes(props.application.applicationType)) &&
 (!scope.grants?.length || scope.grants.some(grant=>selected.value.includes(grant)))))
const issues=computed(()=>configurationIssues(client.value))
function reason(grant) {return grantIssue(client.value,grant) || (selected.value.includes(grant)?grantDependency(client.value,grant):'')}
function disabled(grant) {return grant==='authorization_code' && props.protocol==='OIDC' && !selected.value.includes(EXTERNAL_CODE_GRANT) || (selected.value.includes(grant)?!!grantDependency(client.value,grant):!!grantIssue(client.value,grant))}
function toggle(grant,checked) {const values=new Set(selected.value);if(checked)values.add(grant);else values.delete(grant);settings.value.authorizationGrantTypes=[...values].join(',');normalizeProtocol(props.application,props.protocol)}
function authChanged() {if(normalizeProtocol(props.application,props.protocol))ElMessage.info('已按认证方式调整当前协议的可用授权；应用密钥保持不变')}
watch(()=>props.application.applicationType,()=>normalizeProtocol(props.application,props.protocol),{immediate:true})
</script>
<style scoped>
:deep(.el-radio-group),.grants {display:flex;flex-wrap:wrap;gap:12px 24px;width:100%;min-width:0;}
.protocol-settings > .section-heading:not(:first-child) {margin-top:28px;padding-top:24px;border-top:1px solid var(--el-border-color-lighter);}
.sdk-login {margin:8px 0 24px;padding:14px 16px;border:1px solid var(--el-border-color-lighter);border-radius:6px;background:var(--el-fill-color-lighter);}
.sdk-login h4 {margin:0 0 6px;font-size:13px;}
.sdk-login .hint {margin:0 0 10px;}
.scope-empty {width:100%;margin-top:12px;}
.token-policy {padding-top:0;}
:deep(.el-radio),:deep(.el-checkbox) {height:auto;min-height:32px;margin-right:0;}
:deep(.el-radio__label),:deep(.el-checkbox__label) {white-space:normal;overflow-wrap:anywhere;}
code {font-size:11px;color:var(--el-text-color-secondary);margin-left:6px;}
.hint {color:var(--el-text-color-secondary);font-size:13px;line-height:1.7;}
</style>
