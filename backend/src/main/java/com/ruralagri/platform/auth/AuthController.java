package com.ruralagri.platform.auth;

import java.util.Map;

import com.ruralagri.platform.auth.AuthRequests.LoginRequest;
import com.ruralagri.platform.auth.AuthRequests.RegisterRequest;
import com.ruralagri.platform.common.web.ApiResponse;
import com.ruralagri.platform.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("dev")
public class AuthController {
    private final AuthService auth;
    private final AuthRateLimiter limits;

    public AuthController(AuthService auth, AuthRateLimiter limits) {
        this.auth = auth;
        this.limits = limits;
    }

    @PostMapping("/api/v1/auth/register")
    public ApiResponse<AccountView> register(@Valid @RequestBody RegisterRequest body,
                                             HttpServletRequest request) {
        limits.checkRegistration(request.getRemoteAddr());
        return ApiResponse.success(auth.register(body), TraceIdFilter.current(request));
    }

    @PostMapping("/api/v1/auth/login")
    public ApiResponse<AuthService.LoginResponse> login(@Valid @RequestBody LoginRequest body,
                                                        HttpServletRequest request) {
        limits.checkLogin(request.getRemoteAddr(), body.username());
        return ApiResponse.success(auth.login(body), TraceIdFilter.current(request));
    }

    @GetMapping("/api/v1/me")
    public ApiResponse<AccountView> me(@AuthenticationPrincipal AccountPrincipal principal,
                                      HttpServletRequest request) {
        return ApiResponse.success(principal.account(), TraceIdFilter.current(request));
    }

    @PostMapping("/api/v1/auth/logout")
    public ApiResponse<Map<String, Object>> logout(@AuthenticationPrincipal AccountPrincipal principal,
                                                  HttpServletRequest request) {
        auth.logout(principal.jti());
        return ApiResponse.emptySuccess(TraceIdFilter.current(request));
    }
}
