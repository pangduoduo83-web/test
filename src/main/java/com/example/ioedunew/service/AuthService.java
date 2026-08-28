package com.example.ioedunew.service;

import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.config.JwtUtil;
import com.example.ioedunew.dto.AuthDtos;
import com.example.ioedunew.entity.SkillScore;
import com.example.ioedunew.entity.User;
import com.example.ioedunew.repository.BorrowRequestRepository;
import com.example.ioedunew.repository.DiscussionRepository;
import com.example.ioedunew.repository.EnrollmentRepository;
import com.example.ioedunew.repository.EquipmentFavoriteRepository;
import com.example.ioedunew.repository.FavoriteRepository;
import com.example.ioedunew.repository.NotificationRepository;
import com.example.ioedunew.repository.ProjectRepository;
import com.example.ioedunew.repository.SkillScoreRepository;
import com.example.ioedunew.repository.SubmissionRepository;
import com.example.ioedunew.repository.UserRepository;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

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
    private final BorrowRequestRepository borrowRequestRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final SubmissionRepository submissionRepository;
    private final FavoriteRepository favoriteRepository;
    private final EquipmentFavoriteRepository equipmentFavoriteRepository;
    private final DiscussionRepository discussionRepository;
    private final NotificationRepository notificationRepository;
    private final ProjectRepository projectRepository;
    private final WeChatService weChatService;

    public AuthService(UserRepository userRepository,
                       SkillScoreRepository skillScoreRepository,
                       NotificationService notificationService,
                       JwtUtil jwtUtil,
                       SiteConfigService siteConfigService,
                       BorrowRequestRepository borrowRequestRepository,
                       EnrollmentRepository enrollmentRepository,
                       SubmissionRepository submissionRepository,
                       FavoriteRepository favoriteRepository,
                       EquipmentFavoriteRepository equipmentFavoriteRepository,
                       DiscussionRepository discussionRepository,
                       NotificationRepository notificationRepository,
                       ProjectRepository projectRepository,
                       WeChatService weChatService) {
        this.userRepository = userRepository;
        this.skillScoreRepository = skillScoreRepository;
        this.notificationService = notificationService;
        this.jwtUtil = jwtUtil;
        this.siteConfigService = siteConfigService;
        this.borrowRequestRepository = borrowRequestRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.submissionRepository = submissionRepository;
        this.favoriteRepository = favoriteRepository;
        this.equipmentFavoriteRepository = equipmentFavoriteRepository;
        this.discussionRepository = discussionRepository;
        this.notificationRepository = notificationRepository;
        this.projectRepository = projectRepository;
        this.weChatService = weChatService;
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

    /**
     * 微信手机号一键登录:凭 getPhoneNumber 组件的动态令牌向微信换取手机号,
     * 手机号已有账号则直接登录,否则自动注册学生账号(邮箱占位,资料可后续完善)。
     */
    @Transactional
    public AuthDtos.AuthResponse wechatPhoneLogin(String code) {
        String phone = weChatService.phoneNumberByCode(code);
        if (phone == null) {
            throw new BusinessException("微信手机号获取失败,请重试或使用账号密码登录");
        }
        User user = userRepository.findByPhone(phone).orElse(null);
        if (user == null) {
            if (!siteConfigService.registerAllowed()) {
                throw new BusinessException("平台已关闭自助注册,请联系管理员开通账号");
            }
            user = new User();
            user.setName("用户" + phone.substring(phone.length() - 4));
            // email 列非空且唯一,一键注册先写占位邮箱,后续在编辑资料/管理端补录
            user.setEmail("wx" + phone + "@auto.ioedu.cn");
            user.setPhone(phone);
            user.setPasswordHash(BCrypt.hashpw(UUID.randomUUID().toString(), BCrypt.gensalt()));
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
                    "你已通过微信手机号快速注册,请到「我的-编辑资料」完善姓名与专业信息。");
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

    /**
     * 用户自助注销:验证密码后删除账号及全部个人数据(小程序审核要求提供注销途径)。
     * 仅在以下情况阻止:设备未归还(需先归还)、名下有指导项目(教师需先移交)、最后一个管理员。
     */
    @Transactional
    public void deleteAccount(Long userId, String password) {
        User user = me(userId);
        if (!BCrypt.checkpw(password, user.getPasswordHash())) {
            throw new BusinessException("密码不正确,无法注销");
        }
        long holding = borrowRequestRepository.countByUserIdAndStatus(userId, "APPROVED")
                + borrowRequestRepository.countByUserIdAndStatus(userId, "RETURN_REQUESTED");
        if (holding > 0) {
            throw new BusinessException(409, "你还有未归还的设备,请先归还并通过验收后再注销");
        }
        if (projectRepository.existsByMentorId(userId)) {
            throw new BusinessException(409, "你名下还有指导项目,请先联系管理员移交后再注销");
        }
        if ("ADMIN".equals(user.getRole()) && userRepository.countByRole("ADMIN") <= 1) {
            throw new BusinessException(409, "平台最后一个管理员不能注销");
        }
        // 清除全部个人数据:借阅历史、报名进度、成果、收藏、讨论、技能画像、通知
        borrowRequestRepository.deleteByUserId(userId);
        enrollmentRepository.deleteByUserId(userId);
        submissionRepository.deleteByUserId(userId);
        favoriteRepository.deleteByUserId(userId);
        equipmentFavoriteRepository.deleteByUserId(userId);
        discussionRepository.deleteByUserId(userId);
        skillScoreRepository.deleteByUserId(userId);
        notificationRepository.deleteByUserId(userId);
        userRepository.delete(user);
    }
}
