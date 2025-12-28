package com.easymeeting.service;

import com.easymeeting.dto.*;
import com.easymeeting.entity.UserInfo;
import com.easymeeting.vo.PageResult;
import com.easymeeting.vo.UserInfoVo;
import jakarta.validation.Valid;

public interface UserService {

    UserInfo createUser(UserCreateDTO dto);

    UserInfo updateUserInfo(UserUpdateDTO dto);

    void updatePassword(PasswordUpdateDTO dto);

    UserInfo getUserById(String userId);

    UserInfo getUserByEmail(String email);

    PageResult<UserInfo> listUsers(UserQueryDTO dto);

    void deleteUser(String userId);

    void register(UserRegisterDTO dto);

    UserInfoVo login(@Valid UserLoginDTO dto);
}
