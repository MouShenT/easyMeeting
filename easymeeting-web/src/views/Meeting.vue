<template>
  <div class="meeting-container">
    <!-- 顶部工具栏 -->
    <div class="meeting-header">
      <div class="meeting-info">
        <span class="meeting-name">{{ meetingName }}</span>
        <span class="meeting-id">会议号: {{ meetingId }}</span>
      </div>
      <div class="meeting-time">
        <el-icon><Clock /></el-icon>
        <span>{{ formatDuration(duration) }}</span>
      </div>
    </div>

    <!-- 视频区域 -->
    <div class="video-area">
      <!-- 本地视频 -->
      <div class="video-grid" :class="gridClass">
        <div 
          v-for="member in members" 
          :key="member.userId" 
          class="video-item"
          :class="{ 'is-self': member.userId === userStore.userId }"
        >
          <video 
            v-if="member.videoOpen && videoStreams[member.userId]"
            :ref="el => setVideoRef(member.userId, el)"
            autoplay
            playsinline
            :muted="member.userId === userStore.userId"
          ></video>
          <div v-else class="video-placeholder">
            <div class="avatar">{{ member.nickName.charAt(0) }}</div>
          </div>
          <div class="member-info">
            <span class="member-name">{{ member.nickName }}</span>
            <el-icon v-if="!member.videoOpen" class="video-off"><VideoCameraFilled /></el-icon>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部控制栏 -->
    <div class="meeting-controls">
      <div class="control-item" @click="toggleVideo">
        <el-icon :size="24" :class="{ 'is-off': !localVideoOpen }">
          <VideoCamera v-if="localVideoOpen" />
          <VideoCameraFilled v-else />
        </el-icon>
        <span>{{ localVideoOpen ? '关闭视频' : '开启视频' }}</span>
      </div>
      <div class="control-item" @click="toggleAudio">
        <el-icon :size="24" :class="{ 'is-off': !localAudioOpen }">
          <Microphone v-if="localAudioOpen" />
          <Mute v-else />
        </el-icon>
        <span>{{ localAudioOpen ? '静音' : '取消静音' }}</span>
      </div>
      <div class="control-item" @click="showMembers = !showMembers">
        <el-icon :size="24"><User /></el-icon>
        <span>成员 ({{ members.length }})</span>
      </div>
      <div class="control-item" @click="showChat = !showChat">
        <el-icon :size="24"><ChatDotRound /></el-icon>
        <span>聊天</span>
      </div>
      <div class="control-item leave" @click="handleLeaveMeeting">
        <el-icon :size="24"><SwitchButton /></el-icon>
        <span>离开会议</span>
      </div>
    </div>

    <!-- 成员列表侧边栏 -->
    <el-drawer v-model="showMembers" title="会议成员" direction="rtl" size="300px">
      <div class="member-list">
        <div v-for="member in members" :key="member.userId" class="member-item">
          <div class="member-avatar">{{ member.nickName.charAt(0) }}</div>
          <div class="member-detail">
            <span class="name">{{ member.nickName }}</span>
            <span v-if="member.memberType === 0" class="host-tag">主持人</span>
          </div>
          <div class="member-status">
            <el-icon v-if="member.videoOpen" class="status-on"><VideoCamera /></el-icon>
            <el-icon v-else class="status-off"><VideoCameraFilled /></el-icon>
          </div>
        </div>
      </div>
    </el-drawer>

    <!-- 聊天侧边栏 -->
    <el-drawer v-model="showChat" title="会议聊天" direction="rtl" size="350px">
      <div class="chat-container">
        <div class="chat-messages" ref="chatMessagesRef">
          <div 
            v-for="(msg, index) in chatMessages" 
            :key="index" 
            class="chat-message"
            :class="{ 'is-self': msg.sendUserId === userStore.userId }"
          >
            <div class="msg-sender">{{ msg.sendUserNickName }}</div>
            <div class="msg-content">{{ msg.content }}</div>
            <div class="msg-time">{{ formatTime(msg.sendTime) }}</div>
          </div>
        </div>
        <div class="chat-input">
          <el-input 
            v-model="chatInput" 
            placeholder="输入消息..." 
            @keyup.enter="sendChatMessage"
          />
          <el-button type="primary" @click="sendChatMessage">发送</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { 
  Clock, VideoCamera, VideoCameraFilled, Microphone, Mute,
  User, ChatDotRound, SwitchButton 
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { wsService, MessageType, MessageSendToType } from '@/utils/websocket'
import type { WebSocketMessage, MeetingMember, MeetingJoinContent } from '@/utils/websocket'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 会议信息
const meetingId = ref(route.params.meetingId as string || '')
const meetingName = ref('视频会议')
const duration = ref(0)
let durationTimer: number | null = null

// 成员列表
const members = ref<MeetingMember[]>([])

// 视频流
const videoStreams = reactive<Record<string, MediaStream>>({})
const videoRefs = reactive<Record<string, HTMLVideoElement | null>>({})

// 本地媒体状态
const localVideoOpen = ref(true)
const localAudioOpen = ref(true)
let localStream: MediaStream | null = null

// UI 状态
const showMembers = ref(false)
const showChat = ref(false)

// 聊天
interface ChatMessage {
  sendUserId: string
  sendUserNickName: string
  content: string
  sendTime: number
}
const chatMessages = ref<ChatMessage[]>([])
const chatInput = ref('')
const chatMessagesRef = ref<HTMLElement | null>(null)

// 计算视频网格样式
const gridClass = computed(() => {
  const count = members.value.length
  if (count <= 1) return 'grid-1'
  if (count <= 2) return 'grid-2'
  if (count <= 4) return 'grid-4'
  if (count <= 6) return 'grid-6'
  return 'grid-9'
})

// 设置视频元素引用
function setVideoRef(userId: string, el: any) {
  if (el) {
    videoRefs[userId] = el as HTMLVideoElement
    // 如果已有流，设置到视频元素
    if (videoStreams[userId]) {
      (el as HTMLVideoElement).srcObject = videoStreams[userId]
    }
  }
}

// 格式化时长
function formatDuration(seconds: number): string {
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  if (h > 0) {
    return `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
  }
  return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
}

// 格式化时间
function formatTime(timestamp: number): string {
  const date = new Date(timestamp)
  return `${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`
}

// 初始化本地媒体
async function initLocalMedia() {
  try {
    localStream = await navigator.mediaDevices.getUserMedia({
      video: localVideoOpen.value,
      audio: localAudioOpen.value
    })
    videoStreams[userStore.userId] = localStream
    
    // 设置到视频元素
    await nextTick()
    if (videoRefs[userStore.userId]) {
      videoRefs[userStore.userId]!.srcObject = localStream
    }
  } catch (error) {
    console.error('Failed to get local media:', error)
    ElMessage.warning('无法获取摄像头/麦克风权限')
    localVideoOpen.value = false
    localAudioOpen.value = false
  }
}

// 切换视频
function toggleVideo() {
  localVideoOpen.value = !localVideoOpen.value
  if (localStream) {
    localStream.getVideoTracks().forEach(track => {
      track.enabled = localVideoOpen.value
    })
  }
  // 更新自己在成员列表中的状态
  const self = members.value.find(m => m.userId === userStore.userId)
  if (self) {
    self.videoOpen = localVideoOpen.value
  }
  // 通知其他成员
  wsService.send({
    messageSendToType: MessageSendToType.GROUP,
    meetingId: meetingId.value,
    messageType: MessageType.MEETING_USER_VIDEO_CHANGE,
    sendUserId: userStore.userId,
    messageContent: { videoOpen: localVideoOpen.value }
  })
}

// 切换音频
function toggleAudio() {
  localAudioOpen.value = !localAudioOpen.value
  if (localStream) {
    localStream.getAudioTracks().forEach(track => {
      track.enabled = localAudioOpen.value
    })
  }
}

// 发送聊天消息
function sendChatMessage() {
  if (!chatInput.value.trim()) return
  
  wsService.send({
    messageSendToType: MessageSendToType.GROUP,
    meetingId: meetingId.value,
    messageType: MessageType.CHAT_TEXT_MESSAGE,
    sendUserId: userStore.userId,
    sendUserNickName: userStore.nickName,
    messageContent: chatInput.value.trim(),
    sendTime: Date.now()
  })
  
  // 本地添加消息
  chatMessages.value.push({
    sendUserId: userStore.userId,
    sendUserNickName: userStore.nickName,
    content: chatInput.value.trim(),
    sendTime: Date.now()
  })
  
  chatInput.value = ''
  scrollChatToBottom()
}

function scrollChatToBottom() {
  nextTick(() => {
    if (chatMessagesRef.value) {
      chatMessagesRef.value.scrollTop = chatMessagesRef.value.scrollHeight
    }
  })
}

// 离开会议
async function handleLeaveMeeting() {
  try {
    await ElMessageBox.confirm('确定要离开会议吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    // 发送离开消息
    wsService.send({
      messageSendToType: MessageSendToType.GROUP,
      meetingId: meetingId.value,
      messageType: MessageType.EXIT_MEETING_ROOM,
      sendUserId: userStore.userId,
      sendUserNickName: userStore.nickName
    })
    
    // 停止本地媒体
    if (localStream) {
      localStream.getTracks().forEach(track => track.stop())
    }
    
    // 断开 WebSocket
    wsService.disconnect()
    
    // 返回首页
    router.push('/')
  } catch {
    // 用户取消
  }
}

// WebSocket 消息处理
function handleMemberJoin(message: WebSocketMessage<MeetingJoinContent>) {
  const content = message.messageContent
  console.log('handleMemberJoin received:', content)
  if (content) {
    // 更新成员列表（从服务器获取的完整列表）
    if (content.meetingMemberList && content.meetingMemberList.length > 0) {
      // 保留本地视频状态
      const localVideoState = localVideoOpen.value
      members.value = content.meetingMemberList.map(member => ({
        ...member,
        // 如果是自己，保留本地视频状态
        videoOpen: member.userId === userStore.userId ? localVideoState : member.videoOpen
      }))
      console.log('Updated members list:', members.value)
    }
    
    // 如果是新成员加入，显示提示
    if (content.newMember && content.newMember.userId !== userStore.userId) {
      ElMessage.info(`${content.newMember.nickName} 加入了会议`)
    }
  }
}

function handleMemberExit(message: WebSocketMessage) {
  const userId = message.sendUserId
  if (userId) {
    members.value = members.value.filter(m => m.userId !== userId)
    delete videoStreams[userId]
    ElMessage.info(`${message.sendUserNickName || '成员'} 离开了会议`)
  }
}

function handleVideoChange(message: WebSocketMessage) {
  const userId = message.sendUserId
  const content = message.messageContent as { videoOpen: boolean }
  if (userId && content) {
    const member = members.value.find(m => m.userId === userId)
    if (member) {
      member.videoOpen = content.videoOpen
    }
  }
}

function handleChatMessage(message: WebSocketMessage) {
  if (message.sendUserId !== userStore.userId) {
    chatMessages.value.push({
      sendUserId: message.sendUserId || '',
      sendUserNickName: message.sendUserNickName || '',
      content: message.messageContent as string,
      sendTime: message.sendTime || Date.now()
    })
    scrollChatToBottom()
  }
}

function handleMeetingEnd(_message: WebSocketMessage) {
  ElMessage.warning('会议已结束')
  if (localStream) {
    localStream.getTracks().forEach(track => track.stop())
  }
  wsService.disconnect()
  router.push('/')
}

// 初始化
onMounted(async () => {
  // 获取会议ID
  meetingId.value = route.params.meetingId as string
  if (!meetingId.value) {
    ElMessage.error('会议ID无效')
    router.push('/')
    return
  }

  // 注册 WebSocket 消息处理器
  wsService.on(MessageType.ADD_MEETING_ROOM, handleMemberJoin)
  wsService.on(MessageType.EXIT_MEETING_ROOM, handleMemberExit)
  wsService.on(MessageType.MEETING_USER_VIDEO_CHANGE, handleVideoChange)
  wsService.on(MessageType.CHAT_TEXT_MESSAGE, handleChatMessage)
  wsService.on(MessageType.FINISH_MEETING, handleMeetingEnd)

  // 初始化本地媒体
  await initLocalMedia()

  // 先添加自己到成员列表（临时显示，等待服务器返回完整列表）
  members.value = [{
    userId: userStore.userId,
    nickName: userStore.nickName,
    joinTime: Date.now(),
    memberType: 1,
    status: 0,
    videoOpen: localVideoOpen.value,
    sex: userStore.sex
  }]

  // 连接 WebSocket（连接成功后服务器会发送成员列表）
  try {
    await wsService.connect()
    console.log('WebSocket connected for meeting:', meetingId.value)
    ElMessage.success('已连接到会议服务器')
  } catch (error) {
    console.error('Failed to connect WebSocket:', error)
    ElMessage.error('连接会议服务器失败，请检查网络连接')
  }

  // 开始计时
  durationTimer = window.setInterval(() => {
    duration.value++
  }, 1000)
})

onUnmounted(() => {
  // 清理
  if (durationTimer) {
    clearInterval(durationTimer)
  }
  if (localStream) {
    localStream.getTracks().forEach(track => track.stop())
  }
  
  // 移除消息处理器
  wsService.off(MessageType.ADD_MEETING_ROOM, handleMemberJoin)
  wsService.off(MessageType.EXIT_MEETING_ROOM, handleMemberExit)
  wsService.off(MessageType.MEETING_USER_VIDEO_CHANGE, handleVideoChange)
  wsService.off(MessageType.CHAT_TEXT_MESSAGE, handleChatMessage)
  wsService.off(MessageType.FINISH_MEETING, handleMeetingEnd)
})
</script>

<style scoped>
.meeting-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #1a1a2e;
  color: #fff;
}

.meeting-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 24px;
  background: rgba(0, 0, 0, 0.3);
}

.meeting-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.meeting-name {
  font-size: 16px;
  font-weight: 500;
}

.meeting-id {
  font-size: 12px;
  color: #aaa;
}

.meeting-time {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #aaa;
}

.video-area {
  flex: 1;
  padding: 16px;
  overflow: hidden;
}

.video-grid {
  height: 100%;
  display: grid;
  gap: 8px;
}

.video-grid.grid-1 {
  grid-template-columns: 1fr;
}

.video-grid.grid-2 {
  grid-template-columns: repeat(2, 1fr);
}

.video-grid.grid-4 {
  grid-template-columns: repeat(2, 1fr);
  grid-template-rows: repeat(2, 1fr);
}

.video-grid.grid-6 {
  grid-template-columns: repeat(3, 1fr);
  grid-template-rows: repeat(2, 1fr);
}

.video-grid.grid-9 {
  grid-template-columns: repeat(3, 1fr);
  grid-template-rows: repeat(3, 1fr);
}

.video-item {
  position: relative;
  background: #2d2d44;
  border-radius: 8px;
  overflow: hidden;
}

.video-item.is-self {
  border: 2px solid #409eff;
}

.video-item video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.video-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.video-placeholder .avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  font-weight: 500;
}

.member-info {
  position: absolute;
  bottom: 8px;
  left: 8px;
  right: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 8px;
  background: rgba(0, 0, 0, 0.5);
  border-radius: 4px;
}

.member-name {
  font-size: 12px;
}

.video-off {
  color: #f56c6c;
}

.meeting-controls {
  display: flex;
  justify-content: center;
  gap: 32px;
  padding: 16px;
  background: rgba(0, 0, 0, 0.3);
}

.control-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  padding: 8px 16px;
  border-radius: 8px;
  transition: background 0.2s;
}

.control-item:hover {
  background: rgba(255, 255, 255, 0.1);
}

.control-item span {
  font-size: 12px;
  color: #aaa;
}

.control-item .el-icon {
  color: #fff;
}

.control-item .el-icon.is-off {
  color: #f56c6c;
}

.control-item.leave .el-icon {
  color: #f56c6c;
}

.control-item.leave span {
  color: #f56c6c;
}

/* 成员列表 */
.member-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.member-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px;
  border-radius: 8px;
  background: #f5f7fa;
}

.member-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
}

.member-detail {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
}

.member-detail .name {
  font-size: 14px;
  color: #333;
}

.host-tag {
  font-size: 10px;
  padding: 2px 6px;
  background: #409eff;
  color: #fff;
  border-radius: 4px;
}

.member-status .status-on {
  color: #67c23a;
}

.member-status .status-off {
  color: #909399;
}

/* 聊天 */
.chat-container {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chat-message {
  max-width: 80%;
}

.chat-message.is-self {
  align-self: flex-end;
}

.chat-message.is-self .msg-content {
  background: #409eff;
  color: #fff;
}

.msg-sender {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.msg-content {
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 8px;
  font-size: 14px;
  word-break: break-word;
}

.msg-time {
  font-size: 10px;
  color: #c0c4cc;
  margin-top: 4px;
  text-align: right;
}

.chat-input {
  display: flex;
  gap: 8px;
  padding: 12px;
  border-top: 1px solid #ebeef5;
}

.chat-input .el-input {
  flex: 1;
}
</style>
