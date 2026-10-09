<template>
	<div>
		<div class="toolbar">
			<el-button type="primary" @click="createDialog.open()">新增接入应用</el-button>
			<div class="search">
				<el-input v-model="keywords" clearable placeholder="按应用备注搜索" @keyup.enter="load(1)" />
				<el-button @click="load(1)">查询</el-button>
			</div>
		</div>
		<el-alert type="info" :closable="false" show-icon
			title="第二步：为已配置完整的发号业务创建接入应用。应用可整体删除；Token 不轮换、不停用，业务授权只能增加。" />
		<el-table v-loading="loading" :data="records" stripe>
			<el-table-column prop="remark" label="应用（备注）" min-width="180" />
			<el-table-column label="Token" min-width="360">
				<template #default="scope">
					<code>{{ scope.row.token }}</code>
					<el-button link type="primary" @click="copyToken(scope.row.token)">复制</el-button>
				</template>
			</el-table-column>
			<el-table-column label="授权业务" min-width="220">
				<template #default="scope">
					<el-tag v-for="item in scope.row.bizTypes" :key="item" class="tag">{{ item }}</el-tag>
				</template>
			</el-table-column>
			<el-table-column prop="createTime" label="创建时间" width="180" />
			<el-table-column label="操作" width="250" fixed="right">
				<template #default="scope">
					<el-button link type="primary" @click="remarkDialog.open(scope.row)">修改备注</el-button>
					<el-button link type="primary" @click="authorizationDialog.open(scope.row)">增加授权</el-button>
					<el-button link type="danger" @click="removeApplication(scope.row)">删除应用</el-button>
				</template>
			</el-table-column>
		</el-table>
		<el-pagination class="pagination" background layout="total, prev, pager, next" :total="total"
			:page-size="pageSize" v-model:current-page="page" @current-change="load" />
		<ApplicationCreateDialog ref="createDialog" @success="load(1)" />
		<ApplicationRemarkDialog ref="remarkDialog" @success="load(page)" />
		<ApplicationAuthorizationDialog ref="authorizationDialog" @success="load(page)" />
	</div>
</template>

<script setup>
import {onMounted, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import api from '@/api/model/tinyid/management'
import ApplicationCreateDialog from './ApplicationCreateDialog.vue'
import ApplicationRemarkDialog from './ApplicationRemarkDialog.vue'
import ApplicationAuthorizationDialog from './ApplicationAuthorizationDialog.vue'

const loading = ref(false)
const records = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const keywords = ref('')
const createDialog = ref()
const remarkDialog = ref()
const authorizationDialog = ref()

async function load(targetPage = page.value) {
	loading.value = true
	page.value = targetPage
	try {
		const response = await api.applications.list({page: targetPage, pageSize, keywords: keywords.value}).catch(() => null)
		if (!response) return
		if (!response.success) {
			ElMessage.error(response.cause || response.msg || '操作失败')
			return
		}
		records.value = response.data.records
		total.value = response.data.total
	} finally {
		loading.value = false
	}
}

async function copyToken(token) {
	await navigator.clipboard.writeText(token)
	ElMessage.success('Token 已复制')
}

async function removeApplication(application) {
	try {
		await ElMessageBox.confirm(
			`确定删除接入应用“${application.remark}”吗？数据库授权及当前服务实例缓存会立即失效，其他实例将在定时刷新或重启后收敛。`,
			'删除接入应用',
			{type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'}
		)
	} catch {
		return
	}
	const response = await api.applications.delete(application.appId).catch(() => null)
	if (!response) return
	if (!response.success) {
		ElMessage.error(response.cause || response.msg || '操作失败')
		return
	}
	ElMessage.success('接入应用已删除')
	await load(records.value.length === 1 && page.value > 1 ? page.value - 1 : page.value)
}

onMounted(() => load())
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; margin-bottom: 12px; }
.search { display: flex; gap: 8px; width: 360px; }
.el-alert { margin-bottom: 12px; }
.tag { margin: 2px 4px 2px 0; }
.pagination { justify-content: flex-end; margin-top: 16px; }
code { margin-right: 8px; word-break: break-all; }
</style>
