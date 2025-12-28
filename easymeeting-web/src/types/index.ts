// 通用响应结构
export interface ResponseVO<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

// 验证码
export interface CheckCodeVo {
  checkCode: string
  checkCodeKey: string
}

// 用户信息
export interface UserInfoVo {
  userId: string
  nickName: string
  sex: number
  meetingNo: string
  token: string
  isAdmin: boolean
}

// 会议信息
export interface MeetingInfo {
  meetingId: string
  meetingNo: string
  meetingName: string
  createTime: string
  createUserId: string
  joinType: number
  joinPassword?: string
  startTime: string
  endTime: string
  status: number
}

// 分页结果
export interface PageResult<T> {
  pageNo: number
  pageSize: number
  total: number
  list: T[]
}

// 分页参数
export interface PageParams {
  pageNo?: number
  pageSize?: number
}

// 注册参数
export interface RegisterDTO {
  email: string
  nickName: string
  password: string
  checkCode: string
  checkCodeKey: string
}

// 登录参数
export interface LoginDTO {
  email: string
  password: string
  checkCode: string
  checkCodeKey: string
}

// 创建会议参数
export interface MeetingCreateParams {
  meetingNoType: number    // 0=个人会议号, 1=系统生成
  meetingName: string      // 会议主题
  joinType: number         // 加入类型
  joinPassword?: string    // 会议密码（可选，最多5位）
}

// 预加入会议参数
export interface PreJoinMeetingParams {
  meetingNo: string        // 会议号
  nickName: string         // 昵称
  password?: string        // 会议密码（可选）
}

// 加入会议参数
export interface JoinMeetingParams {
  videoOpen: boolean       // 是否开启摄像头
}

// 快速会议表单
export interface QuickMeetingForm {
  meetingNoType: number
  meetingName: string
  joinType: number
  joinPassword: string
}

// 加入会议表单
export interface JoinMeetingForm {
  meetingNo: string
  nickName: string
  joinPassword: string
  videoOpen: boolean
}


// 会议成员信息（WebSocket）
export interface MeetingMemberDto {
  userId: string
  nickName: string
  joinTime: number
  memberType: number
  status: number
  videoOpen: boolean
  sex: number
}

// 加入会议消息内容
export interface MeetingJoinContent {
  newMember: MeetingMemberDto
  meetingMemberList: MeetingMemberDto[]
}

// WebSocket 消息结构
export interface WebSocketMessage<T = any> {
  messageSendToType: number
  meetingId?: string
  messageType: number
  sendUserId?: string
  sendUserNickName?: string
  messageContent?: T
  receiveUserId?: string
  sendTime?: number
  messageId?: number
  status?: number
  fileName?: string
  fileType?: number
  fileSize?: number
}
