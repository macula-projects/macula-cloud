<template>
	<el-container>
		<el-header>
			<div class="left-panel">
				<el-button type="primary" icon="el-icon-plus" @click="add"></el-button>
				<el-button type="danger" plain icon="el-icon-delete" :disabled="selection.length==0" @click="batch_del"></el-button>
			</div>
			<div class="right-panel">
				<div class="right-panel-search">
					<el-input v-model="keyWord" placeholder="租户名称" clearable></el-input>
					<el-button type="primary" icon="el-icon-search" @click="searchTenant"></el-button>
				</div>
			</div>
		</el-header>
		<el-main class="nopadding">
			<scTable ref="table" :apiObj="apiObj" row-key="id" @selection-change="selectionChange" stripe>
				<el-table-column type="selection" width="50"></el-table-column>
				<el-table-column label="#" type="index" width="50"></el-table-column>
				<el-table-column label="租户名称" prop="name" width="250"></el-table-column>
				<el-table-column label="租户编码" prop="code" width="250"></el-table-column>
				<el-table-column label="负责人" prop="supervisor" show-overflow-tooltip>
					<template #default="scope">
						{{ scope.row.supervisor.map(item=>item.username).join(',') }}
					</template>
				</el-table-column>
				<el-table-column label="描述" prop="description" show-overflow-tooltip width="120"></el-table-column>
				<el-table-column label="操作" fixed="right" align="right" width="250">
					<template #default="scope">
						<el-button-group>
							<el-button text type="primary" size="small" @click="table_show(scope.row, scope.$index)">查看负责人</el-button>
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
	<save-dialog v-if="dialog.save" ref="saveDialog" @success="handleSaveSuccess" @closed="dialog.save=false"></save-dialog>
	<show-maintainer-dialog v-if="dialog.show" ref="showMaintainerDialog" @closed="dialog.show=false"></show-maintainer-dialog>
</template>

<script>
import saveDialog from './save'
import showMaintainerDialog from './showMaintainer'
export default{
	name: 'tenant',
	data(){
		let that =this
		return{
			keyWord: '',
			apiObj: this.$API.system_tenant.tenant.pages,
			selection: [],
			dialog: {
				save: false,
				resource: false,
				show: false
			}
		}
	},
	components:{
		saveDialog,
		showMaintainerDialog
	},
	methods: {
		table_show(row, rowIndex){
			this.dialog.show = true
			this.$nextTick(() => {
				this.$refs.showMaintainerDialog.open(row)
			})
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
		edit(row, index){
			this.dialog.save=true
			this.$nextTick(()=>{
				this.$refs.saveDialog.open("edit").setData(row)
			})
		},
		async del(row, index){
			var res = await this.$API.system_tenant.tenant.del.delete(row.id)
			if(res.success){
				this.$refs.table.refresh()
				ElMessage.success("删除成功")
			}else{
				ElMessageBox.alert(res.cause || res.msg, "提示", {type: 'error'})
			}
		},
		async batch_del(){
			const msgBox = await ElMessageBox.confirm(`确定删除选中的 ${this.selection.length} 项吗？如果删除项中含有子集将会被一并删除`, '提示', {
				type: 'warning'
			}).catch(()=>{});
			if(msgBox==='confirm'){
				let loading = ElLoading.service({ fullscreen: true })
				await this.$API.system_tenant.tenant.del.delete(this.selection.map(item=>item.id))
				this.$refs.table.refresh()
				loading.close()
				ElMessage.success("操作成功")
			}
		},
		searchTenant(){
			this.$refs.table.upData({keywords: this.keyWord})
		},
		//表格选择后回调事件
		selectionChange(selection){
			this.selection = selection
		},
		handleSaveSuccess(){
			this.$refs.table.refresh()
		}
	}
}
</script>

<style>
</style>
