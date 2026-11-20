package com.ruralagri.platform.common.web;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public BusinessException(HttpStatus status, String code, String message) {
        super(message);
        if (!status.is4xxClientError() && !status.is5xxServerError()) {
            throw new IllegalArgumentException("Business errors require an error HTTP status");
        }
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
