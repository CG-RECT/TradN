package com.tradn.common.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
/** 前后端统一响应结构。 */
public class ApiResponse<T> {
    /** 业务状态码，0 表示成功。 */
    private int code;

    /** 面向调用方的结果说明。 */
    private String message;

    /** 接口返回的业务数据。 */
    private T data;

    /** 当前请求标识，用于关联访问审计和异常日志。 */
    private String requestId;

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<T>(0, "success", data, RequestIds.current());
    }

    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    public static ApiResponse<Void> error(int code, String message) {
        return new ApiResponse<Void>(code, message, null, RequestIds.current());
    }
}
