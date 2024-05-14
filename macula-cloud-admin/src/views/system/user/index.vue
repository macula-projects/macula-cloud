<template>
  <el-container>
    <el-aside width="200px" v-loading="showGrouploading">
      <el-container>
        <el-header>
          <el-input placeholder="输入关键字进行过滤" v-model="groupFilterText" clearable></el-input>
        </el-header>
        <el-main class="nopadding">
          <el-tree ref="group" class="menu" node-key="id" :data="group" :current-node-key="''" :highlight-current="true" :expand-on-click-node="false" :filter-node-method="groupFilterNode" @node-click="groupClick"></el-tree>
        </el-main>
      </el-container>
    </el-aside>
    <el-container>
      <el-header>
        <div class="left-panel">
          <el-button type="primary" icon="el-icon-plus" @click="add"></el-button>
          <el-button type="danger" plain icon="el-icon-delete" :disabled="selection.length==0" @click="batch_del"></el-button>

          <!-- <el-button type="primary" plain :disabled="selection.length==0" @click="set_role">分配角色</el-button> -->
          <el-button type="primary" plain :disabled="selection.length==0" @click="reset_password">密码重置</el-button>

        </div>
        <div class="right-panel">
          <div class="right-panel-search">
            <el-input v-model="search.keywords" placeholder="登录账号 / 姓名" clearable></el-input>
            <el-button type="primary" icon="el-icon-search" @click="upsearch"></el-button>
          </div>
        </div>
      </el-header>
      <el-main class="nopadding">
        <scTable ref="table" :apiObj="apiObj" @selection-change="selectionChange" stripe remoteSort remoteFilter>
          <el-table-column type="selection" width="50"></el-table-column>
          <el-table-column label="ID" prop="id" width="80" ></el-table-column>
          <el-table-column label="头像" width="80" column-key="filterAvatar">
            <template #default="scope">
              <el-avatar :src="scope.row.avatar" size="small"></el-avatar>
            </template>
          </el-table-column>
          <el-table-column label="用户名" prop="username" width="150"  column-key="filterUserName"></el-table-column>
          <el-table-column label="用户昵称" prop="nickname" width="150" ></el-table-column>
          <el-table-column label="所属角色" prop="roleNames" width="200" ></el-table-column>
          <el-table-column label="创建时间" prop="createTime" width="170" ></el-table-column>
          <el-table-column label="操作" fixed="right" align="right" width="160">
            <template #default="scope">
              <el-button-group>
                <el-button text type="primary" size="small" @click="table_show(scope.row, scope.$index)">查看</el-button>
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
  </el-container>

  <save-dialog v-if="dialog.save" ref="saveDialog" @success="handleSuccess" @closed="dialog.save=false" :role-names="saveRoleNames" :depts="saveDepts"></save-dialog>

</template>

<script>
import saveDialog from './save'

export default {
  name: 'user',
  components: {
    saveDialog
  },
  data() {
    return {
      dialog: {
        save: false
      },
      showGrouploading: false,
      groupFilterText: '',
      group: [],
      apiObj: this.$API.system_user.user.list,
      selection: [],
      roleNames: [],
      search: {
        keywords: null
      },
      saveRoleNames: [],
      saveDepts: []
    }
  },
  watch: {
    groupFilterText(val) {
      this.$refs.group.filter(val);
    }
  },
  mounted() {
    this.getDept();
    this.getSaveDept()
    this.getSaveRole()
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
      });
    },
    //查看
    table_show(row){
      this.dialog.save = true
      this.$nextTick(() => {
        this.$refs.saveDialog.open('show').setData(row)
      })
    },
    //删除
    async table_del(row, index){
      var res = await this.$API.system_user.user.del.delete(row.id);
      if(res.success){
        //这里选择刷新整个表格 OR 插入/编辑现有表格数据
        this.$refs.table.tableData.splice(index, 1);
        ElMessage.success("删除成功")
      }else{
        ElMessageBox.alert(res.cause || res.msg, "提示", {type: 'error'})
      }
    },
    //批量删除
    async batch_del(){
      ElMessageBox.confirm(`确定删除选中的 ${this.selection.length} 项吗？`, '提示', {
        type: 'warning'
      }).then(() => {
        this.selection.forEach(item => {
          this.$refs.table.tableData.forEach((itemI, indexI) => {
            if (item.id === itemI.id) {
              var res = this.$API.system_user.user.del.delete(itemI.id)
              this.$refs.table.tableData.splice(indexI, 1)

            }
          })
        })
        //loading.close();
        ElMessage.success("操作成功")
      }).catch(() => {})
    },
    //密码重置
    async reset_password(){
      if(this.selection.length == 1){
        let data;
        this.selection.forEach(item => {
          this.$refs.table.tableData.forEach((itemI, indexI) => {
            if (item.id === itemI.id) {
              data = itemI;
            }
          })
        })

        this.dialog.save = true
        this.$nextTick(() => {
          this.$refs.saveDialog.open('resetPassword').setData(data);
        });
      }else{
        ElMessageBox.alert("只能选一个", "提示", {type: 'error'});
      }

    },
    //表格选择后回调事件
    selectionChange(selection){
      this.selection = selection;
    },
    //加载树数据
    async getDept(){
      this.showGrouploading = true;
      var res = await this.$API.system_dept.dept.options.get();
      this.group = res.data;
      this.showGrouploading = false;
      this.group.unshift({id: '', label: '所有'});
    },
    //获取保存页面的角色，用于form表单数据加载
    async getSaveRole(){
      var res = await this.$API.system_role.role.options.get();
      this.saveRoleNames = res.data;
    },
    //获取保存页面的部门，用于form表单数据加载
    async getSaveDept(){
      var res = await this.$API.system_dept.dept.list.get();
      this.saveDepts = res.data;
    },
    //树过滤
    groupFilterNode(value, data){
      if (!value) return true;
      return data.label.indexOf(value) !== -1;
    },
    //树点击事件
    groupClick(data){
      var params = {
        deptId: data.value
      }
      this.$refs.table.reload(params)
    },
    //搜索
    upsearch(){
      this.$refs.table.upData(this.search)
    },
    //本地更新数据
    handleSuccess(data, mode){
      if(mode=='add'){
        this.$refs.table.refresh();
      }else if(mode=='edit'){
        this.$refs.table.refresh();
      }
    }
  }
}
</script>

<style>
</style>
