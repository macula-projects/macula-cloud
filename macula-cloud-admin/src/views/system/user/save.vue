<template>
  <el-dialog :title="titleMap[mode]" v-model="visible" :width="500" destroy-on-close @closed="$emit('closed')">
    <el-form :model="form" :rules="rules" :disabled="mode=='show'" ref="dialogForm" label-width="100px" label-position="left">
      <template v-if="mode!='resetPassword'">
        <el-form-item label="头像">
          <sc-upload v-model="form.avatar" title="上传头像"></sc-upload>
        </el-form-item>
        <el-form-item label="登录账号" prop="username">
          <el-input v-model="form.username" placeholder="用于登录系统" clearable></el-input>
        </el-form-item>
        <el-form-item label="姓名" prop="nickname">
          <el-input v-model="form.nickname" placeholder="请输入完整的真实姓名" clearable></el-input>
        </el-form-item>
        <el-form-item label="所属部门" prop="dept">
          <el-cascader v-model="form.dept" :options="depts" :props="deptsProps" :placeholder="form.dept" clearable style="width: 100%;"></el-cascader>
        </el-form-item>
        <el-form-item label="所属角色" prop="roleIds">
          <el-cascader v-model="form.roleIds" :options="roleNames" :props="roleNamesProps" :placeholder="form.roleNames" clearable style="width: 100%;"></el-cascader>
        </el-form-item>
      </template>
      <template v-if="mode=='resetPassword'">
        <el-form-item label="旧密码" prop="oldPassword">
          <el-input type="password" v-model="form.oldPassword" clearable show-password></el-input>
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input type="password" v-model="form.newPassword" clearable show-password></el-input>
        </el-form-item>
      </template>
      <template v-if="mode=='add'">
        <el-form-item label="登录密码" prop="password">
          <el-input type="password" v-model="form.password" clearable show-password></el-input>
        </el-form-item>
        <el-form-item label="确认密码" prop="password2">
          <el-input type="password" v-model="form.password2" clearable show-password></el-input>
        </el-form-item>
      </template>
    </el-form>
    <template #footer>
      <el-button @click="visible=false" >取 消</el-button>
      <el-button v-if="mode!='show'" type="primary" :loading="isSaveing" @click="submit()">保 存</el-button>
    </template>
  </el-dialog>
</template>

<script>
export default {
  emits: ['success', 'closed'],
  props: {
    roleNames: {
      required: true,
      type: Array
    },
    depts: {
      required: true,
      type: Array
    },
  },
  data() {
    return {
      mode: "add",
      titleMap: {
        add: '新增用户',
        edit: '编辑用户',
        show: '查看',
        resetPassword: '重置密码'
      },
      visible: false,
      isSaveing: false,
      //表单数据
      form: {
        id:"",
        username: "",
        avatar: "",
        nickname: "",
        dept: [],
        deptName: "",
        roleNames: "",
        deptId: null,
        password: "",
        roleIds: []
      },
      //验证规则
      rules: {
        roleIds: [
          {required: true, message: '请选择所属角色'}
        ],
        username: [
          {required: true, message: '请输入登录账号'}
        ],
        nickname: [
          {required: true, message: '请输入真实姓名'}
        ],
        password: [
          {required: true, message: '请输入登录密码'},
          {validator: (rule, value, callback) => {
              if (this.form.password2 !== '') {
                this.$refs.dialogForm.validateField('password2');
              }
              callback();
            }}
        ],
        password2: [
          {required: true, message: '请再次输入密码'},
          {validator: (rule, value, callback) => {
              if (value !== this.form.password) {
                callback(new Error('两次输入密码不一致!'));
              }else{
                callback();
              }
            }}
        ],
        dept: [
          {required: true, message: '请选择所属部门'}
        ]
      },
      //所需数据选项
      roleNamesProps: {
        multiple: true
      },
      deptsProps: {
        children: "children",
        label: "name",
        value: "id",
        checkStrictly: true
      }
    }
  },
  methods: {
    //显示
    open(mode='add'){
      this.mode = mode;
      this.visible = true;
      return this
    },
    //后端接口识别的部门数据处理
    handleSubmitDept(){
      this.form.deptId = this.form.dept[this.form.dept.length - 1];
    },
    //后端接口识别的角色数据处理
    handleSubmitRoleId(){
      this.form.roleIds = this.form.roleIds.map(item => {
        if(item instanceof Array){
          return item[0]
        }
        return item
      })
    },
    //表单提交方法
    submit(){
      // 获取部门id和角色id;
      this.$refs.dialogForm.validate(async (valid) => {
        if (valid) {
          this.handleSubmitDept()
          this.handleSubmitRoleId()
          this.isSaveing = true;
          if(this.mode == 'add'){
            var res = await this.$API.system_user.user.add.post(this.form);
          }else if(this.mode == 'edit'){
            var res = await this.$API.system_user.user.edit.put(this.form, this.form.id);
          }else if(this.mode == 'resetPassword'){
            this.form.password = this.form.newPassword;
            console.log("form",this.form)
            var res = await this.$API.system_user.user.resetPassword.patch(this.form);
          }
          this.isSaveing = false;
          if(res.success){
            this.$emit('success', this.form, this.mode)
            this.visible = false;
            ElMessage.success("操作成功");

          }else{
            ElMessageBox.alert(res.cause || res.msg, "提示", {type: 'error'})
          }
        }else{
          return false;
        }
      })
    },
    //表单注入数据
    setData(data){
      this.form.id = data.id
      this.form.username = data.username
      this.form.avatar = data.avatar
      this.form.nickname = data.nickname
      this.form.roleNames = data.roleNames
      this.form.password = data.password
      this.form.roleIds = this.getRoleIds(data.roleNames)
      this.loadDept(data.deptName, this.form)
    },
    // 跟据列表传递的角色中文转成id值
    getRoleIds(roleNames){
      let roleNameArr = roleNames.split(",")
      let roleIds = []
      roleNameArr.forEach(roleName => {
        let findRole = this.roleNames.find(item => item.label === roleName)
        if(findRole) {
          roleIds.push(findRole.value)
        }
      })
      return roleIds
    },
    // 加载部门信息
    loadDept(deptName, form){
      if(this.depts && this.depts.length > 0){
        this.loopHandSubDept(this.depts, [], "", deptName, form)
      } else {
        form.deptName = ""
        form.dept = []
      }
    },
    // 递归整理部门信息结构
    loopHandSubDept(deptInfo, dept, formDeptName, goalDeptName, form){
      deptInfo.find(item => {
        let innerDept = JSON.parse(JSON.stringify(dept))
        innerDept.push(item.id)
        let innerFormDeptName = formDeptName.length > 0 ? formDeptName + " / " + item.name : item.name 
        if(item.name === goalDeptName){
          form.deptName = innerFormDeptName
          form.dept = innerDept
          return true
        }
        if(item.children) {
          this.loopHandSubDept(item.children, innerDept, innerFormDeptName, goalDeptName, form)
        }
        return false
      })
    }
  }
}
</script>

<style>
</style>
