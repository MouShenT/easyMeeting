package com.easymeeting.mapper;

import com.easymeeting.dto.UserQueryDTO;
import com.easymeeting.entity.UserInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface UserMapper {

    int insert(UserInfo userInfo);

    int updateById(UserInfo userInfo);

    int updatePassword(@Param("userId") String userId, @Param("password") String password);
    
    int updateLastLoginTime(@Param("userId") String userId, @Param("lastLoginTime") Long lastLoginTime);

    int updateLastOffTime(@Param("userId") String userId, @Param("lastOffTime") Long lastOffTime);

    UserInfo selectById(@Param("userId") String userId);

    UserInfo selectByEmail(@Param("email") String email);

    List<UserInfo> selectList(UserQueryDTO dto);

    int selectCount(UserQueryDTO dto);

    int deleteById(@Param("userId") String userId);
}
