import request from './index'
import type { MeetingInfo, PageResult, PageParams, MeetingCreateParams, JoinMeetingParams, PreJoinMeetingParams } from '@/types'

// 加载所有历史会议
export function loadMeeting(params: PageParams = {}): Promise<PageResult<MeetingInfo>> {
  return request.get('/meeting/loadMeeting', { params })
}

// 加载我创建的会议
export function loadMyCreatedMeeting(params: PageParams = {}): Promise<PageResult<MeetingInfo>> {
  return request.get('/meeting/loadMyCreatedMeeting', { params })
}

// 加载我参加的会议
export function loadMyJoinedMeeting(params: PageParams = {}): Promise<PageResult<MeetingInfo>> {
  return request.get('/meeting/loadMyJoinedMeeting', { params })
}

// 快速会议 - 创建并返回会议ID
export function quickMeeting(params: MeetingCreateParams): Promise<string> {
  return request.post('/meeting/quickMeeting', params)
}

// 预加入会议 - 验证会议号和密码，返回会议ID
export function preJoinMeeting(params: PreJoinMeetingParams): Promise<string> {
  return request.post('/meeting/preJoinMeeting', null, {
    params: {
      meetingNo: params.meetingNo,
      nickName: params.nickName,
      password: params.password
    }
  })
}

// 加入会议
export function joinMeeting(params: JoinMeetingParams): Promise<void> {
  return request.post('/meeting/joinMeeting', params)
}
