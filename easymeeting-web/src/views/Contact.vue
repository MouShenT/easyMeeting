<template>
  <AppShell>
    <div class="contact-page">
      <!-- 左侧：搜索和联系人列表 -->
      <div class="contact-left">
        <!-- 搜索区域 -->
        <div class="search-section">
          <div class="search-box">
            <el-input
              v-model="searchKeyword"
              placeholder="搜索用户ID添加好友"
              :prefix-icon="Search"
              clearable
              @keyup.enter="handleSearch"
            />
            <el-button type="primary" :icon="Search" @click="handleSearch" :loading="searchLoading">
              搜索
            </el-button>
          </div>
        </div>

        <!-- 搜索结果 -->
        <div v-if="searchResult" class="search-result">
          <div class="search-result-card">
            <div class="result-avatar">
              {{ searchResult.nickName.charAt(0).toUpperCase() }}
            </div>
            <div class="result-info">
              <div class="result-name">{{ searchResult.nickName }}</div>
              <div class="result-id">ID: {{ searchResult.userId }}</div>
            </div>
            <div class="result-action">
              <template v-if="searchResult.status === ContactSearchStatus.SELF">
                <el-tag type="info">这是你自己</el-tag>
              </template>
              <template v-else-if="searchResult.status === ContactSearchStatus.FRIEND">
                <el-tag type="success">已是好友</el-tag>
              </template>
              <template v-else-if="searchResult.status === ContactSearchStatus.PENDING">
                <el-tag type="warning">申请中</el-tag>
              </template>
              <template v-else-if="searchResult.status === ContactSearchStatus.BLACKLISTED">
                <el-tag type="danger">已被拉黑</el-tag>
              </template>
              <template v-else-if="searchResult.status === ContactSearchStatus.BE_FRIEND">
                <el-button type="primary" size="small" @click="handleAddContact" :loading="applyLoading">
                  直接添加
                </el-button>
              </template>
              <template v-else>
                <el-button type="primary" size="small" @click="handleAddContact" :loading="applyLoading">
                  添加好友
                </el-button>
              </template>
            </div>
            <el-button :icon="Close" circle size="small" class="close-btn" @click="searchResult = null" />
          </div>
        </div>

        <!-- 联系人列表 -->
        <div class="contact-list-section">
          <div class="section-header">
            <h3>我的好友</h3>
            <span class="contact-count">{{ contacts.length }}</span>
          </div>
          
          <div v-if="contactsLoading" class="loading-state">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>加载中...</span>
          </div>
          
          <div v-else-if="contacts.length === 0" class="empty-state">
            <el-empty description="暂无好友，搜索添加吧" :image-size="80" />
          </div>
          
          <div v-else class="contact-list">
            <div
              v-for="contact in contacts"
              :key="contact.contactId"
              class="contact-item"
            >
              <div class="contact-avatar" :class="{ online: contact.online }">
                {{ contact.contactNickName.charAt(0).toUpperCase() }}
                <span v-if="contact.online" class="online-dot"></span>
              </div>
              <div class="contact-info">
                <div class="contact-name">{{ contact.contactNickName }}</div>
                <div class="contact-status">
                  {{ contact.online ? '在线' : '离线' }}
                </div>
              </div>
              <el-dropdown trigger="click" @command="(cmd: string) => handleContactCommand(cmd, contact)">
                <el-button
                  :icon="MoreFilled"
                  circle
                  size="small"
                  class="more-btn"
                  :loading="operatingContactId === contact.contactId"
                />
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="delete" :icon="Delete">
                      删除好友
                    </el-dropdown-item>
                    <el-dropdown-item command="blacklist" :icon="CircleClose" divided>
                      拉黑
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧：好友申请列表 -->
      <div class="contact-right">
        <div class="section-header">
          <h3>好友申请</h3>
          <span v-if="pendingCount > 0" class="pending-badge">{{ pendingCount }}</span>
        </div>
        
        <div v-if="appliesLoading" class="loading-state">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>加载中...</span>
        </div>
        
        <div v-else-if="applies.length === 0" class="empty-state">
          <el-empty description="暂无好友申请" :image-size="80" />
        </div>
        
        <div v-else class="apply-list">
          <div
            v-for="apply in applies"
            :key="apply.applyId"
            class="apply-item"
          >
            <div class="apply-avatar">
              {{ apply.applyUserNickName.charAt(0).toUpperCase() }}
            </div>
            <div class="apply-info">
              <div class="apply-name">{{ apply.applyUserNickName }}</div>
              <div class="apply-time">{{ formatTime(apply.lastApplyTime) }}</div>
            </div>
            <div class="apply-actions">
              <template v-if="apply.status === ApplyStatus.INIT">
                <el-button
                  type="primary"
                  size="small"
                  @click="handleDealApply(apply.applyUserId, ApplyStatus.PASS)"
                  :loading="dealingApplyId === apply.applyUserId"
                >
                  同意
                </el-button>
                <el-button
                  size="small"
                  @click="handleDealApply(apply.applyUserId, ApplyStatus.REJECT)"
                  :loading="dealingApplyId === apply.applyUserId"
                >
                  拒绝
                </el-button>
              </template>
              <template v-else>
                <el-tag :type="getApplyStatusType(apply.status)" size="small">
                  {{ getApplyStatusText(apply.status) }}
                </el-tag>
              </template>
            </div>
          </div>
        </div>
      </div>
    </div>
  </AppShell>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Close, Loading, MoreFilled, Delete, CircleClose } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import AppShell from '@/components/AppShell.vue'
import {
  loadContactUser,
  loadContactApply,
  searchContact,
  applyAddContact,
  dealWithApply,
  operateContact,
  ContactSearchStatus,
  ApplyStatus,
  ContactStatus
} from '@/api/contact'
import type { UserContactVo, UserContactApplyVo, UserInfoVoForSearch } from '@/api/contact'

// 搜索相关
const searchKeyword = ref('')
const searchLoading = ref(false)
const searchResult = ref<UserInfoVoForSearch | null>(null)
const applyLoading = ref(false)

// 联系人列表
const contacts = ref<UserContactVo[]>([])
const contactsLoading = ref(false)

// 好友申请列表
const applies = ref<UserContactApplyVo[]>([])
const appliesLoading = ref(false)
const dealingApplyId = ref<string | null>(null)

// 操作联系人
const operatingContactId = ref<string | null>(null)

// 待处理申请数量
const pendingCount = computed(() => {
  return applies.value.filter(a => a.status === ApplyStatus.INIT).length
})

// 加载联系人列表
async function loadContacts() {
  contactsLoading.value = true
  try {
    contacts.value = await loadContactUser()
  } catch (error) {
    console.error('加载联系人失败:', error)
  } finally {
    contactsLoading.value = false
  }
}

// 加载好友申请列表
async function loadApplies() {
  appliesLoading.value = true
  try {
    applies.value = await loadContactApply()
  } catch (error) {
    console.error('加载好友申请失败:', error)
  } finally {
    appliesLoading.value = false
  }
}

// 搜索用户
async function handleSearch() {
  if (!searchKeyword.value.trim()) {
    ElMessage.warning('请输入用户ID')
    return
  }
  searchLoading.value = true
  searchResult.value = null
  try {
    const result = await searchContact(searchKeyword.value.trim())
    if (result) {
      searchResult.value = result
    } else {
      ElMessage.info('未找到该用户')
    }
  } catch (error) {
    console.error('搜索失败:', error)
  } finally {
    searchLoading.value = false
  }
}

// 添加好友
async function handleAddContact() {
  if (!searchResult.value) return
  applyLoading.value = true
  try {
    const status = await applyAddContact(searchResult.value.userId)
    if (status === ApplyStatus.PASS) {
      ElMessage.success('添加成功')
      loadContacts()
    } else {
      ElMessage.success('申请已发送')
    }
    searchResult.value = null
    searchKeyword.value = ''
  } catch (error) {
    console.error('添加好友失败:', error)
  } finally {
    applyLoading.value = false
  }
}

// 处理好友申请
async function handleDealApply(applyUserId: string, status: number) {
  dealingApplyId.value = applyUserId
  try {
    await dealWithApply(applyUserId, status)
    ElMessage.success(status === ApplyStatus.PASS ? '已同意' : '已拒绝')
    // 刷新列表
    loadApplies()
    if (status === ApplyStatus.PASS) {
      loadContacts()
    }
  } catch (error) {
    console.error('处理申请失败:', error)
  } finally {
    dealingApplyId.value = null
  }
}

// 格式化时间
function formatTime(timeStr: string): string {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))
  
  if (days === 0) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  } else if (days === 1) {
    return '昨天'
  } else if (days < 7) {
    return `${days}天前`
  } else {
    return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
  }
}

// 获取申请状态类型
function getApplyStatusType(status: number): 'success' | 'info' | 'danger' {
  switch (status) {
    case ApplyStatus.PASS: return 'success'
    case ApplyStatus.REJECT: return 'info'
    case ApplyStatus.BLACKLIST: return 'danger'
    default: return 'info'
  }
}

// 获取申请状态文本
function getApplyStatusText(status: number): string {
  switch (status) {
    case ApplyStatus.PASS: return '已同意'
    case ApplyStatus.REJECT: return '已拒绝'
    case ApplyStatus.BLACKLIST: return '已拉黑'
    default: return '待处理'
  }
}

// 删除联系人
async function handleDeleteContact(contactId: string, contactName: string) {
  try {
    await ElMessageBox.confirm(
      `确定要删除好友 "${contactName}" 吗？`,
      '删除好友',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    operatingContactId.value = contactId
    await operateContact(contactId, ContactStatus.DEL)
    ElMessage.success('已删除')
    loadContacts()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除联系人失败:', error)
    }
  } finally {
    operatingContactId.value = null
  }
}

// 拉黑联系人
async function handleBlacklistContact(contactId: string, contactName: string) {
  try {
    await ElMessageBox.confirm(
      `确定要拉黑 "${contactName}" 吗？拉黑后对方将无法向你发送消息。`,
      '拉黑好友',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    operatingContactId.value = contactId
    await operateContact(contactId, ContactStatus.BLACK)
    ElMessage.success('已拉黑')
    loadContacts()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('拉黑联系人失败:', error)
    }
  } finally {
    operatingContactId.value = null
  }
}

// 处理联系人操作命令
function handleContactCommand(command: string, contact: UserContactVo) {
  if (command === 'delete') {
    handleDeleteContact(contact.contactId, contact.contactNickName)
  } else if (command === 'blacklist') {
    handleBlacklistContact(contact.contactId, contact.contactNickName)
  }
}

// 初始化
onMounted(() => {
  loadContacts()
  loadApplies()
})
</script>


<style scoped>
.contact-page {
  display: grid;
  grid-template-columns: 1fr 360px;
  gap: var(--spacing-lg);
  padding: var(--spacing-xl);
  min-height: 100vh;
  background: var(--color-bg-light);
}

/* 左侧区域 */
.contact-left {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

/* 搜索区域 */
.search-section {
  background: var(--color-bg-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--spacing-lg);
}

.search-box {
  display: flex;
  gap: var(--spacing-sm);
}

.search-box .el-input {
  flex: 1;
}

/* 搜索结果 */
.search-result {
  background: var(--color-bg-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--spacing-md);
}

.search-result-card {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  position: relative;
}

.result-avatar {
  width: 48px;
  height: 48px;
  background: var(--color-accent);
  border-radius: var(--radius-full);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-bg-dark);
  font-weight: 600;
  font-size: var(--font-size-lg);
}

.result-info {
  flex: 1;
}

.result-name {
  font-weight: 500;
  color: var(--color-text-primary);
}

.result-id {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.close-btn {
  position: absolute;
  top: -8px;
  right: -8px;
}

/* 联系人列表区域 */
.contact-list-section {
  background: var(--color-bg-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--spacing-lg);
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.section-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
}

.section-header h3 {
  font-size: var(--font-size-md);
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0;
}

.contact-count {
  background: var(--color-bg-light);
  color: var(--color-text-muted);
  padding: 2px 8px;
  border-radius: var(--radius-full);
  font-size: var(--font-size-xs);
}

.pending-badge {
  background: var(--color-error);
  color: white;
  padding: 2px 8px;
  border-radius: var(--radius-full);
  font-size: var(--font-size-xs);
  min-width: 20px;
  text-align: center;
}

/* 联系人列表 */
.contact-list {
  flex: 1;
  overflow-y: auto;
}

.contact-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-sm) var(--spacing-md);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background var(--transition-fast);
}

.contact-item:hover {
  background: var(--color-bg-light);
}

.contact-item:hover .more-btn {
  opacity: 1;
}

.more-btn {
  opacity: 0;
  transition: opacity var(--transition-fast);
}

.contact-avatar {
  width: 40px;
  height: 40px;
  background: var(--color-secondary);
  border-radius: var(--radius-full);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-weight: 500;
  position: relative;
}

.contact-avatar.online {
  background: var(--color-accent);
  color: var(--color-bg-dark);
}

.online-dot {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 10px;
  height: 10px;
  background: var(--color-success);
  border: 2px solid white;
  border-radius: var(--radius-full);
}

.contact-info {
  flex: 1;
}

.contact-name {
  font-weight: 500;
  color: var(--color-text-primary);
}

.contact-status {
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
}

/* 右侧区域 */
.contact-right {
  background: var(--color-bg-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--spacing-lg);
  height: fit-content;
  max-height: calc(100vh - var(--spacing-xl) * 2);
  overflow-y: auto;
}

/* 申请列表 */
.apply-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.apply-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-md);
  background: var(--color-bg-light);
  border-radius: var(--radius-md);
}

.apply-avatar {
  width: 40px;
  height: 40px;
  background: var(--color-secondary);
  border-radius: var(--radius-full);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-weight: 500;
  flex-shrink: 0;
}

.apply-info {
  flex: 1;
  min-width: 0;
}

.apply-name {
  font-weight: 500;
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.apply-time {
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
}

.apply-actions {
  display: flex;
  gap: var(--spacing-xs);
  flex-shrink: 0;
}

/* 加载和空状态 */
.loading-state,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-xl);
  color: var(--color-text-muted);
  gap: var(--spacing-sm);
}

.loading-state .el-icon {
  font-size: 24px;
}

/* 响应式 */
@media (max-width: 900px) {
  .contact-page {
    grid-template-columns: 1fr;
  }
  
  .contact-right {
    max-height: none;
  }
}
</style>
