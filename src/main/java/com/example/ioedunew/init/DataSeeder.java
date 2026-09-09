package com.example.ioedunew.init;

import com.example.ioedunew.entity.Equipment;
import com.example.ioedunew.entity.Project;
import com.example.ioedunew.entity.SkillScore;
import com.example.ioedunew.entity.User;
import com.example.ioedunew.repository.EquipmentRepository;
import com.example.ioedunew.repository.ProjectRepository;
import com.example.ioedunew.repository.SkillScoreRepository;
import com.example.ioedunew.repository.UserRepository;
import com.example.ioedunew.service.AuthService;
import com.example.ioedunew.service.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.security.SecureRandom;

/**
 * 租户库初始数据(由 PlatformBootstrap 在启动时对每个租户、由 TenantProvisioningService 在开通时调用,
 * 调用方负责先绑定 TenantContext)。
 * 用户表为空时创建初始管理员:密码未给出则随机生成并以 WARN 打印一次,生产环境不再有固定默认口令。
 * 演示数据(演示师生账号、10 个开源硬件项目、12 台设备)仅在 demo=true 时写入,
 * 老库的演示数据字段回填也只在该开关打开时进行,避免把客户删掉的演示账号/设备补回来。
 */
@Slf4j
@Component
public class DataSeeder {

    private static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";

    private final UserRepository userRepository;
    private final EquipmentRepository equipmentRepository;
    private final ProjectRepository projectRepository;
    private final SkillScoreRepository skillScoreRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public DataSeeder(UserRepository userRepository,
                      EquipmentRepository equipmentRepository,
                      ProjectRepository projectRepository,
                      SkillScoreRepository skillScoreRepository,
                      NotificationService notificationService,
                      ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.equipmentRepository = equipmentRepository;
        this.projectRepository = projectRepository;
        this.skillScoreRepository = skillScoreRepository;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    /**
     * 对当前租户库执行初始化:用户表为空时创建管理员(及可选演示数据),
     * 否则仅在演示模式下做演示数据的字段回填。返回实际使用的管理员初始密码(仅新建时非 null)。
     */
    @Transactional
    public String seedIfEmpty(String email, String password, boolean demo) throws Exception {
        if (userRepository.count() > 0) {
            if (demo) {
                upgradeDemoData();
            }
            return null;
        }
        String effectivePassword = seedAdmin(email, password);
        if (demo) {
            seedDemoData();
        }
        return effectivePassword;
    }

    private String seedAdmin(String email, String password) {
        boolean generated = password == null || password.trim().isEmpty();
        String effective = generated ? randomPassword(16) : password.trim();
        User admin = new User();
        admin.setName("系统管理员");
        admin.setEmail(email == null || email.trim().isEmpty() ? "admin@ioedu.cn" : email.trim());
        admin.setPasswordHash(BCrypt.hashpw(effective, BCrypt.gensalt()));
        admin.setRole("ADMIN");
        admin.setMajor("实验室管理");
        admin.setGrade("教师");
        admin.setStudentNo("T0001");
        userRepository.save(admin);
        if (generated) {
            log.warn("首次启动已创建管理员 {},随机初始密码为: {}  —— 请立即登录修改,本密码只显示这一次",
                    admin.getEmail(), effective);
        } else {
            log.info("首次启动已创建管理员 {}(密码来自配置)", admin.getEmail());
        }
        return effective;
    }

    private void seedDemoData() throws Exception {
        log.info("演示模式:开始写入演示数据...");
        User student = new User();
        student.setName("张同学");
        student.setEmail("zhang@stu.ioedu.cn");
        student.setPasswordHash(BCrypt.hashpw("123456", BCrypt.gensalt()));
        student.setRole("STUDENT");
        student.setStudentNo("2023010101");
        student.setMajor("电子信息工程");
        student.setGrade("大二");
        student.setExp(320);
        student.setWeeklyHours(12);
        userRepository.save(student);

        seedTeachers();

        int[] baseline = {65, 58, 45, 52, 38, 60};
        int i = 0;
        for (String dim : AuthService.SKILL_DIMENSIONS) {
            SkillScore s = new SkillScore();
            s.setUserId(student.getId());
            s.setSkillName(dim);
            s.setScore(baseline[i++ % baseline.length]);
            skillScoreRepository.save(s);
        }

        seedEquipment();
        seedProjects();

        notificationService.create(student.getId(), "system", "欢迎加入AI未来实践中心",
                "完成一次能力测评,开启你的项目驱动学习之旅吧!");
        notificationService.create(student.getId(), "project", "新项目上线:ESP32-S3 AI开发板",
                "边缘 AI + 语音识别挑战项目已上线,快去项目中心看看!");

        log.info("演示数据写入完成:设备 {} 台,项目 {} 个", equipmentRepository.count(), projectRepository.count());
    }

    private String randomPassword(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
        }
        return sb.toString();
    }

    /** 演示教师账号:与种子项目的 mentor 字段一一对应 */
    private static final String[][] TEACHERS = {
            {"李老师", "li@ioedu.cn", "T1001"},
            {"王老师", "wang@ioedu.cn", "T1002"},
            {"赵老师", "zhao@ioedu.cn", "T1003"},
            {"陈老师", "chen@ioedu.cn", "T1004"}
    };

    private void seedTeachers() {
        for (String[] t : TEACHERS) {
            User teacher = new User();
            teacher.setName(t[0]);
            teacher.setEmail(t[1]);
            teacher.setPasswordHash(BCrypt.hashpw("123456", BCrypt.gensalt()));
            teacher.setRole("TEACHER");
            teacher.setStudentNo(t[2]);
            teacher.setMajor("电子信息工程");
            teacher.setGrade("教师");
            userRepository.save(teacher);
        }
    }

    /** 按讲师姓名查用户 ID,用于给项目绑定 mentorId */
    private Long teacherIdByName(String name) {
        if (name == null) {
            return null;
        }
        return userRepository.findAll().stream()
                .filter(u -> "TEACHER".equals(u.getRole()) && name.equals(u.getName()))
                .map(User::getId)
                .findFirst().orElse(null);
    }

    /**
     * 演示库升级:按名称/标题回填种子中新增的字段(封面图、Fork 数、PCB 尺寸、分类改名、mentorId),
     * 并插入种子中新增而库里缺失的设备。仅补空值,不覆盖管理员已改的数据;不再补建被删除的演示账号。
     */
    private void upgradeDemoData() throws Exception {
        for (Project p : projectRepository.findAll()) {
            if (p.getMentorId() == null && p.getMentor() != null) {
                Long mentorId = teacherIdByName(p.getMentor());
                if (mentorId != null) {
                    p.setMentorId(mentorId);
                    projectRepository.save(p);
                }
            }
        }

        JsonNode projectSeed = readSeed("seed/projects.json");
        for (JsonNode n : projectSeed) {
            projectRepository.findAll().stream()
                    .filter(p -> n.path("title").asText().equals(p.getTitle()))
                    .findFirst()
                    .ifPresent(p -> {
                        boolean changed = false;
                        if (p.getCoverUrl() == null && n.hasNonNull("coverUrl")) {
                            p.setCoverUrl(n.get("coverUrl").asText());
                            changed = true;
                        }
                        if (p.getPcbSize() == null && n.hasNonNull("pcbSize")) {
                            p.setPcbSize(n.get("pcbSize").asText());
                            changed = true;
                        }
                        if (n.hasNonNull("category") && !n.get("category").asText().equals(p.getCategory())) {
                            p.setCategory(n.get("category").asText());
                            changed = true;
                        }
                        if (changed) {
                            projectRepository.save(p);
                        }
                    });
        }

        JsonNode equipmentSeed = readSeed("seed/equipment.json");
        for (JsonNode n : equipmentSeed) {
            Equipment existing = equipmentRepository.findAll().stream()
                    .filter(e -> n.path("name").asText().equals(e.getName()))
                    .findFirst().orElse(null);
            if (existing == null) {
                saveEquipmentFromSeed(n);
                continue;
            }
            boolean changed = false;
            if (existing.getImageUrl() == null && n.hasNonNull("imageUrl")) {
                existing.setImageUrl(n.get("imageUrl").asText());
                changed = true;
            }
            if (n.hasNonNull("category") && !n.get("category").asText().equals(existing.getCategory())) {
                existing.setCategory(n.get("category").asText());
                changed = true;
            }
            if (changed) {
                equipmentRepository.save(existing);
            }
        }
    }

    private void seedEquipment() throws Exception {
        JsonNode root = readSeed("seed/equipment.json");
        for (JsonNode n : root) {
            saveEquipmentFromSeed(n);
        }
    }

    private void saveEquipmentFromSeed(JsonNode n) {
        Equipment e = new Equipment();
        e.setName(n.path("name").asText());
        e.setModel(n.path("model").asText(null));
        e.setDescription(n.path("description").asText(null));
        e.setCategory(n.path("category").asText(null));
        e.setLocation(n.path("location").asText(null));
        e.setIcon(n.path("icon").asText("🔧"));
        e.setImageUrl(n.hasNonNull("imageUrl") ? n.get("imageUrl").asText() : null);
        e.setTotalCount(n.path("totalCount").asInt(1));
        e.setAvailableCount(n.path("availableCount").asInt(1));
        // 统计类字段一律从 0 开始,由真实借阅累计;种子里的历史数字不再采用
        e.setRating(5.0);
        e.setBorrowCount(0);
        e.setPrice(n.hasNonNull("price") ? n.get("price").asDouble() : null);
        e.setManufacturer(n.path("manufacturer").asText(null));
        e.setStatus("AVAILABLE");
        e.setSpecs(n.path("specs").toString());
        e.setTags(n.path("tags").toString());
        e.setDocs(n.path("docs").toString());
        e.setSuitableProjects(n.path("suitableProjects").toString());
        equipmentRepository.save(e);
    }

    private void seedProjects() throws Exception {
        JsonNode root = readSeed("seed/projects.json");
        for (JsonNode n : root) {
            Project p = new Project();
            p.setTitle(n.path("title").asText());
            p.setSummary(n.path("summary").asText(null));
            p.setDescription(n.path("description").asText(null));
            p.setDifficulty(n.path("difficulty").asText("入门"));
            p.setDuration(n.path("duration").asText("2周"));
            p.setTeamSize(n.path("teamSize").asText("1人"));
            p.setCategory(n.path("category").asText(null));
            p.setIcon(n.path("icon").asText("🔌"));
            p.setMentor(n.path("mentor").asText(null));
            p.setMentorId(teacherIdByName(p.getMentor()));
            p.setAuthor(n.path("author").asText(null));
            p.setLicense(n.path("license").asText("GPL-3.0"));
            p.setVerified(n.path("verified").asBoolean(false));
            p.setLayers(n.hasNonNull("layers") ? n.get("layers").asInt() : null);
            p.setPcbSize(n.hasNonNull("pcbSize") ? n.get("pcbSize").asText() : null);
            p.setCoverUrl(n.hasNonNull("coverUrl") ? n.get("coverUrl").asText() : null);
            p.setCost(n.hasNonNull("cost") ? n.get("cost").asDouble() : null);
            // 统计类字段一律从 0 开始,由真实报名/收藏/浏览累计;种子里的历史数字不再采用
            p.setRating(5.0);
            p.setEnrolledCount(0);
            p.setCompletionRate(0);
            p.setViews(0);
            p.setFavoriteCount(0);
            p.setDownloads(0);
            p.setForks(0);
            p.setTags(n.path("tags").toString());
            p.setFeatures(n.path("features").toString());
            p.setLearningGoals(n.path("learningGoals").toString());
            p.setPrerequisites(n.path("prerequisites").toString());
            p.setSkillRequirements(n.path("skillRequirements").toString());
            p.setSyllabus(n.path("syllabus").toString());
            p.setBom(n.path("bom").toString());
            p.setResources(n.path("resources").toString());
            p.setEquipmentNames(n.path("equipmentNames").toString());
            p.setStatus("PUBLISHED");
            projectRepository.save(p);
        }
    }

    private JsonNode readSeed(String path) throws Exception {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readTree(in);
        }
    }
}
