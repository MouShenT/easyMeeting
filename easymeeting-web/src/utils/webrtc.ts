import { wsService, MessageType, MessageSendToType } from './websocket'
import type { WebSocketMessage } from './websocket'

// WebRTC 配置
const rtcConfig: RTCConfiguration = {
  iceServers: [
    { urls: 'stun:stun.l.google.com:19302' },
    { urls: 'stun:stun1.l.google.com:19302' }
  ]
}

// 视频流回调类型
type StreamCallback = (userId: string, stream: MediaStream) => void
type StreamRemovedCallback = (userId: string) => void

/**
 * WebRTC 连接管理器
 * 封装 WebRTC 连接的创建、管理和清理逻辑
 */
export class WebRTCManager {
  private peerConnections: Map<string, RTCPeerConnection> = new Map()
  private localStream: MediaStream | null = null
  private meetingId: string = ''
  private userId: string = ''
  
  // 回调函数
  private onRemoteStream: StreamCallback | null = null
  private onStreamRemoved: StreamRemovedCallback | null = null

  constructor() {
    // 注册 WebSocket 消息处理器
    wsService.on(MessageType.WEBRTC_OFFER, this.handleOffer.bind(this))
    wsService.on(MessageType.WEBRTC_ANSWER, this.handleAnswer.bind(this))
    wsService.on(MessageType.WEBRTC_ICE_CANDIDATE, this.handleIceCandidate.bind(this))
  }

  /**
   * 初始化管理器
   */
  init(meetingId: string, userId: string, onRemoteStream: StreamCallback, onStreamRemoved: StreamRemovedCallback) {
    this.meetingId = meetingId
    this.userId = userId
    this.onRemoteStream = onRemoteStream
    this.onStreamRemoved = onStreamRemoved
  }

  /**
   * 初始化本地媒体流
   */
  async initLocalMedia(videoEnabled: boolean = true, audioEnabled: boolean = true): Promise<MediaStream | null> {
    try {
      // 检查是否在安全上下文中
      if (!window.isSecureContext) {
        console.error('getUserMedia requires a secure context (HTTPS or localhost)')
        throw new Error('需要 HTTPS 或 localhost 环境才能访问摄像头/麦克风')
      }
      
      // 检查浏览器是否支持
      if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
        console.error('getUserMedia is not supported in this browser')
        throw new Error('当前浏览器不支持摄像头/麦克风访问')
      }
      
      this.localStream = await navigator.mediaDevices.getUserMedia({
        video: videoEnabled,
        audio: audioEnabled
      })
      return this.localStream
    } catch (error: any) {
      console.error('Failed to get local media:', error)
      
      // 根据错误类型给出更详细的提示
      if (error.name === 'NotAllowedError' || error.name === 'PermissionDeniedError') {
        console.error('用户拒绝了摄像头/麦克风权限')
      } else if (error.name === 'NotFoundError' || error.name === 'DevicesNotFoundError') {
        console.error('未找到摄像头或麦克风设备')
      } else if (error.name === 'NotReadableError' || error.name === 'TrackStartError') {
        console.error('摄像头或麦克风被其他程序占用')
      } else if (error.name === 'OverconstrainedError') {
        console.error('请求的媒体约束无法满足')
      } else if (error.name === 'TypeError') {
        console.error('媒体约束配置错误')
      }
      
      return null
    }
  }

  /**
   * 获取本地媒体流
   */
  getLocalStream(): MediaStream | null {
    return this.localStream
  }

  /**
   * 创建与指定用户的 PeerConnection
   */
  createConnection(remoteUserId: string): RTCPeerConnection {
    console.log('Creating PeerConnection for:', remoteUserId)
    
    // 如果已存在连接，先关闭
    if (this.peerConnections.has(remoteUserId)) {
      this.closeConnection(remoteUserId)
    }
    
    const pc = new RTCPeerConnection(rtcConfig)
    this.peerConnections.set(remoteUserId, pc)
    
    // 添加本地流的所有轨道
    if (this.localStream) {
      console.log('Adding local tracks to PeerConnection, tracks:', this.localStream.getTracks().length)
      this.localStream.getTracks().forEach(track => {
        pc.addTrack(track, this.localStream!)
        console.log('Added track:', track.kind, track.enabled)
      })
    } else {
      console.warn('No local stream available when creating connection')
    }
    
    // 监听 ICE Candidate
    pc.onicecandidate = (event) => {
      if (event.candidate) {
        console.log('Sending ICE candidate to:', remoteUserId, 'type:', event.candidate.type)
        wsService.send({
          messageSendToType: MessageSendToType.USER,
          meetingId: this.meetingId,
          messageType: MessageType.WEBRTC_ICE_CANDIDATE,
          sendUserId: this.userId,
          receiveUserId: remoteUserId,
          messageContent: event.candidate
        })
      } else {
        console.log('ICE gathering complete for:', remoteUserId)
      }
    }
    
    // 监听远程流
    pc.ontrack = (event) => {
      console.log('Received remote track from:', remoteUserId, 'kind:', event.track.kind, 'streams:', event.streams.length)
      if (event.streams && event.streams[0] && this.onRemoteStream) {
        console.log('Calling onRemoteStream callback for:', remoteUserId)
        this.onRemoteStream(remoteUserId, event.streams[0])
      }
    }
    
    // 监听连接状态
    pc.onconnectionstatechange = () => {
      console.log(`PeerConnection state with ${remoteUserId}:`, pc.connectionState)
      if (pc.connectionState === 'connected') {
        console.log(`Successfully connected to ${remoteUserId}`)
      } else if (pc.connectionState === 'failed') {
        console.error(`Connection with ${remoteUserId} failed, attempting to restart ICE`)
        pc.restartIce()
      } else if (pc.connectionState === 'disconnected') {
        console.warn(`Connection with ${remoteUserId} disconnected`)
      }
    }
    
    pc.oniceconnectionstatechange = () => {
      console.log(`ICE connection state with ${remoteUserId}:`, pc.iceConnectionState)
      if (pc.iceConnectionState === 'failed') {
        console.error(`ICE connection failed with ${remoteUserId}`)
      }
    }
    
    pc.onicegatheringstatechange = () => {
      console.log(`ICE gathering state with ${remoteUserId}:`, pc.iceGatheringState)
    }
    
    pc.onsignalingstatechange = () => {
      console.log(`Signaling state with ${remoteUserId}:`, pc.signalingState)
    }
    
    return pc
  }

  /**
   * 向指定用户发起连接（作为 Offer 方）
   */
  async initiateConnection(remoteUserId: string): Promise<void> {
    console.log('Initiating connection to:', remoteUserId)
    
    const pc = this.createConnection(remoteUserId)
    
    try {
      const offer = await pc.createOffer()
      await pc.setLocalDescription(offer)
      
      console.log('Sending offer to:', remoteUserId)
      wsService.send({
        messageSendToType: MessageSendToType.USER,
        meetingId: this.meetingId,
        messageType: MessageType.WEBRTC_OFFER,
        sendUserId: this.userId,
        receiveUserId: remoteUserId,
        messageContent: offer
      })
    } catch (error) {
      console.error('Failed to create offer:', error)
    }
  }

  /**
   * 处理收到的 Offer
   */
  private async handleOffer(message: WebSocketMessage): Promise<void> {
    const remoteUserId = message.sendUserId!
    console.log('Received offer from:', remoteUserId)
    
    const pc = this.createConnection(remoteUserId)
    
    try {
      await pc.setRemoteDescription(new RTCSessionDescription(message.messageContent))
      
      const answer = await pc.createAnswer()
      await pc.setLocalDescription(answer)
      
      console.log('Sending answer to:', remoteUserId)
      wsService.send({
        messageSendToType: MessageSendToType.USER,
        meetingId: this.meetingId,
        messageType: MessageType.WEBRTC_ANSWER,
        sendUserId: this.userId,
        receiveUserId: remoteUserId,
        messageContent: answer
      })
    } catch (error) {
      console.error('Failed to handle offer:', error)
    }
  }

  /**
   * 处理收到的 Answer
   */
  private async handleAnswer(message: WebSocketMessage): Promise<void> {
    const remoteUserId = message.sendUserId!
    console.log('Received answer from:', remoteUserId)
    
    const pc = this.peerConnections.get(remoteUserId)
    if (pc) {
      try {
        await pc.setRemoteDescription(new RTCSessionDescription(message.messageContent))
      } catch (error) {
        console.error('Failed to set remote description:', error)
      }
    }
  }

  /**
   * 处理收到的 ICE Candidate
   */
  private async handleIceCandidate(message: WebSocketMessage): Promise<void> {
    const remoteUserId = message.sendUserId!
    console.log('Received ICE candidate from:', remoteUserId)
    
    const pc = this.peerConnections.get(remoteUserId)
    if (pc) {
      try {
        await pc.addIceCandidate(new RTCIceCandidate(message.messageContent))
      } catch (error) {
        console.error('Failed to add ICE candidate:', error)
      }
    }
  }

  /**
   * 关闭与指定用户的连接
   */
  closeConnection(userId: string): void {
    const pc = this.peerConnections.get(userId)
    if (pc) {
      pc.close()
      this.peerConnections.delete(userId)
      console.log('Closed connection with:', userId)
      
      if (this.onStreamRemoved) {
        this.onStreamRemoved(userId)
      }
    }
  }

  /**
   * 关闭所有 PeerConnection
   */
  closeAllConnections(): void {
    console.log('Closing all peer connections, count:', this.peerConnections.size)
    this.peerConnections.forEach((pc, userId) => {
      pc.close()
      if (this.onStreamRemoved) {
        this.onStreamRemoved(userId)
      }
    })
    this.peerConnections.clear()
  }

  /**
   * 停止本地媒体流
   */
  stopLocalMedia(): void {
    if (this.localStream) {
      console.log('Stopping local media tracks')
      this.localStream.getTracks().forEach(track => {
        track.stop()
      })
      this.localStream = null
    }
  }

  /**
   * 切换视频轨道状态
   */
  toggleVideo(enabled: boolean): void {
    if (this.localStream) {
      this.localStream.getVideoTracks().forEach(track => {
        track.enabled = enabled
      })
    }
  }

  /**
   * 切换音频轨道状态
   */
  toggleAudio(enabled: boolean): void {
    if (this.localStream) {
      this.localStream.getAudioTracks().forEach(track => {
        track.enabled = enabled
      })
    }
  }

  /**
   * 完整清理（关闭所有连接 + 停止本地媒体）
   */
  cleanup(): void {
    console.log('WebRTCManager cleanup')
    this.closeAllConnections()
    this.stopLocalMedia()
    
    // 移除 WebSocket 消息处理器
    wsService.off(MessageType.WEBRTC_OFFER, this.handleOffer.bind(this))
    wsService.off(MessageType.WEBRTC_ANSWER, this.handleAnswer.bind(this))
    wsService.off(MessageType.WEBRTC_ICE_CANDIDATE, this.handleIceCandidate.bind(this))
  }

  /**
   * 检查是否有与指定用户的连接
   */
  hasConnection(userId: string): boolean {
    return this.peerConnections.has(userId)
  }

  /**
   * 获取当前连接数
   */
  getConnectionCount(): number {
    return this.peerConnections.size
  }
}

// 导出单例
export const webRTCManager = new WebRTCManager()
