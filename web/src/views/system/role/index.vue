<template>
	<el-container>
		<el-header>
			<div class="left-panel">
				<el-button type="primary" icon="el-icon-plus" @click="add"></el-button>
				<el-button type="danger" plain icon="el-icon-delete" :disabled="selection.length==0" @click="batch_del"></el-button>
				<el-button type="primary" plain :disabled="selection.length!=1" @click="resource">权限设置</el-button>
			</div>
			<div class="right-panel">
				<div class="right-panel-search">
					<el-input v-model="keyWord" placeholder="角色名称" clearable></el-input>
					<el-button type="primary" icon="el-icon-search" @click="searchRole"></el-button>
				</div>
			</div>
		</el-header>
		<el-main class="nopadding">
			<scTable ref="table" :apiObj="apiObj" row-key="id" @selection-change="selectionChange" stripe>
				<el-table-column type="selection" width="50"></el-table-column>
				<el-table-column label="#" type="index" width="50"></el-table-column>
				<el-table-column label="角色名称" prop="name" width="250"></el-table-column>
				<el-table-column label="角色编码" prop="code" width="200"></el-table-column>
				<el-table-column label="排序" prop="sort" width="120" sortable></el-table-column>
				<el-table-column label="反向角色" prop="negative" width="120">
					<template #default="scope">
						<el-switch :model-value="String(scope.row.negative)" disabled active-value="true" inactive-value="false" />
					</template>
				</el-table-column>
				<el-table-column label="排他角色" prop="exclusivity" width="120">
					<template #default="scope">
						<el-switch :model-value="String(scope.row.exclusivity)" disabled active-value="true" inactive-value="false" />
					</template>
				</el-table-column>
				<el-table-column label="状态" prop="status" width="120">
					<template #default="scope">
						<el-switch :model-value="String(scope.row.status)" disabled active-value="1" inactive-value="0" />
					</template>
				</el-table-column>
				<el-table-column label="数据权限" prop="dataScope" width="250">
					<template #default="scope">
						{{ dataScopeEnumValue[scope.row.dataScope] }}
					</template>
				</el-table-column>
				<el-table-column label="操作" fixed="right" align="right" width="200">
					<template #default="scope">
						<el-button-group>
							<el-button text type="primary" size="small" @click="show(scope.row)">查看</el-button>
							<el-button text type="primary" size="small" @click="edit(scope.row, scope.$index)">编辑</el-button>
							<el-popconfirm title="确定删除吗？" @confirm="del(scope.row, scope.$index)">
								<template #reference>
									<el-button text type="danger" size="small">删除</el-button>
								</template>
							</el-popconfirm>
						</el-button-group>
					</template>
				</el-table-column>
			</scTable>
		</el-main>
	</el-container>
	<save-dialog v-if="dialog.save" ref="saveDialog" @success="handleSaveSuccess" @closed="dialog.save=false" :dataScopeEnum="dataScopeEnum"></save-dialog>
	<resource-dialog v-if="dialog.resource" ref="resourceDialog" @success="handleSaveSuccess" @closed="dialog.resource=false" :dataScopeEnum="dataScopeEnum"></resource-dialog>
</template>

<script>
import saveDialog from './save'
import resourceDialog from './resource'

export default{
	name: 'role',
	data () {
		return {
			dialog: {
				save: false,
				resource: false
			},
			spaceSize: 10,
			keyWord: '',
			apiObj: this.$API.system_role.role.list,
			dataScopeEnum: [],
			dataScopeEnumValue: {},
			selection: []
		}
	},
	components: {
		saveDialog,
		resourceDialog
	},
	watch: {

	},
	async created(){
		let dataScopeRes = await this.$API.system_role.role.optionsByDataScope.get()
		if(dataScopeRes.success){
			this.dataScopeEnum = dataScopeRes.data
			this.handlerDataScopeEnum()
		}
	},
	methods: {
		handlerDataScopeEnum(){
			this.dataScopeEnum.forEach((item) => {
				this.dataScopeEnumValue[item.value] = item.label
			})
		},
		searchRole(){
			this.$refs.table.upData({keywords: this.keyWord})
		},
		add(){
			this.dialog.save=true
			this.$nextTick(()=>{
				this.$refs.saveDialog.open()
			})
		},
		show(row){
			this.dialog.save=true
			this.$nextTick(()=>{
				this.$refs.saveDialog.open("show").setData(row)
			})
		},
		edit(row){
			this.dialog.save=true
			this.$nextTick(()=>{
				this.$refs.saveDialog.open("edit").setData(row)
			})
		},
		resource(row){
			if(!row['id']){
				row = this.selection[0]
			}
			this.dialog.resource=true
			this.$nextTick(()=>{
				this.$refs.resourceDialog.open().refreshResource(row)
			})
		},
		//表格选择后回调事件
		selectionChange(selection){
			this.selection = selection;
		},
		async batch_del(){
			const msgBox = await ElMessageBox.confirm(`确定删除选中的 ${this.selection.length} 项吗？如果删除项中含有子集将会被一并删除`, '提示', {
				type: 'warning'
			}).catch(()=>{});
			if(msgBox==='confirm'){
				let loading = ElLoading.service({ fullscreen: true })
				await this.$API.system_role.role.del.delete(this.selection.map(item=>item.id))
				this.$refs.table.refresh()
				loading.close()
				ElMessage.success("操作成功")
			}
		},
		async del(row){
			var res = await this.$API.system_role.role.del.delete(row.id)
			if(res.success){
				this.$refs.table.refresh()
				ElMessage.success("删除成功")
			}else{
				ElMessageBox.alert(res.cause || res.msg, "提示", {type: 'error'})
			}
		},
		//本地更新数据
		handleSaveSuccess(data, mode){
			this.$refs.table.refresh()
		}
	}
}
</script>

<style>
</style>
