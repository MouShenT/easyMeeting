package com.easymeeting.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MeetingMember {

    /**
     * 会议ID
     */
    private String meetingId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 昵称
     */
    private String nickName;

    /**
     * 最后加入时间
     */
    private LocalDateTime lastJoinTime;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 成员类型
     */
    private Integer memberType;

    /**
     * 会议状态
     */
    private Integer meetingStatus;
}
