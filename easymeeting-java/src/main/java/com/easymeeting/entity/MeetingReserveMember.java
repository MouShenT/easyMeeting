package com.easymeeting.entity;

import lombok.Data;

/**
 * 预约会议成员实体
 */
@Data
public class MeetingReserveMember {

    /**
     * 会议ID
     */
    private String meetingId;

    /**
     * 被邀请用户ID
     */
    private String inviteUserId;
}
