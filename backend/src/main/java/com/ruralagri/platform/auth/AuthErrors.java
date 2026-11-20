package com.ruralagri.platform.auth;

import com.ruralagri.platform.common.web.BusinessException;
import org.springframework.http.HttpStatus;

public final class AuthErrors {
    private AuthErrors() {
    }

    public static BusinessException unauthenticated() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "登录已失效，请重新登录");
    }

    public static BusinessException dependencyUnavailable() {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE",
                "依赖服务暂不可用，请稍后重试");
    }

    public static BusinessException accountDisabled() {
        return new BusinessException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "账号已停用");
    }
}
