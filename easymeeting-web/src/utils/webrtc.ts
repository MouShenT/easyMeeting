import { wsService, MessageType, MessageSendToType } from './websocket'
import type { WebSocketMessage } from './websocket'

// WebRTC 配置 - 优化连接质量和速度
const rtcConfig: RTCConfiguration = {
  iceServers: [
    { urls: 'stun:stun.l.google.com:19302' },
    { urls: 'stun:stun1.l.google.com:19302' },
    { urls: 'stun:stun2.l.google.com:19302' },
    { urls: 'stun:stun3.l.google.com:19302' }
  ],
  // 优化 ICE 传输策略
  iceCandidatePoolSize: 10,
  bundlePolicy: 'max-bundle',
  rtcpMuxPolicy: 'require'
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
  private initialized: boolean = false
  
  // 回调函数
  private onRemoteStream: StreamCallback | null = null
  private onStreamRemoved: StreamRemovedCallback | null = null
  
  // 绑定的事件处理器（保存引用以便正确移除）
  private boundHandleOffer: (message: WebSocketMessage) => Promise<void>
  private boundHandleAnswer: (message: WebSocketMessage) => Promise<void>
  private boundHandleIceCandidate: (message: WebSocketMessage) => Promise<void>

  constructor() {
    // 预先绑定事件处理器，保存引用
    this.boundHandleOffer = this.handleOffer.bind(this)
    this.boundHandleAnswer = this.handleAnswer.bind(this)
    this.boundHandleIceCandidate = this.handleIceCandidate.bind(this)
  }

  /**
   * 初始化管理器
   */
  init(meetingId: string, userId: string, onRemoteStream: StreamCallback, onStreamRemoved: StreamRemovedCallback) {
    // 如果已经初始化过，先清理
    if (this.initialized) {
      this.cleanup()
    }
    
    this.meetingId = meetingId
    this.userId = userId
    this.onRemoteStream = onRemoteStream
    this.onStreamRemoved = onStreamRemoved
    this.initialized = true
    
    // 注册 WebSocket 消息处理器
    wsService.on(MessageType.WEBRTC_OFFER, this.boundHandleOffer)
    wsService.on(MessageType.WEBRTC_ANSWER, this.boundHandleAnswer)
    wsService.on(MessageType.WEBRTC_ICE_CANDIDATE, this.boundHandleIceCandidate)
    
    console.log('WebRTCManager initialized for meeting:', meetingId, 'user:', userId)
  }

  /**
   * 初始化本地媒体流
   * 优化：支持多浏览器共享摄像头，提高视频流畅度
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
      
      // 优化的媒体约束配置
      // 1. 使用较低分辨率和帧率以支持多浏览器共享
      // 2. 不使用 exact 约束，让浏览器灵活选择
      const constraints: MediaStreamConstraints = {
        video: videoEnabled ? {
          width: { min: 320, ideal: 640, max: 1280 },
          height: { min: 240, ideal: 480, max: 720 },
          frameRate: { min: 10, ideal: 24, max: 30 },
          // 不指定 facingMode 为 exact，允许任何摄像头
          facingMode: 'user'
        } : false,
        audio: audioEnabled ? {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
          // 降低音频采样率以减少带宽
          sampleRate: { ideal: 44100 },
          channelCount: { ideal: 1 }
        } : false
      }
      
      console.log('Requesting media with constraints:', JSON.stringify(constraints))
      this.localStream = await navigator.mediaDevices.getUserMedia(constraints)
      console.log('Got local stream with tracks:', this.localStream.getTracks().map(t => `${t.kind}:${t.label}`))
      return this.localStream
    } catch (error: any) {
      console.error('Failed to get local media:', error)
      
      // 如果视频获取失败，尝试降级策略
      if (videoEnabled) {
        // 策略1: 尝试更低的分辨率
        if (error.name === 'OverconstrainedError' || error.name === 'NotReadableError') {
          console.log('Trying lower resolution...')
          try {
            this.localStream = await navigator.mediaDevices.getUserMedia({
              video: {
                width: { ideal: 320 },
                height: { ideal: 240 },
                frameRate: { ideal: 15 }
              },
              audio: audioEnabled ? {
                echoCancellation: true,
                noiseSuppression: true
              } : false
            })
            console.log('Got low-res stream')
            return this.localStream
          } catch (lowResError) {
            console.error('Low-res also failed:', lowResError)
          }
        }
        
        // 策略2: 只获取音频
        console.log('Video failed, trying audio only...')
        try {
          this.localStream = await navigator.mediaDevices.getUserMedia({
            video: false,
            audio: audioEnabled ? {
              echoCancellation: true,
              noiseSuppression: true,
              autoGainControl: true
            } : false
          })
          console.log('Got audio-only stream')
          return this.localStream
        } catch (audioError) {
          console.error('Failed to get audio-only stream:', audioError)
        }
      }
      
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
   * 优化：添加带宽控制和编码参数
   */
  createConnection(remoteUserId: string): RTCPeerConnection {
    console.log('Creating PeerConnection for:', remoteUserId, 'hasLocalStream:', !!this.localStream)
    
    // 如果已存在连接，先关闭
    if (this.peerConnections.has(remoteUserId)) {
      console.log('Closing existing connection with:', remoteUserId)
      this.closeConnection(remoteUserId)
    }
    
    const pc = new RTCPeerConnection(rtcConfig)
    this.peerConnections.set(remoteUserId, pc)
    
    // 添加本地流的所有轨道
    if (this.localStream) {
      const tracks = this.localStream.getTracks()
      console.log('Adding local tracks to PeerConnection, tracks:', tracks.length)
      tracks.forEach(track => {
        const sender = pc.addTrack(track, this.localStream!)
        console.log('Added track:', track.kind, 'enabled:', track.enabled, 'readyState:', track.readyState)
        
        // 优化视频编码参数
        if (track.kind === 'video' && sender) {
          this.optimizeVideoEncoding(sender)
        }
      })
    } else {
      console.warn('No local stream available when creating connection - will only receive remote video')
      // 即使没有本地流，也需要添加 transceiver 来接收远程流
      // 这确保了即使我们没有发送视频，也能接收对方的视频
      pc.addTransceiver('video', { direction: 'recvonly' })
      pc.addTransceiver('audio', { direction: 'recvonly' })
      console.log('Added recvonly transceivers for video and audio')
    }
    
    // 监听 ICE Candidate
    pc.onicecandidate = (event) => {
      if (event.candidate) {
        // 只发送有效的候选者，减少不必要的信令
        wsService.send({
          messageSendToType: MessageSendToType.USER,
          meetingId: this.meetingId,
          messageType: MessageType.WEBRTC_ICE_CANDIDATE,
          sendUserId: this.userId,
          receiveUserId: remoteUserId,
          messageContent: event.candidate
        })
      }
    }
    
    // 监听远程流
    pc.ontrack = (event) => {
      console.log('=== Received remote track ===')
      console.log('From:', remoteUserId)
      console.log('Track kind:', event.track.kind)
      console.log('Track enabled:', event.track.enabled)
      console.log('Track readyState:', event.track.readyState)
      console.log('Streams count:', event.streams.length)
      
      if (event.streams && event.streams[0]) {
        const stream = event.streams[0]
        console.log('Stream id:', stream.id)
        console.log('Stream tracks:', stream.getTracks().map(t => `${t.kind}:${t.enabled}`))
        
        if (this.onRemoteStream) {
          console.log('Calling onRemoteStream callback for:', remoteUserId)
          this.onRemoteStream(remoteUserId, stream)
        } else {
          console.error('onRemoteStream callback is not set!')
        }
      } else {
        console.warn('No stream in track event')
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
    
    return pc
  }

  /**
   * 优化视频编码参数
   */
  private async optimizeVideoEncoding(sender: RTCRtpSender): Promise<void> {
    try {
      const params = sender.getParameters()
      if (!params.encodings || params.encodings.length === 0) {
        params.encodings = [{}]
      }
      
      // 设置最大比特率和帧率，提高流畅度
      params.encodings[0].maxBitrate = 500000 // 500 kbps
      params.encodings[0].maxFramerate = 24
      // 优先保证流畅度而非清晰度
      params.encodings[0].priority = 'high'
      params.encodings[0].networkPriority = 'high'
      
      await sender.setParameters(params)
      console.log('Video encoding optimized')
    } catch (error) {
      console.warn('Failed to optimize video encoding:', error)
    }
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
    
    // 使用保存的引用移除 WebSocket 消息处理器
    if (this.initialized) {
      wsService.off(MessageType.WEBRTC_OFFER, this.boundHandleOffer)
      wsService.off(MessageType.WEBRTC_ANSWER, this.boundHandleAnswer)
      wsService.off(MessageType.WEBRTC_ICE_CANDIDATE, this.boundHandleIceCandidate)
      this.initialized = false
    }
    
    // 重置状态
    this.meetingId = ''
    this.userId = ''
    this.onRemoteStream = null
    this.onStreamRemoved = null
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
