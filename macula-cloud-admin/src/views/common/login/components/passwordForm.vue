<!--
  - Copyright (c) 2023 Macula
  -   macula.dev, China
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -    http://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -->

<template>
	<el-form ref="loginForm" :model="form" :rules="rules" label-width="0" size="large">
		<el-form-item prop="user">
			<el-input v-model="form.user" prefix-icon="el-icon-user" clearable :placeholder="$t('login.userPlaceholder')">
				<template #append>
					<el-select v-model="userType" style="width: 130px;">
						<el-option :label="$t('login.admin')" value="admin"></el-option>
						<el-option :label="$t('login.user')" value="user"></el-option>
					</el-select>
				</template>
			</el-input>
		</el-form-item>
		<el-form-item prop="password">
			<el-input v-model="form.password" prefix-icon="el-icon-lock" clearable show-password
				:placeholder="$t('login.PWPlaceholder')"></el-input>
		</el-form-item>
		<el-form-item style="margin-bottom: 10px;">
			<el-col :span="12">
				<el-checkbox :label="$t('login.rememberMe')" v-model="form.autologin"></el-checkbox>
			</el-col>
			<el-col :span="12" class="login-forgot">
				<router-link to="/reset_password">{{ $t('login.forgetPassword') }}？</router-link>
			</el-col>
		</el-form-item>
		<el-form-item>
			<el-button type="primary" style="width: 100%;" :loading="islogin" round @click="login">{{ $t('login.signIn')
			}}</el-button>
		</el-form-item>
		<div class="login-reg">
			{{ $t('login.noAccount') }} <router-link to="/user_register">{{ $t('login.createAccount') }}</router-link>
		</div>
	</el-form>
</template>

<script>
import { useTenantStore } from '@/stores/tenant';
import { mapActions } from 'pinia';

export default {
	data() {
		return {
			userType: 'admin',
			form: {
				user: "admin",
				password: "admin",
				autologin: false
			},
			rules: {
				user: [
					{ required: true, message: this.$t('login.userError'), trigger: 'blur' }
				],
				password: [
					{ required: true, message: this.$t('login.PWError'), trigger: 'blur' }
				]
			},
			islogin: false,
		}
	},
	watch: {
		userType(val) {
			if (val == 'admin') {
				this.form.user = 'admin'
				this.form.password = 'admin'
			} else if (val == 'user') {
				this.form.user = 'user'
				this.form.password = 'user'
			}
		}
	},
	mounted() {

	},
	methods: {
		...mapActions(useTenantStore, ['pushTenantOptions', 'updateTenantLabel', 'updateTenantId', 'clearTenantOptions']),
		async login() {

			var validate = await this.$refs.loginForm.validate().catch(() => { })
			if (!validate) { return false }

			this.islogin = true
			var data = {
				username: this.form.user,
				password: this.form.password,
				grant_type: 'password',
				client_id: 'e4da4a32-592b-46f0-ae1d-784310e88423',
				client_secret: 'secret',
				scope: 'message.read message.write userinfo'
			}
			//获取token
			var user = await this.$API.common_auth.systemToken.post({}, {
				params: data
			})
			if (user.access_token) {
				this.$TOOL.cookie.set("TOKEN", user.access_token, {
					expires: 24 * 60 * 60
				})
			} else {
				this.islogin = false
				ElMessage.warning(user.msg || user.error_description || user.error)
				return false
			}

			var userInfo = await this.$API.common_auth.getUserInfo.get()
			if (userInfo.success) {
				this.$TOOL.data.set("USER_INFO", userInfo.data)
			} else {
				this.islogin = false
				ElMessage.warning(userInfo.msg)
				return false
			}

			//获取我的租户列表（普通应用这段需要去掉）
			var tenantOptionsRes = await this.$API.system_tenant.tenant.options.get()
			if (tenantOptionsRes.success) {
				if (tenantOptionsRes.data.length == 0) {
					this.islogin = false
					ElMessageBox.alert("当前用户无任何菜单权限，请联系系统管理员", "无权限访问", {
						type: 'error',
						center: true
					})
					return false
				}
				this.clearTenantOptions()
				this.pushTenantOptions(tenantOptionsRes.data)
				this.updateTenantId(tenantOptionsRes.data[0].value)
				this.updateTenantLabel(tenantOptionsRes.data[0].label)
			}

			// 处理菜单
			// 用户的角色是否包含路由返回菜单对应的角色
			var res = await this.$API.common_auth.getRoutes.get()
			if (res.success) {
				var routes = res.data
				var roles = userInfo.data.roles
				var perms = userInfo.data.perms
				
				// var menu = this.$TOOL.treeFilter(routes, node => {
            	// 	return node.meta.roles ? node.meta.roles.filter(item => roles.indexOf(item) > -1).length > 0 : true
        		// })
				// 支持排除反向角色
				var menu = this.$TOOL.treeFilter(routes, node => {
					const containsRoles = roles.some(role => !role.startsWith("!") && node.meta.roles.includes(role));
					const containsNegatedRoles = roles.some(role => role.startsWith("!") && node.meta.roles.includes(role.substring(1)));
					return containsRoles & !containsNegatedRoles
				})

				this.$TOOL.data.set("MENU", menu)
				this.$TOOL.data.set("PERMISSIONS", perms)
			} else {
				this.islogin = false
				ElMessage.warning(res.msg)
				return false
			}

			this.$router.replace({
				path: '/'
			})
			ElMessage.success("Login Success 登录成功")
			this.islogin = false
		}
	}
}

</script>

<style></style>
