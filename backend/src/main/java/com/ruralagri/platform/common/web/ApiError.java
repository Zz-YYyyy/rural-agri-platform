package com.ruralagri.platform.common.web;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ApiError(String code, String message, String traceId,
                       @JsonInclude(JsonInclude.Include.NON_EMPTY) List<FieldError> fieldErrors) {

    public static ApiError of(String code, String message, String traceId) {
        return new ApiError(code, message, traceId, List.of());
    }

    public record FieldError(String field, String message) {
    }
}
