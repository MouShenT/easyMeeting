package com.easymeeting.controller;

import com.easymeeting.dto.TokenUserInfoDto;
import com.easymeeting.entity.UserContact;
import com.easymeeting.entity.UserContactApply;
import com.easymeeting.enums.UserContactStatusEnum;
import com.easymeeting.interceptor.TokenInterceptor;
import com.easymeeting.service.UserContactApplyService;
import com.easymeeting.service.UserContactService;
import com.easymeeting.vo.ResponseVO;
import com.easymeeting.vo.UserContactApplyVo;
import com.easymeeting.vo.UserContactVo;
import com.easymeeting.vo.UserInfoVoForSearch;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/userContact")
@Validated
@Slf4j
@RequiredArgsConstructor
public class UserContactController {
    private final UserContactService userContactService;
    private final UserContactApplyService userContactApplyService;
    @GetMapping("/searchContact")
    public ResponseVO<UserInfoVoForSearch> searchContact(@NotEmpty String searchUserId, HttpServletRequest request) {
        TokenUserInfoDto tokenUserInfoDto = (TokenUserInfoDto) request.getAttribute(TokenInterceptor.CURRENT_USER);
        UserInfoVoForSearch userInfoVoForSearch = userContactService.searchContact(tokenUserInfoDto.getUserId(), searchUserId);
        return ResponseVO.success(userInfoVoForSearch);

    }
    @RequestMapping("/contactApply")
    public ResponseVO contactApply(@NotEmpty String receiverUserId, HttpServletRequest request) {
        TokenUserInfoDto tokenUserInfoDto = (TokenUserInfoDto) request.getAttribute(TokenInterceptor.CURRENT_USER);
        UserContactApply userContactApply=new UserContactApply();
        userContactApply.setApplyUserId(tokenUserInfoDto.getUserId());
        userContactApply.setReceiveUserId(receiverUserId);
        Integer status=userContactApplyService.saveUserContactApply(userContactApply);
        return ResponseVO.success(status);

    }
    @RequestMapping("/dealWithApply")
    public ResponseVO dealWithApply(@NotEmpty String applyUserId, @NotNull Integer status, HttpServletRequest request) {
        TokenUserInfoDto tokenUserInfoDto = (TokenUserInfoDto) request.getAttribute(TokenInterceptor.CURRENT_USER);
        userContactApplyService.dealWithApply(applyUserId,tokenUserInfoDto.getUserId(),tokenUserInfoDto.getNickName(),status);
        return ResponseVO.success(null);
    }
    /**
     * 获取联系人列表（包含昵称）
     */
    @GetMapping("/loadContactUser")
    public ResponseVO loadContactUser(HttpServletRequest request) {
        TokenUserInfoDto tokenUserInfoDto = (TokenUserInfoDto) request.getAttribute(TokenInterceptor.CURRENT_USER);
        List<UserContactVo> contacts = userContactService.getNormalContactsWithNickName(tokenUserInfoDto.getUserId());
        return ResponseVO.success(contacts);
    }

    /**
     * 获取收到的好友申请列表（包含申请人昵称）
     */
    @GetMapping("/loadContactApply")
    public ResponseVO loadContactApply(HttpServletRequest request) {
        TokenUserInfoDto tokenUserInfoDto = (TokenUserInfoDto) request.getAttribute(TokenInterceptor.CURRENT_USER);
        List<UserContactApplyVo> applies = userContactApplyService.getReceivedAppliesWithNickName(tokenUserInfoDto.getUserId());
        return ResponseVO.success(applies);
    }
    @RequestMapping("/operateContact")
    public ResponseVO operateContact(@NotEmpty String contactId, @NotNull Integer status, HttpServletRequest request) {
        TokenUserInfoDto tokenUserInfoDto = (TokenUserInfoDto) request.getAttribute(TokenInterceptor.CURRENT_USER);
        String userId = tokenUserInfoDto.getUserId();
        // 1. 校验 status 是否为有效枚举值
        UserContactStatusEnum statusEnum = UserContactStatusEnum.getUserContactStatusEnum(status);
        if (statusEnum == null) {
            return ResponseVO.error("无效的状态值");
        }
        // 2. 只允许删除(1)或拉黑(2)操作，不允许通过此接口恢复正常(0)
        if (UserContactStatusEnum.NORMAL.equals(statusEnum)) {
            return ResponseVO.error("不支持的操作");
        }
        // 3. 校验联系人是否存在
        UserContact contact = userContactService.getByUserIdAndContactId(userId, contactId);
        if (contact == null) {
            return ResponseVO.error("联系人不存在");
        }
        // 4. 更新状态
        userContactService.updateStatus(userId, contactId, statusEnum.getStatus());
        return ResponseVO.success(null);
    }

}
