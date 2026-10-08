<template>
	<el-card class="tinyid-management" shadow="never">
		<template #header>
			<div class="page-title">
				<div>
					<h2>TinyID 管理</h2>
					<p>先配置发号业务，再创建接入应用并授权业务。仅超级管理员可访问。</p>
				</div>
			</div>
		</template>
		<el-tabs v-model="activeTab">
			<el-tab-pane label="发号业务" name="businesses">
				<BusinessPanel v-if="activeTab === 'businesses'" />
			</el-tab-pane>
			<el-tab-pane label="接入应用" name="applications">
				<ApplicationPanel v-if="activeTab === 'applications'" />
			</el-tab-pane>
			<el-tab-pane label="审计日志" name="audit">
				<AuditLogPanel v-if="activeTab === 'audit'" />
			</el-tab-pane>
		</el-tabs>
	</el-card>
</template>

<script>
export default {
	name: 'TinyIdManagement'
}
</script>

<script setup>
import {ref} from 'vue'
import ApplicationPanel from './ApplicationPanel.vue'
import BusinessPanel from './BusinessPanel.vue'
import AuditLogPanel from './AuditLogPanel.vue'

const activeTab = ref('businesses')
</script>

<style scoped>
.tinyid-management { height: 100%; }
.page-title h2 { margin: 0 0 6px; font-size: 20px; }
.page-title p { margin: 0; color: var(--el-text-color-secondary); }
</style>
