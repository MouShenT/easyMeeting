package com.easymeeting.entity;

import com.easymeeting.enums.ChatMessageStatusEnum;
import lombok.Data;

/**
 * 聊天消息实体类
 * 对应分表 message_chat_message_XX (XX = 01-32)
 * 使用 meeting_id 作为分表键，通过 MurmurHash 算法路由到对应分表
 */
@Data
public class ChatMessage {

    /**
     * 消息ID，主键，使用雪花算法生成
     */
    private Long messageId;

    /**
     * 会议ID，分表键，10位字符串
     */
    private String meetingId;

    /**
     * 消息类型
     * 5 - 文本消息 (CHAT_TEXT_MESSAGE)
     * 6 - 媒体消息 (CHAT_MEDIA_MESSAGE)
     * @see com.easymeeting.enums.MessageTypeEnum
     */
    private Integer messageType;

    /**
     * 消息内容，最大500字符
     */
    private String messageContent;

    /**
     * 发送者用户ID
     */
    private String sendUserId;

    /**
     * 发送者昵称
     */
    private String sendUserNickName;

    /**
     * 发送时间戳（毫秒）
     */
    private Long sendTime;

    /**
     * 接收类型
     * 0 - 群发（发送给会议中所有人）
     * 1 - 私聊（发送给指定用户）
     */
    private Integer receiveType;

    /**
     * 接收者用户ID（私聊时使用）
     */
    private String receiveUserId;

    /**
     * 文件大小（字节，媒体消息时使用）
     */
    private Long fileSize;

    /**
     * 文件名（媒体消息时使用）
     */
    private String fileName;

    /**
     * 文件类型（媒体消息时使用）
     */
    private Integer fileType;

    /**
     * 文件后缀（媒体消息时使用，如 jpg、mp4）
     */
    private String fileSuffix;

    /**
     * 消息状态
     * 0 - 正在发送
     * 1 - 发送完毕
     * @see ChatMessageStatusEnum
     */
    private Integer status;
}
