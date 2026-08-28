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
    private final SiteConfigService siteConfigService;

    public AuthService(UserRepository userRepository,
                       SkillScoreRepository skillScoreRepository,
                       NotificationService notificationService,
                       JwtUtil jwtUtil,
                       SiteConfigService siteConfigService) {
        this.userRepository = userRepository;
        this.skillScoreRepository = skillScoreRepository;
        this.notificationService = notificationService;
        this.jwtUtil = jwtUtil;
        this.siteConfigService = siteConfigService;
    }

    /** 校验并规整手机号:空白返回 null,非法格式抛业务异常 */
    public static String normalizePhone(String raw) {
        if (raw == null) {
            return null;
        }
        String phone = raw.trim();
        if (phone.isEmpty()) {
            return null;
        }
        if (!phone.matches("^1\\d{10}$")) {
            throw new BusinessException("手机号格式不正确");
        }
        return phone;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest req) {
        if (!siteConfigService.registerAllowed()) {
            throw new BusinessException("平台已关闭自助注册,请联系管理员开通账号");
        }
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new BusinessException("该邮箱已注册");
        }
        String phone = normalizePhone(req.getPhone());
        if (phone != null && userRepository.existsByPhone(phone)) {
            throw new BusinessException("该手机号已注册");
        }
        User user = new User();
        user.setName(req.getName());
        user.setEmail(req.getEmail());
        user.setPhone(phone);
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
        // 同一输入框兼容邮箱与手机号:含 @ 视为邮箱,否则按手机号查找
        String account = req.getEmail().trim();
        User user = (account.contains("@")
                ? userRepository.findByEmail(account)
                : userRepository.findByPhone(account))
                .orElseThrow(() -> new BusinessException("账号或密码错误"));
        if (!BCrypt.checkpw(req.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("账号或密码错误");
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
        if (req.getPhone() != null) {
            String phone = normalizePhone(req.getPhone());
            if (phone != null) {
                User samePhone = userRepository.findByPhone(phone).orElse(null);
                if (samePhone != null && !samePhone.getId().equals(userId)) {
                    throw new BusinessException("该手机号已被其他账号使用");
                }
            }
            user.setPhone(phone);
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
