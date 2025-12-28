<template>
  <div class="home-container">
    <NavBar />
    <div class="home-content">
      <div class="user-card">
        <div class="avatar">{{ userStore.nickName.charAt(0) }}</div>
        <div class="user-detail">
          <div class="nick-name">{{ userStore.nickName }}</div>
          <div class="meeting-no">个人会议号: {{ userStore.meetingNo }}</div>
        </div>
      </div>
      <div class="action-grid">
        <div class="action-item" @click="handleStartMeeting">
          <el-icon :size="40"><VideoCamera /></el-icon>
          <span>发起会议</span>
        </div>
        <div class="action-item" @click="handleJoinMeeting">
          <el-icon :size="40"><Plus /></el-icon>
          <span>加入会议</span>
        </div>
        <div class="action-item" @click="handleHistory">
          <el-icon :size="40"><Clock /></el-icon>
          <span>历史会议</span>
        </div>
      </div>
    </div>
    <!-- 快速会议对话框 -->
    <el-dialog v-model="quickMeetingDialogVisible" title="发起会议" width="450px">
      <el-form :model="quickMeetingForm" label-width="100px">
        <el-form-item label="会议号类型">
          <el-radio-group v-model="quickMeetingForm.meetingNoType">
            <el-radio :value="0">使用个人会议号</el-radio>
            <el-radio :value="1">系统生成会议号</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="会议主题" required>
          <el-input v-model="quickMeetingForm.meetingName" placeholder="请输入会议主题" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="会议密码">
          <el-input v-model="quickMeetingForm.joinPassword" placeholder="可选，最多5位" maxlength="5" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="quickMeetingDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="quickMeetingLoading" @click="confirmQuickMeeting">创建会议</el-button>
      </template>
    </el-dialog>

    <!-- 加入会议对话框 -->
    <el-dialog v-model="joinDialogVisible" title="加入会议" width="400px">
      <el-form :model="joinMeetingForm" label-width="100px">
        <el-form-item label="会议号" required>
          <el-input v-model="joinMeetingForm.meetingNo" placeholder="请输入10位会议号" maxlength="10" />
        </el-form-item>
        <el-form-item label="昵称" required>
          <el-input v-model="joinMeetingForm.nickName" placeholder="请输入昵称" maxlength="20" />
        </el-form-item>
        <el-form-item label="会议密码">
          <el-input v-model="joinMeetingForm.joinPassword" placeholder="如需密码请输入" maxlength="5" show-password />
        </el-form-item>
        <el-form-item label="开启摄像头">
          <el-switch v-model="joinMeetingForm.videoOpen" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="joinDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="joinMeetingLoading" @click="confirmJoin">加入会议</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { VideoCamera, Plus, Clock } from '@element-plus/icons-vue'
import NavBar from '@/components/NavBar.vue'
import { useUserStore } from '@/stores/user'
import { quickMeeting, joinMeeting, preJoinMeeting } from '@/api/meeting'
import type { QuickMeetingForm, JoinMeetingForm } from '@/types'

const router = useRouter()
const userStore = useUserStore()

const quickMeetingDialogVisible = ref(false)
const quickMeetingLoading = ref(false)
const quickMeetingForm = reactive<QuickMeetingForm>({
  meetingNoType: 0,
  meetingName: '',
  joinType: 0,
  joinPassword: ''
})

const joinDialogVisible = ref(false)
const joinMeetingLoading = ref(false)
const joinMeetingForm = reactive<JoinMeetingForm>({
  meetingNo: '',
  nickName: '',
  joinPassword: '',
  videoOpen: true
})

function resetQuickMeetingForm() {
  quickMeetingForm.meetingNoType = 0
  quickMeetingForm.meetingName = ''
  quickMeetingForm.joinType = 0
  quickMeetingForm.joinPassword = ''
}

function resetJoinMeetingForm() {
  joinMeetingForm.meetingNo = ''
  joinMeetingForm.nickName = userStore.nickName || ''
  joinMeetingForm.joinPassword = ''
  joinMeetingForm.videoOpen = true
}

function handleStartMeeting() {
  resetQuickMeetingForm()
  quickMeetingDialogVisible.value = true
}

async function confirmQuickMeeting() {
  if (!quickMeetingForm.meetingName.trim()) {
    ElMessage.warning('请输入会议主题')
    return
  }
  quickMeetingLoading.value = true
  try {
    const meetingId = await quickMeeting({
      meetingNoType: quickMeetingForm.meetingNoType,
      meetingName: quickMeetingForm.meetingName.trim(),
      joinType: quickMeetingForm.joinType,
      joinPassword: quickMeetingForm.joinPassword || undefined
    })
    await joinMeeting({ videoOpen: true })
    ElMessage.success('会议创建成功')
    quickMeetingDialogVisible.value = false
    // 跳转到会议页面
    router.push(`/meeting/${meetingId}`)
  } catch (error) {
    console.error('创建会议失败:', error)
  } finally {
    quickMeetingLoading.value = false
  }
}

function handleJoinMeeting() {
  resetJoinMeetingForm()
  joinDialogVisible.value = true
}

async function confirmJoin() {
  if (!/^\d{10}$/.test(joinMeetingForm.meetingNo)) {
    ElMessage.warning('请输入10位会议号')
    return
  }
  if (!joinMeetingForm.nickName.trim()) {
    ElMessage.warning('请输入昵称')
    return
  }
  joinMeetingLoading.value = true
  try {
    // 先调用 preJoinMeeting 验证会议信息
    const meetingId = await preJoinMeeting({
      meetingNo: joinMeetingForm.meetingNo,
      nickName: joinMeetingForm.nickName.trim(),
      password: joinMeetingForm.joinPassword || undefined
    })
    // 验证通过后，调用 joinMeeting 正式加入
    await joinMeeting({ videoOpen: joinMeetingForm.videoOpen })
    ElMessage.success('加入会议成功')
    joinDialogVisible.value = false
    // 跳转到会议页面
    router.push(`/meeting/${meetingId}`)
  } catch (error) {
    console.error('加入会议失败:', error)
  } finally {
    joinMeetingLoading.value = false
  }
}

function handleHistory() {
  router.push('/history')
}
</script>

<style scoped>
.home-container {
  min-height: 100vh;
  background: #f5f7fa;
}
.home-content {
  padding: 40px;
  max-width: 800px;
  margin: 0 auto;
}
.user-card {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 30px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  margin-bottom: 40px;
}
.avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  font-size: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.nick-name {
  font-size: 24px;
  font-weight: 500;
  margin-bottom: 8px;
}
.meeting-no {
  color: #909399;
  font-size: 14px;
}
.action-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}
.action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 40px 20px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}
.action-item:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}
.action-item span {
  font-size: 16px;
  color: #333;
}
.action-item .el-icon {
  color: #409eff;
}
</style>
