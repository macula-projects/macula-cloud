<template>
	<el-dialog v-model="visible" title="新增接入应用" width="560px" destroy-on-close>
		<el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
			<el-form-item label="应用备注" prop="remark">
				<el-input v-model="form.remark" maxlength="255" show-word-limit placeholder="描述 Token 对应的应用" />
			</el-form-item>
			<el-form-item label="授权业务" prop="bizTypes">
				<el-select v-model="form.bizTypes" multiple filterable style="width: 100%" placeholder="选择已完成配置的业务">
					<el-option v-for="item in businesses" :key="item.bizType" :label="item.bizType" :value="item.bizType" />
				</el-select>
			</el-form-item>
		</el-form>
		<template #footer>
			<el-button @click="visible = false">取消</el-button>
			<el-button type="primary" :loading="saving" @click="submit">创建并展示 Token</el-button>
		</template>
	</el-dialog>
	<el-dialog v-model="tokenVisible" title="应用已创建" width="620px">
		<el-alert type="success" :closable="false" title="完整 Token 将持续对超级管理员可见。" />
		<el-input :model-value="createdToken" readonly>
			<template #append><el-button @click="copy">复制</el-button></template>
		</el-input>
	</el-dialog>
</template>

<script setup>
import {reactive, ref} from 'vue'
import {ElMessage} from 'element-plus'
import api from '@/api/model/tinyid/management'

const emit = defineEmits(['success'])
const visible = ref(false)
const tokenVisible = ref(false)
const saving = ref(false)
const formRef = ref()
const businesses = ref([])
const createdToken = ref('')
const form = reactive({remark: '', bizTypes: []})
const rules = {
	remark: [{required: true, message: '请输入应用备注', trigger: 'blur'}],
	bizTypes: [{required: true, type: 'array', min: 1, message: '至少选择一个业务', trigger: 'change'}]
}

async function open() {
	form.remark = ''
	form.bizTypes = []
	let page = 1
	let all = []
	let total = 0
	do {
		const response = await api.businesses.list({page, pageSize: 100})
		all = all.concat(response.records)
		total = response.total
		page++
	} while (all.length < total)
	businesses.value = all.filter(item => item.consistencyStatus === 'COMPLETE')
	if (businesses.value.length === 0) {
		ElMessage.warning('请先在“发号业务”完成至少一个 biz_type 配置')
		return
	}
	visible.value = true
}

async function submit() {
	await formRef.value.validate()
	saving.value = true
	try {
		const response = await api.applications.create({...form})
		createdToken.value = response.token
		visible.value = false
		tokenVisible.value = true
		emit('success')
	} finally {
		saving.value = false
	}
}

async function copy() {
	await navigator.clipboard.writeText(createdToken.value)
	ElMessage.success('Token 已复制')
}

defineExpose({open})
</script>
