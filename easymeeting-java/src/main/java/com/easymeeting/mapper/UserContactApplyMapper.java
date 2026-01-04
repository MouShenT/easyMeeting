package com.easymeeting.mapper;

import com.easymeeting.entity.UserContactApply;
import com.easymeeting.vo.UserContactApplyVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserContactApplyMapper {

    int insert(UserContactApply userContactApply);

    /**
     * 插入或更新（利用唯一索引），重复申请时更新状态
     */
    int insertOrUpdate(UserContactApply userContactApply);

    int updateById(UserContactApply userContactApply);

    int deleteById(@Param("applyId") Integer applyId);

    UserContactApply selectById(@Param("applyId") Integer applyId);

    List<UserContactApply> selectAll();

    /**
     * 根据申请用户ID查询
     */
    List<UserContactApply> selectByApplyUserId(@Param("applyUserId") String applyUserId);

    /**
     * 根据接收用户ID查询
     */
    List<UserContactApply> selectByReceiveUserId(@Param("receiveUserId") String receiveUserId);

    /**
     * 根据状态查询
     */
    List<UserContactApply> selectByStatus(@Param("status") Integer status);

    /**
     * 根据申请用户ID和接收用户ID查询（唯一索引）
     */
    UserContactApply selectByApplyUserIdAndReceiveUserId(@Param("applyUserId") String applyUserId, 
                                                          @Param("receiveUserId") String receiveUserId);

    /**
     * 更新申请状态
     */
    int updateStatus(@Param("applyId") Integer applyId, @Param("status") Integer status);

    /**
     * 根据唯一索引更新状态
     */
    int updateStatusByApplyUserIdAndReceiveUserId(@Param("applyUserId") String applyUserId, 
                                                   @Param("receiveUserId") String receiveUserId, 
                                                   @Param("status") Integer status);

    /**
     * 查询用户收到的待处理申请
     */
    List<UserContactApply> selectPendingByReceiveUserId(@Param("receiveUserId") String receiveUserId);

    /**
     * 查询用户发出的申请
     */
    List<UserContactApply> selectSentByApplyUserId(@Param("applyUserId") String applyUserId);

    /**
     * 查询用户收到的申请（关联申请人昵称）
     */
    List<UserContactApplyVo> selectReceivedAppliesWithNickName(@Param("receiveUserId") String receiveUserId);
}
