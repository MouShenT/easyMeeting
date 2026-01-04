<template>
  <AppShell>
    <div class="home-page">
      <!-- Current Meeting Banner -->
      <CurrentMeetingCard 
        v-if="currentMeeting" 
        :meeting="currentMeeting" 
        @rejoin="handleRejoinMeeting" 
      />
      
      <div class="home-content">
        <!-- Action Panel -->
        <div class="action-panel">
          <div class="panel-header">
            <h2 class="panel-title">快速开始</h2>
          </div>
          <div class="action-grid">
            <ActionCard label="加入会议" :icon="Plus" @click="handleJoinMeeting" />
            <ActionCard label="快速会议" :icon="VideoCamera" @click="handleStartMeeting" />
            <ActionCard label="预定会议" :icon="Calendar" @click="handleScheduleMeeting" />
            <ActionCard label="历史会议" :icon="Clock" @click="handleHistoryMeeting" />
          </div>
        </div>
        
        <!-- Schedule Panel -->
        <SchedulePanel
          :meetings="todayMeetings"
          :loading="meetingsLoading"
          @meeting-click="handleMeetingClick"
          @view-all="handleViewAllMeetings"
        />
      </div>
    </div>
    
    <!-- Quick Meeting Dialog -->
    <el-dialog v-model="quickMeetingDialogVisible" title="发起会议" width="450px" :close-on-click-modal="false">
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

    <!-- Join Meeting Dialog -->
    <el-dialog v-model="joinDialogVisible" title="加入会议" width="400px" :close-on-click-modal="false">
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
  </AppShell>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { VideoCamera, Plus, Calendar, Clock } from '@element-plus/icons-vue'
import AppShell from '@/components/AppShell.vue'
import ActionCard from '@/components/ActionCard.vue'
import SchedulePanel from '@/components/SchedulePanel.vue'
import CurrentMeetingCard from '@/components/CurrentMeetingCard.vue'
import type { ScheduleMeeting } from '@/components/SchedulePanel.vue'
import { useUserStore } from '@/stores/user'
import { quickMeeting, joinMeeting, preJoinMeeting, getCurrentMeeting } from '@/api/meeting'
import { loadTodayMeeting } from '@/api/reserve'
import type { QuickMeetingForm, JoinMeetingForm, MeetingInfo } from '@/types'

const router = useRouter()
const userStore = useUserStore()

// Current meeting state
const currentMeeting = ref<MeetingInfo | null>(null)

// Today's meetings
const todayMeetings = ref<ScheduleMeeting[]>([])
const meetingsLoading = ref(false)

// Quick meeting dialog
const quickMeetingDialogVisible = ref(false)
const quickMeetingLoading = ref(false)
const quickMeetingForm = reactive<QuickMeetingForm>({
  meetingNoType: 0,
  meetingName: '',
  joinType: 0,
  joinPassword: ''
})

// Join meeting dialog
const joinDialogVisible = ref(false)
const joinMeetingLoading = ref(false)
const joinMeetingForm = reactive<JoinMeetingForm>({
  meetingNo: '',
  nickName: '',
  joinPassword: '',
  videoOpen: true
})

// Load current meeting (使用缓存避免重复请求)
let currentMeetingPromise: Promise<MeetingInfo | null> | null = null

async function loadCurrentMeeting() {
  try {
    // 如果已有请求在进行中，复用它
    if (!currentMeetingPromise) {
      currentMeetingPromise = getCurrentMeeting()
    }
    currentMeeting.value = await currentMeetingPromise
  } catch (error) {
    console.error('获取当前会议失败:', error)
  } finally {
    currentMeetingPromise = null
  }
}

// Load today's meetings
async function loadTodayMeetings() {
  meetingsLoading.value = true
  try {
    const reserves = await loadTodayMeeting()
    // 转换为 ScheduleMeeting 格式，使用数据库 status 字段判断状态
    todayMeetings.value = reserves.map(reserve => ({
      id: reserve.meetingId,
      name: reserve.meetingName,
      meetingNo: reserve.realMeetingId || reserve.meetingId,
      startTime: reserve.startTime,
      duration: reserve.duration,
      status: getReserveStatus(reserve.status),
      realMeetingId: reserve.realMeetingId
    }))
  } catch (error) {
    console.error('获取今日会议失败:', error)
  } finally {
    meetingsLoading.value = false
  }
}

// 根据数据库 status 字段获取会议状态
// 0: 待开始, 1: 进行中, 2: 已结束, 3: 已取消
function getReserveStatus(status: number): 'ongoing' | 'upcoming' | 'ended' {
  switch (status) {
    case 1: return 'ongoing'
    case 2: return 'ended'
    case 3: return 'ended'
    default: return 'upcoming'
  }
}

// Rejoin meeting
function handleRejoinMeeting(meetingId: string) {
  router.push(`/meeting/${meetingId}`)
}

// Reset forms
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

// Action handlers
function handleStartMeeting() {
  if (currentMeeting.value) {
    ElMessage.warning('您有正在进行的会议，请先结束或退出当前会议')
    return
  }
  resetQuickMeetingForm()
  quickMeetingDialogVisible.value = true
}

function handleJoinMeeting() {
  if (currentMeeting.value) {
    router.push(`/meeting/${currentMeeting.value.meetingId}`)
    return
  }
  resetJoinMeetingForm()
  joinDialogVisible.value = true
}

function handleScheduleMeeting() {
  router.push('/schedule')
}

function handleHistoryMeeting() {
  router.push('/history')
}

// Confirm quick meeting
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
    router.push(`/meeting/${meetingId}`)
  } catch (error) {
    console.error('创建会议失败:', error)
  } finally {
    quickMeetingLoading.value = false
  }
}

// Confirm join meeting
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
    const meetingId = await preJoinMeeting({
      meetingNo: joinMeetingForm.meetingNo,
      nickName: joinMeetingForm.nickName.trim(),
      password: joinMeetingForm.joinPassword || undefined
    })
    await joinMeeting({ videoOpen: joinMeetingForm.videoOpen })
    ElMessage.success('加入会议成功')
    joinDialogVisible.value = false
    router.push(`/meeting/${meetingId}`)
  } catch (error) {
    console.error('加入会议失败:', error)
  } finally {
    joinMeetingLoading.value = false
  }
}

// Meeting list handlers
function handleMeetingClick(meeting: ScheduleMeeting) {
  joinMeetingForm.meetingNo = meeting.meetingNo
  joinMeetingForm.nickName = userStore.nickName || ''
  joinDialogVisible.value = true
}

function handleViewAllMeetings() {
  router.push('/history')
}

// Initialize - 并行加载数据
onMounted(() => {
  Promise.all([
    loadCurrentMeeting(),
    loadTodayMeetings()
  ])
})
</script>

<style scoped>
.home-page {
  padding: var(--spacing-xl);
  min-height: 100vh;
  background: var(--color-bg-light);
}

.home-content {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: var(--spacing-xl);
  max-width: 1200px;
  margin: 0 auto;
}

.action-panel {
  background: var(--color-bg-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--spacing-lg);
}

.panel-header {
  margin-bottom: var(--spacing-lg);
}

.panel-title {
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--color-text-primary);
}

.action-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--spacing-md);
}

/* Responsive */
@media (max-width: 1200px) {
  .home-content {
    grid-template-columns: 1fr;
  }
}
</style>
