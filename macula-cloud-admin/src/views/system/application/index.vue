<template>
	<el-container>
		<el-header>
			<div class="left-panel">
				<el-button type="primary" icon="el-icon-plus" @click="add"></el-button>
				<el-button type="danger" plain icon="el-icon-delete" :disabled="selection.length==0" @click="batch_del"></el-button>
			</div>
			<div class="right-panel">
				<div class="right-panel-search">
					<el-input v-model="search.keywords" placeholder="应用名称 / 应用编码" clearable></el-input>
					<el-button type="primary" icon="el-icon-search" @click="upsearch"></el-button>
				</div>
			</div>
		</el-header>
		<el-main class="nopadding">
			<scTable ref="table" :apiObj="apiObj" @selection-change="selectionChange" stripe>
				<el-table-column type="selection" width="50"></el-table-column>
				<el-table-column label="应用名称" prop="applicationName" width="250"></el-table-column>
				<el-table-column label="应用编码" prop="code" width="250"></el-table-column>
				<el-table-column label="主页" prop="homepage" width="270"></el-table-column>
				<el-table-column label="可访问路径" prop="accessPath" width="370"></el-table-column>
				<el-table-column label="负责人" prop="manager" width="120"></el-table-column>
				<el-table-column label="联系方式" prop="mobile" width="150"></el-table-column>
				<el-table-column label="创建时间" prop="createTime" width="170"></el-table-column>
				<el-table-column label="操作" fixed="right" align="right" width="300">
					<template #default="scope">
						<el-button-group>
							<el-button text type="primary" size="small" @click="table_show(scope.row, scope.$index)">查看维护人</el-button>
							<el-button text type="primary" size="small" @click="table_add_maintainer(scope.row, scope.$index)">添加维护人</el-button>
							<el-button text type="primary" size="small" @click="table_edit(scope.row, scope.$index)">编辑</el-button>
							<el-popconfirm title="确定删除吗？" @confirm="table_del(scope.row, scope.$index)">
								<template #reference>
									<el-button text type="primary" size="small">删除</el-button>
								</template>
							</el-popconfirm>
						</el-button-group>
					</template>
				</el-table-column>
			</scTable>
		</el-main>
	</el-container>
	<save-dialog v-if="dialog.save" ref="saveDialog" @success="handleSuccess" @closed="dialog.save=false"></save-dialog>
	<list-dialog v-if="dialog.list" ref="listDialog" @success="handleSuccess" @closed="dialog.list=false"></list-dialog>
	<show-dialog v-if="dialog.show" ref="showDialog" @success="handleSuccess" @closed="dialog.show=false"></show-dialog>

</template>

<script>
	import saveDialog from './save'
	import listDialog from './addMaintainer'
	import showDialog from './showMaintainer'

	export default {
		name: 'application',
		components: {
			saveDialog,
			listDialog,
			showDialog
		},
		data() {
			return {
				dialog: {
					save: false,
					list: false,
					show: false,
				},
				apiObj: this.$API.system_application.application.listPages,
				selection: [],
				search: {
					keywords: null,
				}
			}
		},
		methods: {
			//添加
			add(){
				this.dialog.save = true
				this.$nextTick(() => {
					this.$refs.saveDialog.open()
				})
			},
			//编辑
			table_edit(row){
				this.dialog.save = true
				this.$nextTick(() => {
					this.$refs.saveDialog.open('edit').setData(row)
				})
			},
			//查看维护人
			table_show(row){
				this.dialog.show = true
				this.$nextTick(() => {
					this.$refs.showDialog.open(row)
				})
			},
			//添加维护人
			table_add_maintainer(row){
				this.dialog.list = true
				this.$nextTick(() => {
					this.$refs.listDialog.open(row)
				})
			},
			//删除
			async table_del(row, index){
				var reqData = row.id
				var res = await this.$API.system_application.application.del.delete(reqData);
				if(res.success){
					//这里选择刷新整个表格 OR 插入/编辑现有表格数据
					// this.$refs.table.tableData.splice(index, 1);
					this.$refs.table.refresh()
					ElMessage.success("删除成功")
				}else{
					ElMessageBox.alert(res.cause || res.msg, "提示", {type: 'error'})
				}
			},
			//表格选择后回调事件
			selectionChange(selection){
				this.selection = selection;
			},
			//批量删除
			async batch_del(){
				ElMessageBox.confirm(`确定删除选中的 ${this.selection.length} 项吗？`, '提示', {
					type: 'warning'
				}).then(() => {
					this.selection.forEach(item => {
						this.$refs.table.tableData.forEach((itemI, indexI) => {
							if (item.id === itemI.id) {
								var res = this.$API.system_application.application.del.delete(itemI.id)
								this.$refs.table.tableData.splice(indexI, 1)
							}
						})
					})
					//loading.close();
					ElMessage.success("操作成功")
				}).catch(() => {})
			},
			//搜索
			async upsearch(){
				this.$refs.table.upData(this.search)
			},
			//本地更新数据
			handleSuccess(data, mode){
				this.$refs.table.refresh()
			},			
		}
	}
</script>

<style>
</style>
