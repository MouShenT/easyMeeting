package com.easymeeting.service.impl;

import com.easymeeting.dto.*;
import com.easymeeting.entity.UserInfo;
import com.easymeeting.entity.config.AppConfig;
import com.easymeeting.enums.UserStatusEnum;
import com.easymeeting.exception.BusinessException;
import com.easymeeting.mapper.UserMapper;
import com.easymeeting.redis.RedisComponent;
import com.easymeeting.service.UserService;
import com.easymeeting.utils.JwtUtils;
import com.easymeeting.utils.MD5Utils;
import com.easymeeting.utils.StringUtils;
import com.easymeeting.vo.PageResult;
import com.easymeeting.vo.UserInfoVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final RedisComponent redisComponent;
    private final AppConfig appConfig;

    @Override
    public UserInfo createUser(UserCreateDTO dto) {
        // 检查邮箱是否已存在
        UserInfo existUser = userMapper.selectByEmail(dto.getEmail());
        if (existUser != null) {
            throw new BusinessException("邮箱已存在");
        }

        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(StringUtils.generateUserId());
        userInfo.setEmail(dto.getEmail());
        userInfo.setNickName(dto.getNickName());
        userInfo.setSex(dto.getSex());
        userInfo.setPassword(MD5Utils.encrypt(dto.getPassword()));
        userInfo.setStatus(UserStatusEnum.ENABLE.getStatus());
        userInfo.setCreateTime(LocalDateTime.now());
        userInfo.setMeetingNo(StringUtils.generateMeetingNo());

        userMapper.insert(userInfo);
        userInfo.setPassword(null);
        return userInfo;
    }

    @Override
    public UserInfo updateUserInfo(UserUpdateDTO dto) {
        UserInfo existUser = userMapper.selectById(dto.getUserId());
        if (existUser == null) {
            throw new BusinessException(404, "用户不存在");
        }

        UserInfo updateUser = new UserInfo();
        updateUser.setUserId(dto.getUserId());
        updateUser.setNickName(dto.getNickName());
        updateUser.setSex(dto.getSex());

        userMapper.updateById(updateUser);

        UserInfo result = userMapper.selectById(dto.getUserId());
        result.setPassword(null);
        return result;
    }

    @Override
    public void updatePassword(PasswordUpdateDTO dto) {
        UserInfo existUser = userMapper.selectById(dto.getUserId());
        if (existUser == null) {
            throw new BusinessException(404, "用户不存在");
        }

        String encryptedPassword = MD5Utils.encrypt(dto.getNewPassword());
        userMapper.updatePassword(dto.getUserId(), encryptedPassword);
    }

    @Override
    public UserInfo getUserById(String userId) {
        UserInfo userInfo = userMapper.selectById(userId);
        if (userInfo == null) {
            throw new BusinessException(404, "用户不存在");
        }
        userInfo.setPassword(null);
        return userInfo;
    }

    @Override
    public UserInfo getUserByEmail(String email) {
        UserInfo userInfo = userMapper.selectByEmail(email);
        if (userInfo == null) {
            throw new BusinessException(404, "用户不存在");
        }
        userInfo.setPassword(null);
        return userInfo;
    }

    @Override
    public PageResult<UserInfo> listUsers(UserQueryDTO dto) {
        int total = userMapper.selectCount(dto);
        List<UserInfo> list = userMapper.selectList(dto);
        list.forEach(user -> user.setPassword(null));
        return PageResult.of(dto.getPageNo(), dto.getPageSize(), (long) total, list);
    }

    @Override
    public void deleteUser(String userId) {
        UserInfo existUser = userMapper.selectById(userId);
        if (existUser == null) {
            throw new BusinessException(404, "用户不存在");
        }
        userMapper.deleteById(userId);
    }

    @Override
    public void register(UserRegisterDTO dto) {
        // 检查邮箱是否已存在
        UserInfo existUser = userMapper.selectByEmail(dto.getEmail());
        if (existUser != null) {
            throw new BusinessException("邮箱已存在");
        }

        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(StringUtils.generateUserId());
        userInfo.setEmail(dto.getEmail());
        userInfo.setNickName(dto.getNickName());
        userInfo.setPassword(MD5Utils.encrypt(dto.getPassword()));
        userInfo.setStatus(UserStatusEnum.ENABLE.getStatus());
        userInfo.setCreateTime(LocalDateTime.now());
        userInfo.setMeetingNo(StringUtils.generateMeetingNo());

        userMapper.insert(userInfo);
        userInfo.setPassword(null);

    }

    @Override
    public UserInfoVo login(UserLoginDTO dto) {
        // 1. 验证码校验
        String savedCheckCode = redisComponent.getCheckCode(dto.getCheckCodeKey());
        if (savedCheckCode == null || !savedCheckCode.equalsIgnoreCase(dto.getCheckCode())) {
            throw new BusinessException("验证码错误");
        }
        // 清除已使用的验证码
        redisComponent.clearCheckCode(dto.getCheckCodeKey());
        
        // 2. 查询用户并验证密码（前端传过来的已经是MD5加密后的密码）
        UserInfo userInfo = userMapper.selectByEmail(dto.getEmail());
        if (userInfo == null || !userInfo.getPassword().equals(dto.getPassword())) {
            throw new BusinessException("账号或者密码不正确");
        }
        
        // 3. 检查账号状态
        if (userInfo.getStatus().equals(UserStatusEnum.DISABLE.getStatus())) {
            throw new BusinessException("账号已被禁用");
        }
        
        // 4. 单设备登录：新登录会自动踢掉旧登录（在 RedisComponent.saveTokenUserInfo 中实现）
        // 旧设备的 token 会被删除，下次请求时拦截器会返回 401
        
        // 5. 更新最后登录时间
        long currentTime = System.currentTimeMillis();
        userMapper.updateLastLoginTime(userInfo.getUserId(), currentTime);
        
        // 6. 生成JWT Token
        String token = JwtUtils.generateToken(userInfo.getUserId());
        
        // 7. 构建TokenUserInfoDto并存入Redis（双向映射）
        TokenUserInfoDto tokenUserInfo = new TokenUserInfoDto();
        tokenUserInfo.setToken(token);
        tokenUserInfo.setUserId(userInfo.getUserId());
        tokenUserInfo.setNickName(userInfo.getNickName());
        tokenUserInfo.setSex(userInfo.getSex());
        tokenUserInfo.setAdmin(appConfig.isAdmin(userInfo.getEmail()));
        tokenUserInfo.setMeetingNo(userInfo.getMeetingNo());
        // 设置管理员标识
        redisComponent.saveTokenUserInfo(tokenUserInfo);
        
        // 8. 构建返回对象
        UserInfoVo userInfoVo = new UserInfoVo();
        userInfoVo.setUserId(userInfo.getUserId());
        userInfoVo.setNickName(userInfo.getNickName());
        userInfoVo.setSex(userInfo.getSex());
        userInfoVo.setMeetingNo(userInfo.getMeetingNo());
        userInfoVo.setToken(token);
        userInfoVo.setIsAdmin(appConfig.isAdmin(userInfo.getEmail()));
        
        return userInfoVo;
    }
}
