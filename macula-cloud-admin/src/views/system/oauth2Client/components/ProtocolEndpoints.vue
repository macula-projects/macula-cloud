<template>
 <el-collapse class="protocol-endpoints"><el-collapse-item :title="$t('applicationProtocols.endpoints')" name="endpoints">
  <el-alert v-if="!metadata?.available" :title="metadata?.message || '接入地址暂不可用'" type="warning" :closable="false" />
  <el-button link :loading="loading" @click="$emit('reload')">重新获取</el-button>
  <div v-for="[key,value] in endpoints" :key="key" class="endpoint-row"><strong>{{labels[key] || key}}</strong><span>{{value}}</span><el-button link @click="$emit('copy',value)">复制</el-button></div>
  <p>共享的授权、Token 等端点可以相同；协议发现及专属端点分别展示。</p>
  <p v-if="management">内省/撤销调用方认证使用：{{management}}。不随默认协议切换，也不尝试另一协议的公钥。</p>
 </el-collapse-item></el-collapse>
</template>
<script setup>
import {computed} from 'vue'
const props=defineProps({service:Boolean,protocol:String,metadata:Object,loading:Boolean,management:String})
defineEmits(['reload','copy'])
const labels={issuer:'Issuer',discovery:'Discovery',authorization_endpoint:'授权地址',token_endpoint:'Token 地址',jwks_uri:'平台 JWKS',userinfo_endpoint:'UserInfo',end_session_endpoint:'退出地址',introspection_endpoint:'令牌内省',revocation_endpoint:'令牌撤销'}
const endpoints=computed(()=>Object.entries(props.metadata?.endpoints||{}).filter(([key])=>(!props.service || !['authorization_endpoint','userinfo_endpoint','end_session_endpoint'].includes(key)) && (props.protocol==='OIDC'||!['userinfo_endpoint','end_session_endpoint'].includes(key))))
</script>
<style scoped>.protocol-endpoints {margin-top:24px;}.endpoint-row {display:flex;gap:16px;align-items:flex-start;padding:8px 0;}.endpoint-row strong {min-width:110px;}.endpoint-row span {flex:1;min-width:0;overflow-wrap:anywhere;}@media(max-width:600px){.endpoint-row {flex-wrap:wrap;}.endpoint-row span {flex-basis:100%;}}</style>
