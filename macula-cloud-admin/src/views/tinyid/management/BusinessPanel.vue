<template>
	<div>
		<div class="toolbar">
			<div class="actions">
				<el-input v-model="keywords" clearable placeholder="按 biz_type 搜索" @keyup.enter="load(1)" />
				<el-button @click="load(1)">查询</el-button>
				<el-button type="primary" @click="createDialog.open()">新增业务</el-button>
			</div>
		</div>
		<el-alert type="info" :closable="false" show-icon
			title="第一步：创建发号业务，系统会同时配置全部数据源并自动分配 remainder；完成后再创建接入应用并授权业务。" />
		<el-table v-loading="loading" :data="records" stripe row-key="bizType">
			<el-table-column type="expand">
				<template #default="scope">
					<el-table :data="scope.row.dataSources" border class="instance-table">
						<el-table-column prop="sequence" label="顺序" width="80" />
						<el-table-column prop="dataSourceKey" label="数据源" min-width="150" />
						<el-table-column label="健康状态" width="110">
							<template #default="instance"><el-tag :type="instance.row.healthy ? 'success' : 'danger'">{{ instance.row.healthy ? '正常' : '不可用' }}</el-tag></template>
						</el-table-column>
						<el-table-column prop="remainder" label="remainder（只读）" width="160" />
						<el-table-column prop="maxId" label="max_id" min-width="130" />
						<el-table-column prop="version" label="version" width="100" />
						<el-table-column prop="updateTime" label="更新时间" min-width="180" />
					</el-table>
				</template>
			</el-table-column>
			<el-table-column prop="bizType" label="biz_type" min-width="200" />
			<el-table-column prop="step" label="step" width="120" />
			<el-table-column prop="delta" label="delta" width="100" />
			<el-table-column label="一致性状态" width="140">
				<template #default="scope">
					<el-tag :type="statusType(scope.row.consistencyStatus)">{{ statusText(scope.row.consistencyStatus) }}</el-tag>
				</template>
			</el-table-column>
			<el-table-column label="配置约束" min-width="260">
				<template #default>step、delta、remainder 创建后不可修改</template>
			</el-table-column>
			<el-table-column label="操作" width="120" fixed="right">
				<template #default="scope">
					<el-button link type="danger" :disabled="!canDelete(scope.row)"
						:title="canDelete(scope.row) ? '' : '所有数据源的 max_id 都为 0 时才可删除'"
						@click="removeBusiness(scope.row)">删除业务</el-button>
				</template>
			</el-table-column>
		</el-table>
		<el-pagination class="pagination" background layout="total, prev, pager, next" :total="total"
			:page-size="pageSize" v-model:current-page="page" @current-change="load" />
		<BusinessCreateDialog ref="createDialog" @success="load(1)" />
	</div>
</template>

<script setup>
import {onMounted, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import api from '@/api/model/tinyid/management'
import BusinessCreateDialog from './BusinessCreateDialog.vue'

const records = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const keywords = ref('')
const loading = ref(false)
const createDialog = ref()

async function load(targetPage = page.value) {
	loading.value = true
	page.value = targetPage
	try {
		const response = await api.businesses.list({page: targetPage, pageSize, keywords: keywords.value})
		records.value = response.records
		total.value = response.total
	} finally {
		loading.value = false
	}
}

function statusText(status) {
	return {COMPLETE: '配置完整', PENDING: '待补齐', CONFLICT: '配置冲突'}[status] || '未知'
}

function statusType(status) {
	return {COMPLETE: 'success', PENDING: 'warning', CONFLICT: 'danger'}[status] || 'info'
}

function canDelete(business) {
	return business.dataSources?.length > 0 && business.dataSources.every(instance =>
		instance.healthy && instance.id != null && Number(instance.maxId) === 0
	)
}

async function removeBusiness(business) {
	try {
		await ElMessageBox.confirm(
			`确定删除发号业务“${business.bizType}”吗？对应的应用授权也会被删除。`,
			'删除发号业务',
			{type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'}
		)
	} catch {
		return
	}
	await api.businesses.delete(business.bizType)
	ElMessage.success('发号业务已删除')
	await load(records.value.length === 1 && page.value > 1 ? page.value - 1 : page.value)
}

onMounted(() => load(1))
</script>

<style scoped>
.toolbar { display: flex; justify-content: flex-end; margin-bottom: 12px; }
.actions { display: flex; align-items: center; gap: 8px; }
.actions .el-input { width: 230px; }
.el-alert { margin-bottom: 12px; }
.instance-table { margin: 0 32px; width: calc(100% - 64px); }
.pagination { justify-content: flex-end; margin-top: 16px; }
</style>
