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
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;

/**
 * 启动种子数据:首次启动(用户表为空)时初始化管理员、演示学生、
 * 参考站的 10 个开源硬件项目与 10 台实验设备。
 * 幂等性:任何一次成功初始化后不再重复执行。
 */
@Slf4j
@Component
public class DataSeeder implements CommandLineRunner {

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

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (userRepository.count() > 0) {
            upgradeExistingData();
            return;
        }
        log.info("首次启动,开始初始化种子数据...");

        User admin = new User();
        admin.setName("系统管理员");
        admin.setEmail("admin@ioedu.cn");
        admin.setPasswordHash(BCrypt.hashpw("admin123", BCrypt.gensalt()));
        admin.setRole("ADMIN");
        admin.setMajor("实验室管理");
        admin.setGrade("教师");
        admin.setStudentNo("T0001");
        userRepository.save(admin);

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

        log.info("种子数据初始化完成:设备 {} 台,项目 {} 个", equipmentRepository.count(), projectRepository.count());
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
     * 老库升级:种子已初始化过时,按名称/标题回填新增字段(封面图、Fork 数、PCB 尺寸、
     * 分类改名、讲师账号与 mentorId),并插入种子中新增而库里缺失的设备。
     * 仅补空值,不覆盖管理员已改的数据。
     */
    private void upgradeExistingData() throws Exception {
        // 补建缺失的教师账号(按邮箱判重)
        for (String[] t : TEACHERS) {
            if (!userRepository.findByEmail(t[1]).isPresent()) {
                User teacher = new User();
                teacher.setName(t[0]);
                teacher.setEmail(t[1]);
                teacher.setPasswordHash(BCrypt.hashpw("123456", BCrypt.gensalt()));
                teacher.setRole("TEACHER");
                teacher.setStudentNo(t[2]);
                teacher.setMajor("电子信息工程");
                teacher.setGrade("教师");
                userRepository.save(teacher);
                log.info("老库升级:补建教师账号 {}", t[1]);
            }
        }

        // 回填项目 mentorId(按讲师姓名匹配)
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
                        if ((p.getForks() == null || p.getForks() == 0) && n.hasNonNull("forks")) {
                            p.setForks(n.get("forks").asInt());
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
        e.setRating(n.path("rating").asDouble(5.0));
        e.setBorrowCount(n.path("borrowCount").asInt(0));
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
            p.setRating(n.path("rating").asDouble(5.0));
            p.setEnrolledCount(n.path("enrolledCount").asInt(0));
            p.setCompletionRate(n.path("completionRate").asInt(0));
            p.setViews(n.path("views").asInt(0));
            p.setFavoriteCount(n.path("favoriteCount").asInt(0));
            p.setDownloads(n.path("downloads").asInt(0));
            p.setForks(n.path("forks").asInt(0));
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
