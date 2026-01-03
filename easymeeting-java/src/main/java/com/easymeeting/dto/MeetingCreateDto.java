package com.easymeeting.dto;


import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MeetingCreateDto {
    @NotNull
    Integer meetingNoType;

    @NotEmpty
    @Size(min = 1, max = 100)
    String meetingName;

    @NotNull
    Integer joinType;

    @Size(max = 5, message = "会议密码最多5位")
    String joinPassword;



}
