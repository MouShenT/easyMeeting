package com.easymeeting.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserRegisterDTO {

    @NotEmpty(message = "验证码Key不能为空")
    private String checkCodeKey;

    @NotEmpty(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotEmpty(message = "密码不能为空")
    @Size(max = 20, message = "密码最长20个字符")
    private String password;

    @NotEmpty(message = "昵称不能为空")
    @Size(max = 20, message = "昵称最长20个字符")
    private String nickName;

    @NotEmpty(message = "验证码不能为空")
    private String checkCode;
}
