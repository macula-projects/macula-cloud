<template>
	<el-dialog v-model="visible" title="新增发号业务" width="600px">
		<el-alert type="warning" :closable="false" show-icon
			title="系统会同时配置全部数据源并自动分配只读 remainder；step、delta 和 remainder 创建后不可修改。" />
		<el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
			<el-form-item label="biz_type" prop="bizType"><el-input v-model="form.bizType" maxlength="63" /></el-form-item>
			<el-form-item label="step" prop="step">
				<el-input-number v-model="form.step" :min="1" :max="2147483647" />
				<span class="hint">每次获取 ID 的步长</span>
			</el-form-item>
			<el-form-item label="delta" prop="delta">
				<el-input-number v-model="form.delta" :min="1" :max="2147483647" />
				<span class="hint">预留的数据库实例容量，默认 10</span>
			</el-form-item>
		</el-form>
		<template #footer>
			<el-button @click="visible = false">取消</el-button>
			<el-button type="primary" :loading="saving" @click="submit">在全部数据源创建</el-button>
		</template>
	</el-dialog>
</template>

<script setup>
import {reactive, ref} from 'vue'
import api from '@/api/model/tinyid/management'

const emit = defineEmits(['success'])
const visible = ref(false)
const saving = ref(false)
const formRef = ref()
const form = reactive({bizType: '', step: 100, delta: 10})
const rules = {
	bizType: [{required: true, message: '请输入 biz_type', trigger: 'blur'}],
	step: [{required: true, type: 'number', min: 1, message: 'step 必须大于 0', trigger: 'change'}],
	delta: [{required: true, type: 'number', min: 1, message: 'delta 必须大于 0', trigger: 'change'}]
}

function open() {
	form.bizType = ''
	form.step = 100
	form.delta = 10
	visible.value = true
}

async function submit() {
	await formRef.value.validate()
	saving.value = true
	try {
		await api.businesses.create({...form})
		visible.value = false
		emit('success')
	} finally {
		saving.value = false
	}
}

defineExpose({open})
</script>

<style scoped>
.el-alert { margin-bottom: 16px; }
.hint { margin-left: 10px; color: var(--el-text-color-secondary); }
</style>
