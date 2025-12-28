<template>
  <div class="history-container">
    <NavBar />
    <div class="history-content">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="全部" name="all" />
        <el-tab-pane label="我创建的" name="created" />
        <el-tab-pane label="我参加的" name="joined" />
      </el-tabs>
      
      <div class="meeting-list" v-loading="loading">
        <template v-if="meetings.length > 0">
          <MeetingCard v-for="meeting in meetings" :key="meeting.meetingId" :meeting="meeting" />
          <div v-if="hasMore" class="load-more" @click="loadMore">加载更多</div>
          <div v-else class="no-more">没有更多了</div>
        </template>
        <el-empty v-else description="暂无会议记录" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import NavBar from '@/components/NavBar.vue'
import MeetingCard from '@/components/MeetingCard.vue'
import { loadMeeting, loadMyCreatedMeeting, loadMyJoinedMeeting } from '@/api/meeting'
import type { MeetingInfo } from '@/types'

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

function handleTabChange() {
  fetchMeetings(true)
}

function loadMore() {
  pageNo.value++
  fetchMeetings()
}

onMounted(() => {
  fetchMeetings()
})
</script>

<style scoped>
.history-container {
  min-height: 100vh;
  background: #f5f7fa;
}
.history-content {
  padding: 20px 40px;
  max-width: 800px;
  margin: 0 auto;
}
.meeting-list {
  margin-top: 20px;
  min-height: 300px;
}
.load-more, .no-more {
  text-align: center;
  padding: 16px;
  color: #909399;
  cursor: pointer;
}
.load-more:hover {
  color: #409eff;
}
</style>
