package com.easymeeting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserUpdateDTO {

    @NotBlank(message = "用户ID不能为空")
    private String userId;

    @Size(max = 20, message = "昵称最长20个字符")
    private String nickName;

    private Integer sex;
}
