<template>
  <div class="chat-panel">
    <!-- 消息列表区域 -->
    <div class="chat-messages" ref="messagesRef">
      <div 
        v-for="(msg, index) in messages" 
        :key="index" 
        class="chat-message"
        :class="{ 'is-self': msg.sendUserId === currentUserId }"
      >
        <div class="msg-sender" v-if="msg.sendUserId !== currentUserId">
          {{ msg.sendUserNickName }}
        </div>
        <div class="msg-bubble">
          {{ msg.content }}
        </div>
        <div class="msg-time">{{ formatTime(msg.sendTime) }}</div>
      </div>
      
      <div v-if="messages.length === 0" class="empty-tip">
        暂无消息，开始聊天吧~
      </div>
    </div>
    
    <!-- 输入区域 -->
    <div class="chat-input">
      <el-input 
        v-model="inputText" 
        placeholder="输入消息..." 
        @keyup.enter="handleSend"
        :maxlength="500"
      />
      <el-button type="primary" @click="handleSend" :disabled="!inputText.trim()">
        发送
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'

// 聊天消息接口
export interface ChatMessage {
  sendUserId: string
  sendUserNickName: string
  content: string
  sendTime: number
}

const props = defineProps<{
  messages: ChatMessage[]
  currentUserId: string
}>()

const emit = defineEmits<{
  (e: 'send', content: string): void
}>()

const inputText = ref('')
const messagesRef = ref<HTMLElement | null>(null)

// 格式化时间
function formatTime(timestamp: number): string {
  const date = new Date(timestamp)
  return `${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`
}

// 发送消息
function handleSend() {
  const content = inputText.value.trim()
  if (!content) return
  
  emit('send', content)
  inputText.value = ''
}

// 滚动到底部
function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

// 监听消息变化，自动滚动到底部
watch(() => props.messages.length, () => {
  scrollToBottom()
})
</script>

<style scoped>
/* 微信风格配色 */
.chat-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f5f5f5;
}

/* 消息列表区域 - 微信灰色背景 */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #EDEDED;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 单条消息容器 */
.chat-message {
  max-width: 75%;
  display: flex;
  flex-direction: column;
}

/* 自己的消息靠右 */
.chat-message.is-self {
  align-self: flex-end;
  align-items: flex-end;
}

/* 他人的消息靠左 */
.chat-message:not(.is-self) {
  align-self: flex-start;
  align-items: flex-start;
}

/* 发送者名称 - 灰色 */
.msg-sender {
  font-size: 12px;
  color: #999999;
  margin-bottom: 4px;
  padding: 0 4px;
}

/* 消息气泡 */
.msg-bubble {
  padding: 10px 14px;
  border-radius: 4px;
  font-size: 14px;
  line-height: 1.5;
  word-break: break-word;
  color: #000000;
  position: relative;
}

/* 自己的消息 - 微信绿色气泡 */
.chat-message.is-self .msg-bubble {
  background: #95EC69;
  border-radius: 4px 0 4px 4px;
}

/* 他人的消息 - 白色气泡 */
.chat-message:not(.is-self) .msg-bubble {
  background: #FFFFFF;
  border-radius: 0 4px 4px 4px;
}

/* 时间戳 - 灰色 */
.msg-time {
  font-size: 10px;
  color: #999999;
  margin-top: 4px;
  padding: 0 4px;
}

/* 空消息提示 */
.empty-tip {
  text-align: center;
  color: #999999;
  padding: 40px 20px;
  font-size: 14px;
}

/* 输入区域 - 白色背景 */
.chat-input {
  display: flex;
  gap: 8px;
  padding: 12px 16px;
  background: #FFFFFF;
  border-top: 1px solid #E5E5E5;
}

.chat-input .el-input {
  flex: 1;
}

/* 输入框样式 */
.chat-input :deep(.el-input__wrapper) {
  background: #F5F5F5;
  border: none;
  box-shadow: none;
}

.chat-input :deep(.el-input__wrapper:hover),
.chat-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: none;
}

/* 发送按钮 */
.chat-input .el-button {
  background: #07C160;
  border-color: #07C160;
}

.chat-input .el-button:hover {
  background: #06AD56;
  border-color: #06AD56;
}

.chat-input .el-button:disabled {
  background: #A0CFFF;
  border-color: #A0CFFF;
}
</style>
