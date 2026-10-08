<template>
	<div>
		<div class="toolbar">
			<el-input v-model="operator" clearable placeholder="按操作人搜索" @keyup.enter="load(1)" />
			<el-button @click="load(1)">查询</el-button>
		</div>
		<el-table v-loading="loading" :data="records" stripe>
			<el-table-column prop="operator" label="操作人" width="150" />
			<el-table-column prop="action" label="操作" min-width="190" />
			<el-table-column prop="requestMethod" label="方法" width="90" />
			<el-table-column prop="requestUri" label="请求路径" min-width="280" />
			<el-table-column label="结果" width="90">
				<template #default="scope"><el-tag :type="scope.row.success ? 'success' : 'danger'">{{ scope.row.success ? '成功' : '失败' }}</el-tag></template>
			</el-table-column>
			<el-table-column prop="errorSummary" label="错误摘要（已脱敏）" min-width="220" show-overflow-tooltip />
			<el-table-column prop="clientIp" label="客户端 IP" width="150" />
			<el-table-column prop="createTime" label="时间" width="180" />
		</el-table>
		<el-pagination class="pagination" background layout="total, prev, pager, next" :total="total"
			:page-size="pageSize" v-model:current-page="page" @current-change="load" />
	</div>
</template>

<script setup>
import {onMounted, ref} from 'vue'
import api from '@/api/model/tinyid/management'

const records = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const operator = ref('')
const loading = ref(false)

async function load(targetPage = page.value) {
	loading.value = true
	page.value = targetPage
	try {
		const response = await api.auditLogs({page: targetPage, pageSize, operator: operator.value})
		records.value = response.records
		total.value = response.total
	} finally {
		loading.value = false
	}
}

onMounted(() => load())
</script>

<style scoped>
.toolbar { display: flex; justify-content: flex-end; gap: 8px; margin-bottom: 12px; }
.toolbar .el-input { width: 280px; }
.pagination { justify-content: flex-end; margin-top: 16px; }
</style>
