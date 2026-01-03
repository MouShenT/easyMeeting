<template>
  <AppShell>
    <div class="history-page">
      <div class="page-header">
        <h1 class="page-title">会议记录</h1>
      </div>

      <!-- Tab Filter -->
      <div class="tab-filter">
        <button 
          v-for="tab in tabs" 
          :key="tab.value"
          class="tab-btn"
          :class="{ active: activeTab === tab.value }"
          @click="handleTabChange(tab.value)"
        >
          {{ tab.label }}
        </button>
      </div>

      <!-- Meeting List -->
      <div class="meeting-list">
        <div v-if="loading" class="loading-state">
          <div class="skeleton-card" v-for="i in 3" :key="i"></div>
        </div>

        <div v-else-if="meetings.length === 0" class="empty-state">
          <el-icon :size="48"><Calendar /></el-icon>
          <p>暂无会议记录</p>
        </div>

        <div v-else class="meeting-cards">
          <div 
            v-for="meeting in meetings" 
            :key="meeting.meetingId" 
            class="meeting-card"
          >
            <div class="card-header">
              <span class="meeting-name">{{ meeting.meetingName }}</span>
              <span class="status-badge" :class="getStatusClass(meeting.status)">
                {{ getStatusText(meeting.status) }}
              </span>
            </div>
            <div class="card-body">
              <div class="info-row">
                <el-icon><Clock /></el-icon>
                <span>{{ formatDateTime(meeting.createTime) }}</span>
              </div>
              <div class="info-row">
                <el-icon><User /></el-icon>
                <span>会议号: {{ meeting.meetingNo }}</span>
              </div>
            </div>
            <div class="card-actions" v-if="meeting.status === 0">
              <el-button type="primary" size="small" @click="rejoinMeeting(meeting)">
                重新加入
              </el-button>
            </div>
          </div>
        </div>

        <!-- Load More -->
        <div v-if="hasMore && meetings.length > 0" class="load-more">
          <el-button :loading="loading" @click="loadMore">加载更多</el-button>
        </div>
      </div>
    </div>
  </AppShell>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { Calendar, Clock, User } from '@element-plus/icons-vue'
import AppShell from '@/components/AppShell.vue'
import { loadMeeting, loadMyCreatedMeeting, loadMyJoinedMeeting, joinMeeting, reserveJoinMeeting } from '@/api/meeting'
import type { MeetingInfo } from '@/types'

// 组件名称，用于 keep-alive
defineOptions({
  name: 'History'
})

const router = useRouter()

const tabs = [
  { label: '全部', value: 'all' },
  { label: '我创建的', value: 'created' },
  { label: '我参加的', value: 'joined' }
]

const activeTab = ref('all')
const meetings = ref<MeetingInfo[]>([])
const loading = ref(false)
const pageNo = ref(1)
const pageSize = 10
const total = ref(0)

const hasMore = computed(() => meetings.value.length < total.value)

async function fetchMeetings(reset = false) {
  if (reset) {
    pageNo.value = 1
    meetings.value = []
  }
  
  loading.value = true
  try {
    const params = { pageNo: pageNo.value, pageSize }
    let result
    
    switch (activeTab.value) {
      case 'created':
        result = await loadMyCreatedMeeting(params)
        break
      case 'joined':
        result = await loadMyJoinedMeeting(params)
        break
      default:
        result = await loadMeeting(params)
    }
    
    if (reset) {
      meetings.value = result.list
    } else {
      meetings.value.push(...result.list)
    }
    total.value = result.total
  } catch {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}

function handleTabChange(tab: string) {
  activeTab.value = tab
  fetchMeetings(true)
}

function loadMore() {
  pageNo.value++
  fetchMeetings()
}

// 重新加入会议
async function rejoinMeeting(meeting: MeetingInfo) {
  try {
    // 先调用 reserveJoinMeeting 设置 currentMeetingId 到 token
    // 需要获取当前用户昵称，这里使用会议名作为临时方案
    // TODO: 从用户信息中获取昵称
    const nickName = localStorage.getItem('nickName') || '用户'
    await reserveJoinMeeting(meeting.meetingId, nickName)
    await joinMeeting({ videoOpen: true })
    router.push(`/meeting/${meeting.meetingId}`)
  } catch (error) {
    console.error('重新加入会议失败:', error)
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

// 获取状态样式 (MeetingInfo: 0=进行中, 1=已结束)
function getStatusClass(status: number) {
  switch (status) {
    case 0: return 'ongoing'
    case 1: return 'ended'
    default: return 'ended'
  }
}

// 获取状态文本
function getStatusText(status: number) {
  switch (status) {
    case 0: return '进行中'
    case 1: return '已结束'
    default: return '已结束'
  }
}

// 是否已加载过数据
const hasLoaded = ref(false)

onMounted(() => {
  if (!hasLoaded.value) {
    fetchMeetings()
    hasLoaded.value = true
  }
})

// 当从 keep-alive 缓存中激活时，可选择刷新数据
onActivated(() => {
  // 如果需要每次进入都刷新，取消下面的注释
  // fetchMeetings(true)
})
</script>

<style scoped>
.history-page {
  padding: var(--spacing-xl);
  min-height: 100vh;
  background: var(--color-bg-light);
}

.page-header {
  margin-bottom: var(--spacing-lg);
}

.page-title {
  font-size: var(--font-size-2xl);
  font-weight: 600;
  color: var(--color-text-primary);
}

.tab-filter {
  display: flex;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-xl);
  background: var(--color-bg-white);
  padding: var(--spacing-xs);
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
  width: fit-content;
}

.tab-btn {
  padding: var(--spacing-sm) var(--spacing-lg);
  border: none;
  background: transparent;
  border-radius: var(--radius-sm);
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.tab-btn:hover {
  color: var(--color-text-primary);
}

.tab-btn.active {
  background: var(--color-primary);
  color: var(--color-text-white);
}

.meeting-list {
  max-width: 800px;
}

.loading-state {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.skeleton-card {
  height: 120px;
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

.meeting-cards {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.meeting-card {
  background: var(--color-bg-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--spacing-lg);
  transition: border-color var(--transition-fast);
}

.meeting-card:hover {
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

.status-badge.ongoing {
  background: rgba(212, 175, 55, 0.15);
  color: var(--color-accent);
}

.status-badge.ended {
  background: rgba(64, 64, 64, 0.08);
  color: var(--color-text-muted);
}

.status-badge.cancelled {
  background: rgba(220, 53, 69, 0.1);
  color: #dc3545;
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

.load-more {
  display: flex;
  justify-content: center;
  padding: var(--spacing-lg);
}
</style>
