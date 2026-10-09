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
				title="创建发号业务，系统会同时配置全部数据源并自动分配 remainder；发号访问由网关统一认证。" />
		<el-collapse class="config-help">
			<el-collapse-item title="配置规则与状态说明（点击展开）" name="rules">
				<ul>
					<li>biz_type 表示独立业务；创建时同时写入全部数据源，begin_id 和 max_id 初始为 0。</li>
					<li>step 是每次申请号段的步长，必须大于 0；同一业务在全部数据源中的 step、delta 必须一致。</li>
					<li>delta 是 ID 增量，也是预留的数据库实例容量，默认 10，创建时可调整；数据源数量不能超过 delta。</li>
					<li>remainder 按数据源顺序从 0 自动分配，必须等于该库的顺序号，互不重复，且满足 0 ≤ remainder &lt; delta。</li>
					<li>例如 3 个数据库、delta=10，各库 remainder 为 0、1、2；扩容时只能追加 3、4…9，已有数据源不可重排、删除或中间插入。</li>
					<li>step、delta、remainder 创建后不可通过管理页面修改；新增库的既有业务需由运维补齐。</li>
					<li>配置完整：所有数据源可用、均存在该业务，且参数满足一致性检查。</li>
					<li>配置冲突：已读取到的配置中，step 或 delta 不一致，或 remainder 重复、越界、与数据源顺序不符。请展开业务行对照各库参数，由运维核对原配置和部署顺序；不要直接修改已发号业务的参数。</li>
					<li>待补齐：未发现上述参数冲突，但存在不可用的数据源或缺少业务配置的库；请先恢复连接并核对业务数据。冲突与缺失同时存在时优先显示配置冲突。</li>
					<li>各库 max_id、version 和更新时间可以不同，不属于配置冲突；仅所有库均可用、业务存在且 max_id 都为 0 时允许删除。</li>
				</ul>
			</el-collapse-item>
		</el-collapse>
		<el-table v-loading="loading" :data="records" stripe row-key="bizType">
			<el-table-column type="expand">
				<template #default="scope">
					<el-table :data="scope.row.dataSources" border class="instance-table">
						<el-table-column prop="sequence" label="顺序" width="80" />
						<el-table-column prop="dataSourceKey" label="数据源" min-width="150" />
						<el-table-column label="业务配置" width="120">
							<template #default="instance">{{ !instance.row.healthy ? '无法读取' : instance.row.id == null ? '缺少业务' : '已配置' }}</template>
						</el-table-column>
						<el-table-column prop="step" label="step" width="120" />
						<el-table-column prop="delta" label="delta" width="100" />
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
					<el-tooltip :content="statusDescription(scope.row.consistencyStatus)" placement="top" :popper-style="{maxWidth: '360px'}">
						<el-tag :type="statusType(scope.row.consistencyStatus)">{{ statusText(scope.row.consistencyStatus) }}</el-tag>
					</el-tooltip>
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
		const response = await api.businesses.list({page: targetPage, pageSize, keywords: keywords.value}).catch(() => null)
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

function statusText(status) {
	return {COMPLETE: '配置完整', PENDING: '待补齐', CONFLICT: '配置冲突'}[status] || '未知'
}

function statusType(status) {
	return {COMPLETE: 'success', PENDING: 'warning', CONFLICT: 'danger'}[status] || 'info'
}

function statusDescription(status) {
	return {
		COMPLETE: '全部数据源可用且业务配置一致。',
		PENDING: '存在不可用的数据源或缺少业务配置的库，请展开业务行查看。',
		CONFLICT: 'step、delta 不一致，或 remainder 重复、越界、与数据源顺序不符。请展开业务行对照参数，详见上方配置规则。'
	}[status] || '暂时无法确定配置状态，请刷新后重试。'
}

function canDelete(business) {
	return business.dataSources?.length > 0 && business.dataSources.every(instance =>
		instance.healthy && instance.id != null && Number(instance.maxId) === 0
	)
}

async function removeBusiness(business) {
	try {
		await ElMessageBox.confirm(
			`确定删除发号业务“${business.bizType}”吗？删除后无法恢复，请确认该业务尚未使用。`,
			'删除发号业务',
			{type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'}
		)
	} catch {
		return
	}
	const response = await api.businesses.delete(business.bizType).catch(() => null)
	if (!response) return
	if (!response.success) {
		ElMessage.error(response.cause || response.msg || '操作失败')
		return
	}
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
.config-help { margin-bottom: 16px; }
.config-help ul { padding-left: 20px; line-height: 1.8; }
.instance-table { margin: 0 32px; width: calc(100% - 64px); }
.pagination { justify-content: flex-end; margin-top: 16px; }
</style>
