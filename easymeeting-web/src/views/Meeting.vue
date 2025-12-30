<template>
  <div class="meeting-container">
    <!-- 顶部工具栏 -->
    <div class="meeting-header">
      <div class="header-left">
        <el-button type="text" class="back-btn" @click="handleBack">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </el-button>
        <div class="meeting-info">
          <span class="meeting-name">{{ meetingName }}</span>
          <span class="meeting-id">会议号: {{ meetingNo }}</span>
        </div>
      </div>
      <div class="meeting-time">
        <el-icon><Clock /></el-icon>
        <span>{{ formatDuration(duration) }}</span>
      </div>
    </div>

    <!-- 视频区域 -->
    <div class="video-area">
      <div class="video-grid" :class="gridClass">
        <div 
          v-for="member in members" 
          :key="member.userId" 
          class="video-item"
          :class="{ 'is-self': member.userId === userStore.userId }"
        >
          <video 
            v-if="member.videoOpen && hasVideoStream(member.userId)"
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
      <div v-if="isCreator" class="control-item end-meeting" @click="handleEndMeeting">
        <el-icon :size="24"><CircleClose /></el-icon>
        <span>结束会议</span>
      </div>
      <div class="control-item leave" @click="handleLeaveMeeting">
        <el-icon :size="24"><SwitchButton /></el-icon>
        <span>离开会议</span>
      </div>
    </div>

    <!-- 成员列表侧边栏 -->
    <el-drawer v-model="showMembers" title="会议成员" direction="rtl" size="320px">
      <MemberList 
        :members="members"
        :current-user-id="userStore.userId"
        :creator-id="creatorId"
        @kick="handleKickMember"
        @blacklist="handleBlacklistMember"
      />
    </el-drawer>

    <!-- 聊天侧边栏 -->
    <el-drawer v-model="showChat" title="会议聊天" direction="rtl" size="380px">
      <ChatPanel 
        :messages="chatMessages"
        :current-user-id="userStore.userId"
        @send="sendChatMessage"
      />
    </el-drawer>
  </div>
</template>


<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { 
  Clock, VideoCamera, VideoCameraFilled, Microphone, Mute,
  User, ChatDotRound, SwitchButton, ArrowLeft, CircleClose 
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { wsService, MessageType, MessageSendToType, MemberType, MemberStatus } from '@/utils/websocket'
import type { WebSocketMessage, MeetingMember, MeetingJoinContent, MeetingExitContent } from '@/utils/websocket'
import { webRTCManager } from '@/utils/webrtc'
import { kickOutMember, blacklistMember, finishMeeting, getCurrentMeeting, exitMeeting } from '@/api/meeting'
import MemberList from '@/components/MemberList.vue'
import ChatPanel from '@/components/ChatPanel.vue'
import type { ChatMessage } from '@/components/ChatPanel.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 会议信息
const meetingId = ref(route.params.meetingId as string || '')
const meetingNo = ref('')
const meetingName = ref('视频会议')
const creatorId = ref('')
const duration = ref(0)
let durationTimer: number | null = null

// 成员列表
const members = ref<MeetingMember[]>([])

// 视频流 - 使用 reactive 确保深层响应式
const videoStreams = reactive<Record<string, MediaStream | null>>({})
const videoRefs = reactive<Record<string, HTMLVideoElement | null>>({})

// 检查用户是否有视频流
function hasVideoStream(userId: string): boolean {
  return !!videoStreams[userId]
}

// 本地媒体状态
const localVideoOpen = ref(true)
const localAudioOpen = ref(true)

// UI 状态
const showMembers = ref(false)
const showChat = ref(false)

// 聊天消息
const chatMessages = ref<ChatMessage[]>([])

// 是否是创建者
const isCreator = computed(() => userStore.userId === creatorId.value)

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
    // 确保流被正确绑定
    const stream = videoStreams[userId]
    if (stream) {
      (el as HTMLVideoElement).srcObject = stream
    } else if (userId === userStore.userId) {
      // 如果是自己但流还没准备好，尝试从 webRTCManager 获取
      const localStream = webRTCManager.getLocalStream()
      if (localStream) {
        videoStreams[userId] = localStream
        ;(el as HTMLVideoElement).srcObject = localStream
      }
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

// 返回主页
function handleBack() {
  router.push('/')
}

// 初始化本地媒体
async function initLocalMedia() {
  try {
    const stream = await webRTCManager.initLocalMedia(localVideoOpen.value, localAudioOpen.value)
    if (stream) {
      videoStreams[userStore.userId] = stream
      await nextTick()
      if (videoRefs[userStore.userId]) {
        videoRefs[userStore.userId]!.srcObject = stream
      }
    } else {
      ElMessage.warning('无法获取摄像头/麦克风权限，请检查浏览器设置')
      localVideoOpen.value = false
      localAudioOpen.value = false
    }
  } catch (error: any) {
    console.error('初始化媒体失败:', error)
    
    // 根据错误给出具体提示
    if (!window.isSecureContext) {
      ElMessage.error('请使用 HTTPS 或 localhost 访问，否则无法使用摄像头')
    } else if (error.name === 'NotAllowedError') {
      ElMessage.warning('您拒绝了摄像头/麦克风权限，请在浏览器设置中允许')
    } else if (error.name === 'NotFoundError') {
      ElMessage.warning('未检测到摄像头或麦克风设备')
    } else if (error.name === 'NotReadableError') {
      ElMessage.warning('摄像头或麦克风被其他程序占用')
    } else {
      ElMessage.warning('无法获取摄像头/麦克风权限')
    }
    
    localVideoOpen.value = false
    localAudioOpen.value = false
  }
}

// 切换视频
function toggleVideo() {
  localVideoOpen.value = !localVideoOpen.value
  webRTCManager.toggleVideo(localVideoOpen.value)
  
  const self = members.value.find(m => m.userId === userStore.userId)
  if (self) {
    self.videoOpen = localVideoOpen.value
  }
  
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
  webRTCManager.toggleAudio(localAudioOpen.value)
}

// 发送聊天消息
function sendChatMessage(content: string) {
  wsService.send({
    messageSendToType: MessageSendToType.GROUP,
    meetingId: meetingId.value,
    messageType: MessageType.CHAT_TEXT_MESSAGE,
    sendUserId: userStore.userId,
    sendUserNickName: userStore.nickName,
    messageContent: content,
    sendTime: Date.now()
  })
  
  chatMessages.value.push({
    sendUserId: userStore.userId,
    sendUserNickName: userStore.nickName,
    content: content,
    sendTime: Date.now()
  })
}

// 踢出成员
async function handleKickMember(userId: string) {
  try {
    await ElMessageBox.confirm('确定要踢出该成员吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await kickOutMember(userId)
    ElMessage.success('已踢出该成员')
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('踢出成员失败:', error)
    }
  }
}

// 拉黑成员
async function handleBlacklistMember(userId: string) {
  try {
    await ElMessageBox.confirm('确定要拉黑该成员吗？拉黑后该成员将无法重新加入会议。', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await blacklistMember(userId)
    ElMessage.success('已拉黑该成员')
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('拉黑成员失败:', error)
    }
  }
}

// 结束会议
async function handleEndMeeting() {
  try {
    await ElMessageBox.confirm('确定要结束会议吗？所有成员都将被移出会议。', '结束会议', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await finishMeeting()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('结束会议失败:', error)
    }
  }
}

// 离开会议
async function handleLeaveMeeting() {
  try {
    // 检查是否是最后一个人
    const isLastMember = members.value.length <= 1
    // 检查是否是创建者
    const isHost = userStore.userId === creatorId.value
    
    let confirmMessage = '确定要离开会议吗？'
    let confirmTitle = '离开会议'
    
    if (isLastMember) {
      confirmMessage = '您是会议中的最后一个成员，离开后会议将自动结束。确定要离开吗？'
      confirmTitle = '离开并结束会议'
    } else if (isHost) {
      confirmMessage = '您是会议主持人，离开后会议仍将继续。如需结束会议请点击"结束会议"按钮。确定要离开吗？'
    }
    
    await ElMessageBox.confirm(confirmMessage, confirmTitle, {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: isLastMember ? 'warning' : 'info'
    })
    
    // 调用后端退出接口
    await exitMeeting()
    
    cleanup()
    router.push('/')
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('离开会议失败:', error)
    }
  }
}

// 清理资源
function cleanup() {
  webRTCManager.cleanup()
  wsService.disconnect()
  if (durationTimer) {
    clearInterval(durationTimer)
    durationTimer = null
  }
}


// WebSocket 消息处理
function handleMemberJoin(message: WebSocketMessage<MeetingJoinContent>) {
  const content = message.messageContent
  if (content) {
    if (content.meetingMemberList && content.meetingMemberList.length > 0) {
      const localVideoState = localVideoOpen.value
      const localStream = webRTCManager.getLocalStream()
      
      // 保存现有的远程视频流
      const existingStreams: Record<string, MediaStream | null> = {}
      Object.keys(videoStreams).forEach(userId => {
        if (videoStreams[userId]) {
          existingStreams[userId] = videoStreams[userId]
        }
      })
      
      members.value = content.meetingMemberList.map(member => ({
        ...member,
        videoOpen: member.userId === userStore.userId ? localVideoState : member.videoOpen
      }))
      
      // 恢复所有视频流
      Object.keys(existingStreams).forEach(userId => {
        if (existingStreams[userId]) {
          videoStreams[userId] = existingStreams[userId]
        }
      })
      
      // 确保自己的视频流被保留
      if (localStream) {
        videoStreams[userStore.userId] = localStream
        nextTick(() => {
          if (videoRefs[userStore.userId]) {
            videoRefs[userStore.userId]!.srcObject = localStream
          }
        })
      }
      
      // 找到创建者
      const creator = members.value.find(m => m.memberType === MemberType.CREATOR)
      if (creator) {
        creatorId.value = creator.userId
      }
      
      // 与其他成员建立 WebRTC 连接
      // 策略：新加入的成员主动向所有现有成员发起连接
      // 判断是否是新成员：如果 newMember 是自己，说明自己刚加入
      const iAmNewMember = content.newMember?.userId === userStore.userId
      
      console.log('=== handleMemberJoin ===')
      console.log('My userId:', userStore.userId)
      console.log('newMember:', content.newMember?.userId)
      console.log('iAmNewMember:', iAmNewMember)
      console.log('Total members:', members.value.length)
      console.log('Current connections:', webRTCManager.getConnectionCount())
      
      members.value.forEach(member => {
        if (member.userId !== userStore.userId) {
          const hasConnection = webRTCManager.hasConnection(member.userId)
          
          console.log(`Checking member ${member.userId}: hasConnection=${hasConnection}`)
          
          if (!hasConnection) {
            if (iAmNewMember) {
              // 我是新加入的，主动向所有现有成员发起连接
              console.log('I am new member, initiating connection to:', member.userId)
              webRTCManager.initiateConnection(member.userId)
            } else if (content.newMember && content.newMember.userId === member.userId) {
              // 有新成员加入，等待新成员向我发起连接
              console.log('New member joined:', member.userId, '- waiting for them to initiate')
            } else {
              // 既不是我新加入，也不是对方新加入，使用 userId 比较决定谁发起
              // 这种情况可能发生在网络重连或其他边缘情况
              if (userStore.userId > member.userId) {
                console.log('Fallback: initiating connection to:', member.userId)
                webRTCManager.initiateConnection(member.userId)
              } else {
                console.log('Fallback: waiting for connection from:', member.userId)
              }
            }
          }
        }
      })
    }
    
    if (content.newMember && content.newMember.userId !== userStore.userId) {
      ElMessage.info(`${content.newMember.nickName} 加入了会议`)
    }
  }
}

function handleMemberExit(message: WebSocketMessage<MeetingExitContent>) {
  const content = message.messageContent
  const exitUserId = content?.exitUserId || message.sendUserId
  
  if (exitUserId) {
    // 检查是否是自己被踢出或拉黑
    if (exitUserId === userStore.userId) {
      const exitStatus = content?.exitStatus
      if (exitStatus === MemberStatus.KICK_OUT) {
        ElMessage.warning('您已被移出会议')
        cleanup()
        router.push('/')
        return
      } else if (exitStatus === MemberStatus.BLACKLIST) {
        ElMessage.error('您已被拉黑，无法重新加入此会议')
        cleanup()
        router.push('/')
        return
      }
      // 正常退出（EXIT_MEETING）不需要处理，因为是自己主动离开的
      return
    }
    
    members.value = members.value.filter(m => m.userId !== exitUserId)
    webRTCManager.closeConnection(exitUserId)
    delete videoStreams[exitUserId]
    
    const nickName = message.sendUserNickName || '成员'
    ElMessage.info(`${nickName} 离开了会议`)
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
  }
}

function handleMeetingEnd(_message: WebSocketMessage) {
  ElMessage.warning('会议已结束')
  cleanup()
  router.push('/')
}

// 远程流回调
function onRemoteStream(userId: string, stream: MediaStream) {
  console.log('Received remote stream for user:', userId, 'tracks:', stream.getTracks().length)
  
  // 使用 Vue 的响应式方式设置流
  videoStreams[userId] = stream
  
  // 强制更新成员的 videoOpen 状态
  const member = members.value.find(m => m.userId === userId)
  if (member) {
    member.videoOpen = true
    console.log('Set videoOpen=true for member:', userId)
  } else {
    console.warn('Member not found for userId:', userId)
  }
  
  nextTick(() => {
    if (videoRefs[userId]) {
      videoRefs[userId]!.srcObject = stream
      console.log('Set srcObject for user:', userId)
    } else {
      console.log('Video ref not found for user:', userId, 'will retry...')
      // 如果 ref 还没准备好，稍后重试
      setTimeout(() => {
        if (videoRefs[userId]) {
          videoRefs[userId]!.srcObject = stream
          console.log('Retry: Set srcObject for user:', userId)
        } else {
          console.error('Video ref still not found after retry for user:', userId)
        }
      }, 500)
    }
  })
}

function onStreamRemoved(userId: string) {
  delete videoStreams[userId]
}

// 初始化
onMounted(async () => {
  meetingId.value = route.params.meetingId as string
  if (!meetingId.value) {
    ElMessage.error('会议ID无效')
    router.push('/')
    return
  }

  // 获取会议信息
  try {
    const meeting = await getCurrentMeeting()
    if (meeting) {
      meetingName.value = meeting.meetingName
      meetingNo.value = meeting.meetingNo
      creatorId.value = meeting.createUserId
    }
  } catch (error) {
    console.error('获取会议信息失败:', error)
  }

  // 初始化 WebRTC 管理器
  webRTCManager.init(meetingId.value, userStore.userId, onRemoteStream, onStreamRemoved)

  // 注册 WebSocket 消息处理器
  wsService.on(MessageType.ADD_MEETING_ROOM, handleMemberJoin)
  wsService.on(MessageType.EXIT_MEETING_ROOM, handleMemberExit)
  wsService.on(MessageType.MEETING_USER_VIDEO_CHANGE, handleVideoChange)
  wsService.on(MessageType.CHAT_TEXT_MESSAGE, handleChatMessage)
  wsService.on(MessageType.FINISH_MEETING, handleMeetingEnd)

  // 初始化本地媒体
  await initLocalMedia()

  // 先添加自己到成员列表
  members.value = [{
    userId: userStore.userId,
    nickName: userStore.nickName,
    joinTime: Date.now(),
    memberType: MemberType.NORMAL,
    status: 0,
    videoOpen: localVideoOpen.value,
    sex: userStore.sex
  }]

  // 连接 WebSocket
  try {
    await wsService.connect()
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
  if (durationTimer) {
    clearInterval(durationTimer)
  }
  webRTCManager.cleanup()
  
  wsService.off(MessageType.ADD_MEETING_ROOM, handleMemberJoin)
  wsService.off(MessageType.EXIT_MEETING_ROOM, handleMemberExit)
  wsService.off(MessageType.MEETING_USER_VIDEO_CHANGE, handleVideoChange)
  wsService.off(MessageType.CHAT_TEXT_MESSAGE, handleChatMessage)
  wsService.off(MessageType.FINISH_MEETING, handleMeetingEnd)
})
</script>


<style scoped>
.meeting-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: #1a1a1a;
  color: #fff;
}

.meeting-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  background-color: #2d2d2d;
  border-bottom: 1px solid #3d3d3d;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.back-btn {
  color: #fff;
  font-size: 14px;
}

.back-btn:hover {
  color: #409eff;
}

.meeting-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.meeting-name {
  font-size: 16px;
  font-weight: 500;
}

.meeting-id {
  font-size: 12px;
  color: #999;
}

.meeting-time {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #ccc;
}

.video-area {
  flex: 1;
  padding: 16px;
  overflow: hidden;
}

.video-grid {
  display: grid;
  gap: 8px;
  height: 100%;
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
  background-color: #2d2d2d;
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
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  background-color: #3d3d3d;
}

.video-placeholder .avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background-color: #409eff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  font-weight: 500;
  color: #fff;
}

.member-info {
  position: absolute;
  bottom: 8px;
  left: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  background-color: rgba(0, 0, 0, 0.6);
  border-radius: 4px;
  font-size: 12px;
}

.member-info .video-off {
  color: #f56c6c;
}

.meeting-controls {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 32px;
  padding: 16px 20px;
  background-color: #2d2d2d;
  border-top: 1px solid #3d3d3d;
}

.control-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  padding: 8px 16px;
  border-radius: 8px;
  transition: background-color 0.2s;
}

.control-item:hover {
  background-color: #3d3d3d;
}

.control-item span {
  font-size: 12px;
  color: #ccc;
}

.control-item .is-off {
  color: #f56c6c;
}

.control-item.end-meeting {
  color: #f56c6c;
}

.control-item.end-meeting:hover {
  background-color: rgba(245, 108, 108, 0.2);
}

.control-item.leave {
  color: #e6a23c;
}

.control-item.leave:hover {
  background-color: rgba(230, 162, 60, 0.2);
}
</style>
