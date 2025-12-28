package com.easymeeting.dto;

import lombok.Data;

@Data
public class UserQueryDTO {

    private Integer pageNo = 1;
    private Integer pageSize = 10;
    private String nickName;
    private Integer status;

    public Integer getOffset() {
        return (pageNo - 1) * pageSize;
    }
}
