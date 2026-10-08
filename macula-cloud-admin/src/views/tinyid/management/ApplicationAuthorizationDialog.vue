<template>
	<el-dialog v-model="visible" title="增加业务授权" width="560px">
		<el-alert type="warning" :closable="false" title="授权只能增加，提交后不能移除。" />
		<el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
			<el-form-item label="新增业务" prop="bizTypes">
				<el-select v-model="form.bizTypes" multiple filterable style="width: 100%">
					<el-option v-for="item in available" :key="item.bizType" :label="item.bizType" :value="item.bizType" />
				</el-select>
			</el-form-item>
		</el-form>
		<template #footer>
			<el-button @click="visible = false">取消</el-button>
			<el-button type="primary" :loading="saving" @click="submit">确认增加</el-button>
		</template>
	</el-dialog>
</template>

<script setup>
import {computed, reactive, ref} from 'vue'
import api from '@/api/model/tinyid/management'

const emit = defineEmits(['success'])
const visible = ref(false)
const saving = ref(false)
const formRef = ref()
const application = ref({bizTypes: []})
const businesses = ref([])
const form = reactive({bizTypes: []})
const rules = {bizTypes: [{required: true, type: 'array', min: 1, message: '至少选择一个业务', trigger: 'change'}]}
const available = computed(() => businesses.value.filter(item => !application.value.bizTypes.includes(item.bizType)))

async function open(row) {
	application.value = row
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
	visible.value = true
}

async function submit() {
	await formRef.value.validate()
	saving.value = true
	try {
		await api.applications.addBusinesses(application.value.appId, {bizTypes: form.bizTypes})
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
</style>
