package com.example.ioedunew.controller;

import com.example.ioedunew.common.ApiResponse;
import com.example.ioedunew.config.AuthUser;
import com.example.ioedunew.dto.AuthDtos;
import com.example.ioedunew.entity.User;
import com.example.ioedunew.service.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

/**
 * 认证接口:注册 / 登录 / 当前用户。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthDtos.AuthResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest req) {
        return ApiResponse.ok(authService.register(req));
    }

    @PostMapping("/login")
    public ApiResponse<AuthDtos.AuthResponse> login(@Valid @RequestBody AuthDtos.LoginRequest req) {
        return ApiResponse.ok(authService.login(req));
    }

    /**
     * 当前登录用户信息。注意:/api/auth 在拦截器白名单里,这里手动校验令牌属性。
     */
    @GetMapping("/me")
    public ApiResponse<User> me(HttpServletRequest request) {
        AuthUser auth = requireAuth(request);
        return ApiResponse.ok(authService.me(auth.getId()));
    }

    @PutMapping("/profile")
    public ApiResponse<User> updateProfile(@Valid @RequestBody AuthDtos.ProfileUpdateRequest req,
                                           HttpServletRequest request) {
        AuthUser auth = requireAuth(request);
        return ApiResponse.ok(authService.updateProfile(auth.getId(), req));
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody AuthDtos.PasswordChangeRequest req,
                                            HttpServletRequest request) {
        AuthUser auth = requireAuth(request);
        authService.changePassword(auth.getId(), req);
        return ApiResponse.ok();
    }

    private AuthUser requireAuth(HttpServletRequest request) {
        AuthUser auth = (AuthUser) request.getAttribute(AuthUser.REQUEST_ATTR);
        if (auth == null) {
            throw new com.example.ioedunew.common.BusinessException(401, "未登录");
        }
        return auth;
    }
}
