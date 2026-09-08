-- 基线:与 entity 包中 12 个 JPA 实体一一对应(由 Hibernate ddl-auto=create 生成后整理)。
-- 已由 ddl-auto=update 建好的老库不会执行本文件,而是被 baseline-on-migrate 直接标记为 V1。
-- 之后任何表结构变化都只能新增 V{n}__xxx.sql,不要再改本文件。

CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `avatar_url` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `email` varchar(100) NOT NULL,
  `enabled` bit(1) NOT NULL,
  `exp` int NOT NULL,
  `grade` varchar(20) DEFAULT NULL,
  `major` varchar(50) DEFAULT NULL,
  `name` varchar(50) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `role` varchar(20) NOT NULL,
  `student_no` varchar(30) DEFAULT NULL,
  `weekly_hours` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_email` (`email`),
  UNIQUE KEY `uk_users_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `equipment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `available_count` int NOT NULL,
  `borrow_count` int NOT NULL,
  `category` varchar(30) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` text,
  `docs` text,
  `icon` varchar(10) DEFAULT NULL,
  `image_url` varchar(255) DEFAULT NULL,
  `location` varchar(50) DEFAULT NULL,
  `manufacturer` varchar(50) DEFAULT NULL,
  `model` varchar(100) DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `price` double DEFAULT NULL,
  `rating` double NOT NULL,
  `specs` text,
  `status` varchar(20) NOT NULL,
  `suitable_projects` text,
  `tags` text,
  `total_count` int NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `projects` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `assessments` text,
  `author` varchar(50) DEFAULT NULL,
  `bom` text,
  `category` varchar(30) DEFAULT NULL,
  `completion_rate` int NOT NULL,
  `cost` double DEFAULT NULL,
  `cover_url` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` text,
  `difficulty` varchar(10) NOT NULL,
  `downloads` int NOT NULL,
  `duration` varchar(20) DEFAULT NULL,
  `enrolled_count` int NOT NULL,
  `equipment_names` text,
  `favorite_count` int NOT NULL,
  `features` text,
  `forks` int DEFAULT NULL,
  `icon` varchar(10) DEFAULT NULL,
  `layers` int DEFAULT NULL,
  `learning_goals` text,
  `license` varchar(30) DEFAULT NULL,
  `mentor` varchar(50) DEFAULT NULL,
  `mentor_id` bigint DEFAULT NULL,
  `pcb_size` varchar(30) DEFAULT NULL,
  `prerequisites` text,
  `rating` double NOT NULL,
  `resources` text,
  `skill_requirements` text,
  `status` varchar(20) NOT NULL,
  `summary` varchar(300) DEFAULT NULL,
  `syllabus` text,
  `tags` text,
  `team_size` varchar(20) DEFAULT NULL,
  `title` varchar(100) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `verified` bit(1) NOT NULL,
  `views` int NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `borrow_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `applied_at` datetime(6) NOT NULL,
  `approved_at` datetime(6) DEFAULT NULL,
  `approver_name` varchar(50) DEFAULT NULL,
  `duration_days` int NOT NULL,
  `equipment_id` bigint NOT NULL,
  `equipment_name` varchar(100) DEFAULT NULL,
  `project_name` varchar(100) DEFAULT NULL,
  `purpose` varchar(30) DEFAULT NULL,
  `quantity` int NOT NULL,
  `reject_reason` varchar(200) DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `reminder_sent` bit(1) NOT NULL,
  `renewed` bit(1) NOT NULL,
  `request_no` varchar(30) NOT NULL,
  `returned_at` datetime(6) DEFAULT NULL,
  `start_date` date DEFAULT NULL,
  `status` varchar(30) NOT NULL,
  `user_id` bigint NOT NULL,
  `user_name` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_borrow_requests_request_no` (`request_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `enrollments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `current_task` varchar(100) DEFAULT NULL,
  `deadline` date DEFAULT NULL,
  `enrolled_at` datetime(6) NOT NULL,
  `progress` int NOT NULL,
  `project_id` bigint NOT NULL,
  `project_title` varchar(100) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_enrollments_user_project` (`user_id`,`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `submissions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `assessment_name` varchar(50) DEFAULT NULL,
  `attachment_url` varchar(255) DEFAULT NULL,
  `content` varchar(2000) NOT NULL,
  `feedback` varchar(500) DEFAULT NULL,
  `graded_at` datetime(6) DEFAULT NULL,
  `grader_name` varchar(50) DEFAULT NULL,
  `project_id` bigint NOT NULL,
  `project_title` varchar(100) DEFAULT NULL,
  `score` int DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `submitted_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  `user_name` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `discussions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` varchar(1000) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `parent_id` bigint DEFAULT NULL,
  `project_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `user_name` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `favorites` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `project_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_favorites_user_project` (`user_id`,`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `equipment_favorites` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `equipment_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_equipment_favorites_user_equipment` (`user_id`,`equipment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `notifications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` varchar(300) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `is_read` bit(1) NOT NULL,
  `title` varchar(100) NOT NULL,
  `type` varchar(20) NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `skill_scores` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `score` int NOT NULL,
  `skill_name` varchar(30) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_skill_scores_user_skill` (`user_id`,`skill_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `system_settings` (
  `setting_key` varchar(60) NOT NULL,
  `setting_value` text,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
