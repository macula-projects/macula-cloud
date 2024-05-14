<template>
	<el-dialog :title="titleMap[mode]" v-model="visible" :width="600" destroy-on-close @closed="$emit('closed')">
		<el-form :model="form" :rules="rules" :disabled="mode == 'show'" ref="dialogForm" label-width="100px"
			label-position="left">
			<el-form-item label="应用名称" prop="applicationName">
				<el-input v-model="form.applicationName" clearable></el-input>
			</el-form-item>
			<el-form-item label="应用编码" prop="code">
				<el-input v-model="form.code" clearable :disabled="form.id != ''"></el-input>
			</el-form-item>
			<el-form-item label="secretKey" prop="sk">
				<el-input v-model="form.sk" disabled>
					<template #append>
						<el-button type="primary" :icon="'el-icon-refresh'" @click="refreshSk" />
					</template>
				</el-input>
			</el-form-item>
			<el-form-item label="主页" prop="homepage">
				<el-input v-model="form.homepage" clearable></el-input>
			</el-form-item>
			<el-form-item label="可访问路径" prop="accessPath">
				<el-input v-model="form.accessPath" clearable></el-input>
				<div class="el-form-item-msg">多个使用英文逗号,隔开，例: /a,/b,/c</div>
			</el-form-item>
			<el-form-item label="负责人" prop="manager">
				<sc-table-select v-model="form.manager" :apiObj="apiObj" :table-width="450" multiple clearable collapse-tags
					collapse-tags-tooltip :props="props">
					<template #header="{ form, submit }">
						<el-form :inline="true" :model="form">
							<el-form-item>
								<el-input v-model="form.keywords" placeholder="用户名称" clearable></el-input>
							</el-form-item>
							<el-form-item>
								<el-button type="primary" @click="submit">查询</el-button>
							</el-form-item>
						</el-form>
					</template>
					<!-- <el-table-column prop="id" label="ID" width="180"></el-table-column> -->
					<el-table-column label="#" type="index" width="50"></el-table-column>
					<el-table-column prop="username" label="用户名" width="150"></el-table-column>
					<el-table-column prop="nickname" label="真实名字"></el-table-column>
				</sc-table-select>
			</el-form-item>
			<el-form-item label="联系方式" prop="mobile">
				<el-input v-model="form.mobile" clearable></el-input>
			</el-form-item>
			<el-form-item label="是否回传属性" prop="useAttrs">
				<el-switch v-model="form.useAttrs"></el-switch>
			</el-form-item>
			<el-form-item label="回传列表" prop="allowedAttrs">
				<el-input v-model="form.allowedAttrs" clearable type="textarea"></el-input>
			</el-form-item>
		</el-form>
		<template #footer>
			<el-button @click="visible = false">取 消</el-button>
			<el-button v-if="mode != 'show'" type="primary" :loading="isSaveing" @click="submit()">保 存</el-button>
		</template>
	</el-dialog>
</template>


<script>
export default {
	emits: ['success', 'closed'],
	data() {
		let that = this
		return {
			mode: "add",
			titleMap: {
				add: '新增应用',
				edit: '编辑应用'
			},
			visible: false,
			isSaveing: false,
			apiObj: this.$API.system_user.user.list,
			props: {
				label: 'username',
				value: 'username',
				keyword: "keywords"
			},
			//表单数据
			form: {
				id: "",
				applicationName: "",
				sk: "",
				homepage: "",
				code: "",
				manager: [],
				mobile: "",
				accessPath: "",
				useAttrs: false,
				allowedAttrs: ""
			},
			//验证规则
			rules: {
				applicationName: [
					{ required: true, message: '请输入应用名称' }
				],
				code: [
					{ required: true, validator: that.validtorCode, trigger: 'blur' }
				],
				manager: [
					{ required: true, message: '请输入负责人' }
				],
				mobile: [
					{ required: true, message: '请输入联系方式' }
				]
			},

		}
	},
	methods: {
		//显示
		open(mode = 'add') {
			this.mode = mode;
			this.visible = true;
			if (this.mode == 'add') {
				this.form.sk = this.generateAkSK()
			}
			return this
		},
		//表单提交方法
		submit() {
			this.$refs.dialogForm.validate(async (valid) => {
				if (valid) {
					this.isSaveing = true;
					this.form.manager = this.form.manager.map(item => item.username).join(',')
					if (this.mode == 'add') {
						// 调用相关接口
						var res = await this.$API.system_application.application.add.post(this.form);
					} else {
						var res = await this.$API.system_application.application.edit.put(this.form, this.form.id);
					}
					this.isSaveing = false;
					if (res.success) {
						this.$emit('success', this.form, this.mode)
						this.visible = false;
						ElMessage.success("操作成功")
					} else {
						ElMessageBox.alert(res.msg || res.cause, "提示", { type: 'error' })
					}
				} else {
					return false;
				}
			})
		},
		refreshSk() {
			this.form.sk = this.generateAkSK()
		},
		generateAkSK() {
			return this.generateHexString(26) + (new Date()).getTime();
		},
		generateHexString(length) {
			var ret = "";
			while (ret.length < length) {
				ret += Math.random().toString(16).substring(2);
			}
			return ret.substring(0, length);
		},
		//表单注入数据
		setData(data) {
			this.form.id = data.id
			this.form.applicationName = data.applicationName
			this.form.homepage = data.homepage
			this.form.code = data.code
			this.getUsersByUsernames(data.manager)
			this.form.accessPath = data.accessPath
			this.form.mobile = data.mobile
			this.form.sk = data.sk
			this.form.useAttrs = data.useAttrs
			this.form.allowedAttrs = data.allowedAttrs
			//可以和上面一样单个注入，也可以像下面一样直接合并进去
			//Object.assign(this.form, data)
		},
		async getUsersByUsernames(data) {
			this.form.manager = []
			if(!data){
				return
			}
			for(var i in data.split(",")){
				this.form.manager.push({username: data.split(",")[i]})
			}
		},
		async validtorCode(rule, value, callback) {
			if (value.trim().length === 0) {
				callback(new Error('请输入应用编码'))
			}
			const regx = /^[a-zA-Z0-9_\-]+$/
			if (!regx.test(value)) {
				callback(new Error('应用编码由字母、数字、横线及下划线组成'))
			}
			const params = {
				appId: this.form.id ? this.form.id : null,
				appCode: value,
			}
			const res = await this.$API.system_application.application.validtorCode.get(params)
			if (res.success) {
				callback()
			}
			callback(new Error('应用编码已存在！'))
		}
	}
}
</script>

<style></style>
