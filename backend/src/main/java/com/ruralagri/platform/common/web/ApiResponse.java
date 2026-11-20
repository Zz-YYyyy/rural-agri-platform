package com.ruralagri.platform.common.web;

import java.util.Map;
import java.util.Objects;

public record ApiResponse<T>(String code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>("OK", "成功", Objects.requireNonNull(data), traceId);
    }

    public static ApiResponse<Map<String, Object>> emptySuccess(String traceId) {
        return success(Map.of(), traceId);
    }
}
