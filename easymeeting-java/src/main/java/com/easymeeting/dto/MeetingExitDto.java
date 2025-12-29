package com.easymeeting.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class MeetingExitDto implements Serializable {
    public String exitUserId;
    private List<MeetingMemberDto> meetingMemberList;
    private Integer exitStatus;
}
