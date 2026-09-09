-- 1) 课程班:老师开班、学生加入、按班布置项目
CREATE TABLE IF NOT EXISTS `course_classes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(60) NOT NULL,
  `description` varchar(300) DEFAULT NULL,
  `teacher_id` bigint DEFAULT NULL,
  `teacher_name` varchar(50) DEFAULT NULL,
  `join_code` varchar(12) NOT NULL,
  `join_enabled` tinyint(1) NOT NULL DEFAULT 1,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_course_classes_code` (`join_code`),
  KEY `idx_course_classes_teacher` (`teacher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `class_members` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `class_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `joined_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_class_members` (`class_id`, `user_id`),
  KEY `idx_class_members_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `class_assignments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `class_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `project_title` varchar(100) DEFAULT NULL,
  `deadline` date DEFAULT NULL,
  `note` varchar(300) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_class_assignments` (`class_id`, `project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `class_announcements` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `class_id` bigint NOT NULL,
  `project_id` bigint DEFAULT NULL,
  `author_id` bigint DEFAULT NULL,
  `author_name` varchar(50) DEFAULT NULL,
  `title` varchar(100) NOT NULL,
  `content` varchar(1000) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_class_announcements_class` (`class_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2) 报名:按大纲阶段打勾的进度 + 来源班级
ALTER TABLE `enrollments`
  ADD COLUMN `completed_phases` text DEFAULT NULL,
  ADD COLUMN `class_id` bigint DEFAULT NULL;

-- 3) 登录防爆破
ALTER TABLE `users`
  ADD COLUMN `failed_logins` int NOT NULL DEFAULT 0,
  ADD COLUMN `locked_until` datetime(6) DEFAULT NULL;

-- 4) 管理端 / 教师端 / 平台接口的写操作审计
CREATE TABLE IF NOT EXISTS `audit_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `actor_id` bigint DEFAULT NULL,
  `actor_name` varchar(50) DEFAULT NULL,
  `actor_role` varchar(20) DEFAULT NULL,
  `method` varchar(8) NOT NULL,
  `path` varchar(200) NOT NULL,
  `summary` varchar(300) DEFAULT NULL,
  `status` int NOT NULL,
  `ip` varchar(45) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_audit_logs_time` (`created_at`),
  KEY `idx_audit_logs_actor` (`actor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
