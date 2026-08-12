package com.example.ioedunew.service;

import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.config.JwtUtil;
import com.example.ioedunew.dto.AuthDtos;
import com.example.ioedunew.entity.SkillScore;
import com.example.ioedunew.entity.User;
import com.example.ioedunew.repository.SkillScoreRepository;
import com.example.ioedunew.repository.UserRepository;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * 认证服务:注册、登录、当前用户信息。
 * 注册副作用:初始化 6 个技能维度基线分并发送欢迎通知。
 */
@Service
public class AuthService {

    /** 技能评估的固定维度 */
    public static final List<String> SKILL_DIMENSIONS = Arrays.asList(
            "嵌入式开发", "编程能力", "通信技术", "PCB设计", "信号处理", "硬件调试");

    private final UserRepository userRepository;
    private final SkillScoreRepository skillScoreRepository;
    private final NotificationService notificationService;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository,
                       SkillScoreRepository skillScoreRepository,
                       NotificationService notificationService,
                       JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.skillScoreRepository = skillScoreRepository;
        this.notificationService = notificationService;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new BusinessException("该邮箱已注册");
        }
        User user = new User();
        user.setName(req.getName());
        user.setEmail(req.getEmail());
        user.setPasswordHash(BCrypt.hashpw(req.getPassword(), BCrypt.gensalt()));
        user.setStudentNo(req.getStudentNo());
        user.setMajor(req.getMajor());
        user.setGrade(req.getGrade());
        user.setRole("STUDENT");
        userRepository.save(user);

        for (String dim : SKILL_DIMENSIONS) {
            SkillScore s = new SkillScore();
            s.setUserId(user.getId());
            s.setSkillName(dim);
            s.setScore(30);
            skillScoreRepository.save(s);
        }
        notificationService.create(user.getId(), "system", "欢迎加入AI未来实践中心",
                "完成一次能力测评,开启你的项目驱动学习之旅吧!");
        return new AuthDtos.AuthResponse(jwtUtil.createToken(user.getId(), user.getRole()), user);
    }

    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new BusinessException("邮箱或密码错误"));
        if (!BCrypt.checkpw(req.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("邮箱或密码错误");
        }
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new BusinessException(403, "账号已被禁用,请联系管理员");
        }
        return new AuthDtos.AuthResponse(jwtUtil.createToken(user.getId(), user.getRole()), user);
    }

    public User me(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(401, "用户不存在"));
    }

    @Transactional
    public User updateProfile(Long userId, AuthDtos.ProfileUpdateRequest req) {
        User user = me(userId);
        user.setName(req.getName());
        if (req.getMajor() != null) {
            user.setMajor(req.getMajor());
        }
        if (req.getGrade() != null) {
            user.setGrade(req.getGrade());
        }
        user.setAvatarUrl(req.getAvatarUrl());
        return userRepository.save(user);
    }

    @Transactional
    public void changePassword(Long userId, AuthDtos.PasswordChangeRequest req) {
        User user = me(userId);
        if (!BCrypt.checkpw(req.getOldPassword(), user.getPasswordHash())) {
            throw new BusinessException("原密码不正确");
        }
        user.setPasswordHash(BCrypt.hashpw(req.getNewPassword(), BCrypt.gensalt()));
        userRepository.save(user);
    }
}
