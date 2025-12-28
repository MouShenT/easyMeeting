package com.easymeeting.vo;

import lombok.Data;
import java.util.List;

@Data
public class PageResult<T> {
    
    private Integer pageNo;
    private Integer pageSize;
    private Long total;
    private List<T> list;

    public PageResult() {
    }

    public PageResult(Integer pageNo, Integer pageSize, Long total, List<T> list) {
        this.pageNo = pageNo;
        this.pageSize = pageSize;
        this.total = total;
        this.list = list;
    }

    public static <T> PageResult<T> of(Integer pageNo, Integer pageSize, Long total, List<T> list) {
        return new PageResult<>(pageNo, pageSize, total, list);
    }
}
