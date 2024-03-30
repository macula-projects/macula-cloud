<template>
	<el-dialog :title="titleMap[mode]" v-model="visible" :width="800" destroy-on-close @closed="$emit('closed')">
        <el-container>
            <el-header>
                    <div class="left-panel"/>
                    <div class="right-panel">
                        <div class="right-panel-search">
                            <el-input v-model="search.keywords" placeholder="用户名" clearable></el-input>
                            <el-button type="primary" icon="el-icon-search" @click="upsearch"></el-button>
                        </div>
                    </div>
            </el-header>
            <el-main class="nopadding">
                <sc-list-table ref="listable" :apiObj="obj"
                :params="params"
                stripe>
                    <el-table-column label="ID" prop="id" width="80"></el-table-column>
                    <el-table-column label="用户名" prop="username" width="150"></el-table-column>
                    <el-table-column label="用户昵称" prop="nickname" width="150"></el-table-column>
                    <el-table-column label="所属角色" prop="roleNames" width="150"></el-table-column>
                    <el-table-column label="创建时间" prop="createTime" width="170"></el-table-column>
                </sc-list-table>
            </el-main>
        </el-container>
	</el-dialog>
</template>

<script>
	export default {
		emits: ['success', 'closed'],
		data() {
			return {
                mode: 'show',
                row: null,
				titleMap: {
					show: '查看维护人',
				},
				visible: false,
				isSaveing: false,
                isSearching: false,
                isInit: false,
                params: {
                    ids: '-1'
                },
                obj: this.$API.system_user.user.listByIds,
                search: {
                    keywords: null
                }
			}
		},
		methods: {
			//显示
			open(row){
				this.row = row;
				this.visible = true;
                this.isInit = true
                if (this.row.maintainer !== null && this.row.maintainer !== '') {
                    this.params.ids = this.row.maintainer
                }
			},
            //搜索
			upsearch(){
				this.$refs.listable.upData(this.search)
			},
		}
	}
</script>

<style>
</style>
