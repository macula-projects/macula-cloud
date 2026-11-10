<template>
    <main class="callback">
        <el-result v-if="failed" icon="error" :title="$t('login.iamFailed')">
            <template #extra><el-button type="primary" @click="$router.replace('/login')">{{ $t('login.iamRetry') }}</el-button></template>
        </el-result>
        <p v-else role="status">{{ $t('login.iamInitializing') }}</p>
    </main>
</template>
<script setup>
import {ref, onMounted} from 'vue'
defineOptions({name: 'AdminAuthorizationCallback'})
import {useRouter} from 'vue-router'
import config from '@/config'
import {completeAuthorization} from '@/utils/adminAuthorizationCode'
import {ownsSession} from '@/utils/adminAuthSession'
import {initializeSession} from '@/utils/adminAuthBootstrap'
const failed = ref(false)
const router = useRouter()
onMounted(async () => {
    try {
        const {session, returnPath} = await completeAuthorization(config)
        await initializeSession(session)
        if (!ownsSession(session)) throw new Error('AUTH_SUPERSEDED')
        await router.replace(returnPath)
    } catch { failed.value = true }
})
</script>
<style scoped>.callback {min-height:100vh; display:flex; align-items:center; justify-content:center; padding:24px;}</style>
