<template><section class="configuration-panel"><div class="section-heading"><h3>访问授权</h3><p>选择允许登录此应用的用户目录。</p></div>
    <el-form-item label="用户类型"
      ><el-select v-model="client.userType"
        ><el-option label="行政员工" value="employee" /><el-option
          label="会员"
          value="member" /><el-option label="外包人员" value="odc" /><el-option
          label="供应商"
          value="supplier" /></el-select
      ><p class="hint"
        >按应用的用户目录选择；后端服务不发起用户登录。</p
      ></el-form-item
    >
</section></template>
<script setup>
const client = defineModel('client', {type:Object, required:true});
</script>
<style scoped>.hint {color:var(--el-text-color-secondary);font-size:13px;line-height:1.7;}</style>
