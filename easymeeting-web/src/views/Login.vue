<template>
  <div class="login-container">
    <div class="login-box">
      <h2>登录 EasyMeeting</h2>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="0">
        <el-form-item prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" prefix-icon="Message" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item prop="checkCode">
          <div class="check-code-row">
            <el-input v-model="form.checkCode" placeholder="请输入验证码" />
            <CheckCode ref="checkCodeRef" />
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleLogin" style="width: 100%">登录</el-button>
        </el-form-item>
      </el-form>
      <div class="footer">
        还没有账号？<router-link to="/register">立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import CheckCode from '@/components/CheckCode.vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const formRef = ref<FormInstance>()
const checkCodeRef = ref<InstanceType<typeof CheckCode>>()
const loading = ref(false)

const form = reactive({
  email: '',
  password: '',
  checkCode: ''
})

const rules: FormRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ],
  checkCode: [
    { required: true, message: '请输入验证码', trigger: 'blur' }
  ]
}

async function handleLogin() {
  if (!formRef.value) return
  await formRef.value.validate()
  
  loading.value = true
  try {
    await userStore.login(
      form.email,
      form.password,
      form.checkCode,
      checkCodeRef.value?.checkCodeKey || ''
    )
    ElMessage.success('登录成功')
  } catch {
    checkCodeRef.value?.refresh()
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-box {
  width: 400px;
  padding: 40px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
}
.login-box h2 {
  text-align: center;
  margin-bottom: 30px;
  color: #333;
}
.check-code-row {
  display: flex;
  gap: 12px;
  width: 100%;
}
.check-code-row .el-input {
  flex: 1;
}
.footer {
  text-align: center;
  margin-top: 20px;
  color: #666;
}
.footer a {
  color: #409eff;
  text-decoration: none;
}
</style>
