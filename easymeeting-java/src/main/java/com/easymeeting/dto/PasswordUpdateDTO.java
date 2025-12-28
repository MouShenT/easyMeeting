package com.easymeeting.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PasswordUpdateDTO {

    @NotBlank(message = "用户ID不能为空")
    private String userId;

    @NotBlank(message = "新密码不能为空")
    private String newPassword;
}
