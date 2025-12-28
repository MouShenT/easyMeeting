<template>
  <div class="register-container">
    <div class="register-box">
      <h2>注册 EasyMeeting</h2>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="0">
        <el-form-item prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" prefix-icon="Message" />
        </el-form-item>
        <el-form-item prop="nickName">
          <el-input v-model="form.nickName" placeholder="请输入昵称" prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" placeholder="请确认密码" prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item prop="checkCode">
          <div class="check-code-row">
            <el-input v-model="form.checkCode" placeholder="请输入验证码" />
            <CheckCode ref="checkCodeRef" />
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleRegister" style="width: 100%">注册</el-button>
        </el-form-item>
      </el-form>
      <div class="footer">
        已有账号？<router-link to="/login">立即登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import CheckCode from '@/components/CheckCode.vue'
import { register } from '@/api/account'

const router = useRouter()
const formRef = ref<FormInstance>()
const checkCodeRef = ref<InstanceType<typeof CheckCode>>()
const loading = ref(false)

const form = reactive({
  email: '',
  nickName: '',
  password: '',
  confirmPassword: '',
  checkCode: ''
})

const validateConfirmPassword = (_rule: any, value: string, callback: any) => {
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules: FormRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  nickName: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { max: 20, message: '昵称最长20个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { max: 20, message: '密码最长20个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ],
  checkCode: [
    { required: true, message: '请输入验证码', trigger: 'blur' }
  ]
}

async function handleRegister() {
  if (!formRef.value) return
  await formRef.value.validate()
  
  loading.value = true
  try {
    await register({
      email: form.email,
      nickName: form.nickName,
      password: form.password,
      checkCode: form.checkCode,
      checkCodeKey: checkCodeRef.value?.checkCodeKey || ''
    })
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch {
    checkCodeRef.value?.refresh()
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.register-box {
  width: 400px;
  padding: 40px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
}
.register-box h2 {
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
