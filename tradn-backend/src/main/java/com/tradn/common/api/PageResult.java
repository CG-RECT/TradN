package com.tradn.common.api;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
/** 统一的服务端分页结果，避免不同模块重复定义分页字段。 */
public class PageResult<T> {
    /** 当前页数据。 */
    private List<T> records;

    /** 符合查询条件的总记录数。 */
    private long total;

    /** 当前页码，从 1 开始。 */
    private int current;

    /** 每页记录数。 */
    private int size;
}
