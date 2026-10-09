<template>
	<el-dialog v-model="visible" title="修改应用备注" width="520px">
		<el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
			<el-form-item label="应用备注" prop="remark">
				<el-input v-model="form.remark" maxlength="255" show-word-limit />
			</el-form-item>
		</el-form>
		<template #footer>
			<el-button @click="visible = false">取消</el-button>
			<el-button type="primary" :loading="saving" @click="submit">保存</el-button>
		</template>
	</el-dialog>
</template>

<script setup>
import {reactive, ref} from 'vue'
import {ElMessage} from 'element-plus'
import api from '@/api/model/tinyid/management'

const emit = defineEmits(['success'])
const visible = ref(false)
const saving = ref(false)
const appId = ref()
const formRef = ref()
const form = reactive({remark: ''})
const rules = {remark: [{required: true, message: '请输入应用备注', trigger: 'blur'}]}

function open(application) {
	appId.value = application.appId
	form.remark = application.remark
	visible.value = true
}

async function submit() {
	await formRef.value.validate()
	saving.value = true
	try {
		const response = await api.applications.updateRemark(appId.value, form).catch(() => null)
		if (!response) return
		if (!response.success) {
			ElMessage.error(response.cause || response.msg || '操作失败')
			return
		}
		visible.value = false
		emit('success')
	} finally {
		saving.value = false
	}
}

defineExpose({open})
</script>
