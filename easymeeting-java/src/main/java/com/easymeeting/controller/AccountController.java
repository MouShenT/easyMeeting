package com.easymeeting.controller;

import com.easymeeting.dto.*;

import com.easymeeting.exception.BusinessException;
import com.easymeeting.redis.RedisComponent;
import com.easymeeting.service.UserService;
import com.easymeeting.vo.CheckCodeVo;


import com.easymeeting.vo.ResponseVO;
import com.easymeeting.vo.UserInfoVo;
import com.wf.captcha.ArithmeticCaptcha;
import jakarta.validation.Valid;

import jakarta.validation.constraints.Null;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/account")
@Validated
@Slf4j
@RequiredArgsConstructor
public class AccountController {

    private final UserService userService;
    private final RedisComponent redisComponent;

    @GetMapping("/checkCode")
    public ResponseVO<CheckCodeVo> checkCode() {
        ArithmeticCaptcha captcha = new ArithmeticCaptcha(100, 42);
        String code = captcha.text();//计算结果，不返回前端
        String checkCodeKey = redisComponent.saveCheckCode(code);
        String checkCodeBase64 = captcha.toBase64();//图片base64
        log.info("code:{}", code);
        CheckCodeVo checkCodeVo=new CheckCodeVo();
        checkCodeVo.setCheckCode(checkCodeBase64);
        checkCodeVo.setCheckCodeKey(checkCodeKey);
        return ResponseVO.success(checkCodeVo);
    }
    @PostMapping("/register")
    public ResponseVO<Null> register(@RequestBody @Valid UserRegisterDTO dto) {
        try {
            if (!dto.getCheckCode().equalsIgnoreCase(redisComponent.getCheckCode(dto.getCheckCodeKey()))) {
                throw new BusinessException("图片验证码不正确");
            }
            userService.register(dto);
            return ResponseVO.success(null);
        } finally {
            redisComponent.clearCheckCode(dto.getCheckCodeKey());
        }
    }
    @PostMapping("/login")
    public ResponseVO<UserInfoVo> login(@RequestBody @Valid UserLoginDTO dto) {
        try {
            if (!dto.getCheckCode().equalsIgnoreCase(redisComponent.getCheckCode(dto.getCheckCodeKey()))) {
                throw new BusinessException("图片验证码不正确");
            }
            UserInfoVo userInfoVo = userService.login(dto);
            return ResponseVO.success(userInfoVo);
        } finally {
            redisComponent.clearCheckCode(dto.getCheckCodeKey());
        }
    }

//    @PostMapping("/createUser")
//    public ResponseVO<UserInfo> createUser(@RequestBody @Valid UserCreateDTO dto) {
//        UserInfo userInfo = userService.createUser(dto);
//        return ResponseVO.success(userInfo);
//    }
//
//    @PostMapping("/updateUserInfo")
//    public ResponseVO<UserInfo> updateUserInfo(@RequestBody @Valid UserUpdateDTO dto) {
//        UserInfo userInfo = userService.updateUserInfo(dto);
//        return ResponseVO.success(userInfo);
//    }
//
//    @PostMapping("/updatePassword")
//    public ResponseVO<Void> updatePassword(@RequestBody @Valid PasswordUpdateDTO dto) {
//        userService.updatePassword(dto);
//        return ResponseVO.success();
//    }
//
//    @GetMapping("/getUserById/{userId}")
//    public ResponseVO<UserInfo> getUserById(@PathVariable String userId) {
//        UserInfo userInfo = userService.getUserById(userId);
//        return ResponseVO.success(userInfo);
//    }
//
//    @GetMapping("/getUserByEmail")
//    public ResponseVO<UserInfo> getUserByEmail(@RequestParam String email) {
//        UserInfo userInfo = userService.getUserByEmail(email);
//        return ResponseVO.success(userInfo);
//    }
//
//    @GetMapping("/listUsers")
//    public ResponseVO<PageResult<UserInfo>> listUsers(UserQueryDTO dto) {
//        PageResult<UserInfo> result = userService.listUsers(dto);
//        return ResponseVO.success(result);
//    }
//
//    @DeleteMapping("/deleteUser/{userId}")
//    public ResponseVO<Void> deleteUser(@PathVariable String userId) {
//        userService.deleteUser(userId);
//        return ResponseVO.success();
//    }
}
