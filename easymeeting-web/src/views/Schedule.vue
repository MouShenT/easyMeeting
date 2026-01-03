<template>
  <AppShell>
    <div class="schedule-page">
      <div class="page-header">
        <h1 class="page-title">预定会议</h1>
        <el-button class="create-btn" @click="showCreateDialog">
          <el-icon><Plus /></el-icon>
          新建预约
        </el-button>
      </div>

      <!-- 会议列表 -->
      <div class="reserve-list">
        <div v-if="loading" class="loading-state">
          <div class="skeleton-card" v-for="i in 3" :key="i"></div>
        </div>

        <div v-else-if="reserves.length === 0" class="empty-state">
          <el-icon :size="48"><Calendar /></el-icon>
          <p>暂无预约会议</p>
          <el-button @click="showCreateDialog">创建第一个预约</el-button>
        </div>

        <div v-else class="reserve-cards">
          <div 
            v-for="reserve in reserves" 
            :key="reserve.meetingId" 
            class="reserve-card"
          >
            <div class="card-header">
              <span class="meeting-name">{{ reserve.meetingName }}</span>
              <span class="status-badge" :class="getStatusClass(reserve)">
                {{ getStatusText(reserve) }}
              </span>
            </div>
            <div class="card-body">
              <div class="info-row">
                <el-icon><Clock /></el-icon>
                <span>{{ formatDateTime(reserve.startTime) }}</span>
              </div>
              <div class="info-row">
                <el-icon><Timer /></el-icon>
                <span>时长 {{ reserve.duration }} 分钟</span>
              </div>
              <div class="info-row" v-if="reserve.joinPassword">
                <el-icon><Lock /></el-icon>
                <span>需要密码</span>
              </div>
            </div>
            <div class="card-actions">
              <template v-if="reserve.realMeetingId">
                <el-button type="primary" size="small" @click="joinMeetingAction(reserve)">
                  加入会议
                </el-button>
              </template>
              <template v-else>
                <el-button type="primary" size="small" @click="startMeeting(reserve)">
                  开始会议
                </el-button>
              </template>
              <el-button size="small" @click="deleteReserve(reserve)">
                删除
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- 创建预约对话框 -->
      <el-dialog 
        v-model="createDialogVisible" 
        title="新建预约会议" 
        width="480px"
        :close-on-click-modal="false"
      >
        <el-form 
          ref="formRef" 
          :model="createForm" 
          :rules="formRules" 
          label-width="100px"
        >
          <el-form-item label="会议主题" prop="meetingName">
            <el-input 
              v-model="createForm.meetingName" 
              placeholder="请输入会议主题"
              maxlength="100"
              show-word-limit
            />
          </el-form-item>
          <el-form-item label="开始时间" prop="startTime">
            <el-date-picker
              v-model="createForm.startTime"
              type="datetime"
              placeholder="选择开始时间"
              format="YYYY-MM-DD HH:mm"
              value-format="YYYY-MM-DD HH:mm:ss"
              :disabled-date="disabledDate"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="会议时长" prop="duration">
            <el-select v-model="createForm.duration" style="width: 100%">
              <el-option label="30 分钟" :value="30" />
              <el-option label="60 分钟" :value="60" />
              <el-option label="90 分钟" :value="90" />
              <el-option label="120 分钟" :value="120" />
            </el-select>
          </el-form-item>
          <el-form-item label="会议密码">
            <el-input 
              v-model="createForm.joinPassword" 
              placeholder="可选，最多5位"
              maxlength="5"
              show-password
            />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="createDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="creating" @click="handleCreate">
            创建
          </el-button>
        </template>
      </el-dialog>
    </div>
  </AppShell>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Calendar, Clock, Timer, Lock } from '@element-plus/icons-vue'
import AppShell from '@/components/AppShell.vue'
import { 
  loadMeetingReserve, 
  createMeetingReserve, 
  delMeetingReserve,
  startReserveMeeting,
  joinReserveMeeting,
  type MeetingReserve 
} from '@/api/reserve'
import { joinMeeting } from '@/api/meeting'

// 组件名称，用于 keep-alive
defineOptions({
  name: 'Schedule'
})

const router = useRouter()

const loading = ref(false)
const reserves = ref<MeetingReserve[]>([])

const createDialogVisible = ref(false)
const creating = ref(false)
const formRef = ref<FormInstance>()

const createForm = reactive({
  meetingName: '',
  startTime: '',
  duration: 60,
  joinPassword: ''
})

const formRules: FormRules = {
  meetingName: [
    { required: true, message: '请输入会议主题', trigger: 'blur' }
  ],
  startTime: [
    { required: true, message: '请选择开始时间', trigger: 'change' }
  ]
}

// 加载预约列表
async function loadReserves() {
  loading.value = true
  try {
    reserves.value = await loadMeetingReserve()
  } catch (error) {
    console.error('加载预约会议失败:', error)
  } finally {
    loading.value = false
  }
}

// 显示创建对话框
function showCreateDialog() {
  createForm.meetingName = ''
  createForm.startTime = ''
  createForm.duration = 60
  createForm.joinPassword = ''
  createDialogVisible.value = true
}

// 禁用过去的日期
function disabledDate(date: Date) {
  return date.getTime() < Date.now() - 24 * 60 * 60 * 1000
}

// 创建预约
async function handleCreate() {
  if (!formRef.value) return
  await formRef.value.validate()

  creating.value = true
  try {
    await createMeetingReserve({
      meetingName: createForm.meetingName,
      startTime: createForm.startTime,
      duration: createForm.duration,
      joinPassword: createForm.joinPassword || undefined,
      joinType: createForm.joinPassword ? 1 : 0
    })
    ElMessage.success('预约创建成功')
    createDialogVisible.value = false
    loadReserves()
  } catch (error) {
    console.error('创建预约失败:', error)
  } finally {
    creating.value = false
  }
}

// 开始会议
async function startMeeting(reserve: MeetingReserve) {
  try {
    const realMeetingId = await startReserveMeeting(reserve.meetingId)
    await joinMeeting({ videoOpen: true })
    router.push(`/meeting/${realMeetingId}`)
  } catch (error) {
    console.error('开始会议失败:', error)
  }
}

// 加入会议
async function joinMeetingAction(reserve: MeetingReserve) {
  if (!reserve.realMeetingId) {
    ElMessage.warning('会议尚未开始')
    return
  }
  try {
    // 先调用 joinReserveMeeting 设置 currentMeetingId 到 token
    await joinReserveMeeting(reserve.meetingId)
    await joinMeeting({ videoOpen: true })
    router.push(`/meeting/${reserve.realMeetingId}`)
  } catch (error) {
    console.error('加入会议失败:', error)
  }
}

// 删除预约
async function deleteReserve(reserve: MeetingReserve) {
  try {
    await ElMessageBox.confirm('确定要删除这个预约会议吗？', '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await delMeetingReserve(reserve.meetingId)
    ElMessage.success('删除成功')
    loadReserves()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('删除预约失败:', error)
    }
  }
}

// 格式化日期时间
function formatDateTime(dateStr: string) {
  const date = new Date(dateStr)
  const month = date.getMonth() + 1
  const day = date.getDate()
  const hours = date.getHours().toString().padStart(2, '0')
  const minutes = date.getMinutes().toString().padStart(2, '0')
  return `${month}月${day}日 ${hours}:${minutes}`
}

// 获取状态样式
function getStatusClass(reserve: MeetingReserve) {
  if (reserve.realMeetingId) return 'started'
  return 'pending'
}

// 获取状态文本
function getStatusText(reserve: MeetingReserve) {
  if (reserve.realMeetingId) return '进行中'
  return '待开始'
}

// 是否已加载过数据
const hasLoaded = ref(false)

onMounted(() => {
  if (!hasLoaded.value) {
    loadReserves()
    hasLoaded.value = true
  }
})

// 当从 keep-alive 缓存中激活时刷新数据
onActivated(() => {
  if (hasLoaded.value) {
    loadReserves()
  }
})
</script>

<style scoped>
.schedule-page {
  padding: var(--spacing-xl);
  min-height: 100vh;
  background: var(--color-bg-light);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-xl);
}

.page-title {
  font-size: var(--font-size-2xl);
  font-weight: 600;
  color: var(--color-text-primary);
}

.create-btn {
  background: var(--color-primary);
  border: none;
  color: var(--color-text-white);
  padding: var(--spacing-sm) var(--spacing-lg);
  border-radius: var(--radius-md);
  font-weight: 500;
  transition: background var(--transition-fast);
}

.create-btn:hover {
  background: var(--color-secondary);
}

.reserve-list {
  max-width: 800px;
}

.loading-state {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.skeleton-card {
  height: 140px;
  background: linear-gradient(90deg, var(--color-bg-light) 25%, var(--color-border-light) 50%, var(--color-bg-light) 75%);
  background-size: 200% 100%;
  border-radius: var(--radius-lg);
  animation: skeleton-loading 1.5s infinite;
}

@keyframes skeleton-loading {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-2xl);
  color: var(--color-text-muted);
  gap: var(--spacing-md);
}

.empty-state .el-icon {
  color: var(--color-border);
}

.reserve-cards {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.reserve-card {
  background: var(--color-bg-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--spacing-lg);
  transition: border-color var(--transition-fast);
}

.reserve-card:hover {
  border-color: var(--color-border-hover);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-md);
}

.meeting-name {
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--color-text-primary);
}

.status-badge {
  font-size: var(--font-size-xs);
  padding: 2px 10px;
  border-radius: var(--radius-full);
}

.status-badge.pending {
  background: rgba(64, 64, 64, 0.1);
  color: var(--color-text-secondary);
}

.status-badge.started {
  background: rgba(212, 175, 55, 0.15);
  color: var(--color-accent);
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
}

.info-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.info-row .el-icon {
  color: var(--color-text-muted);
}

.card-actions {
  display: flex;
  gap: var(--spacing-sm);
}

.card-actions .el-button--primary {
  background: var(--color-primary);
  border-color: var(--color-primary);
}

.card-actions .el-button--primary:hover {
  background: var(--color-secondary);
  border-color: var(--color-secondary);
}

/* Dialog overrides */
:deep(.el-dialog) {
  border-radius: var(--radius-lg);
}

:deep(.el-dialog__header) {
  border-bottom: 1px solid var(--color-border);
  padding-bottom: var(--spacing-md);
}

:deep(.el-dialog__title) {
  font-weight: 600;
  color: var(--color-text-primary);
}
</style>
