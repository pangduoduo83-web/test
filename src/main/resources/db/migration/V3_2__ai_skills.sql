-- AI 应用:SKILL(可复用的提示词 + 输入结构 + 工具白名单)、会话与消息、运行与工具调用审计、工具策略、用量

CREATE TABLE `ai_skills` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `skill_key` varchar(60) NOT NULL,
  `name` varchar(60) NOT NULL,
  `description` varchar(300) DEFAULT NULL,
  `icon` varchar(10) DEFAULT NULL,
  `category` varchar(30) DEFAULT NULL,
  `scope` varchar(20) NOT NULL,
  `owner_user_id` bigint DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `spec` longtext NOT NULL,
  `version` int NOT NULL,
  `forked_from` varchar(60) DEFAULT NULL,
  `hub_item_id` bigint DEFAULT NULL,
  `hub_version_no` int DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_skills_key` (`skill_key`),
  KEY `idx_ai_skills_scope_owner` (`scope`, `owner_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `ai_skill_versions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `skill_id` bigint NOT NULL,
  `version` int NOT NULL,
  `spec` longtext NOT NULL,
  `changelog` varchar(300) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_skill_versions` (`skill_id`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `ai_conversations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `skill_key` varchar(60) DEFAULT NULL,
  `title` varchar(100) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ai_conversations_user` (`user_id`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `ai_messages` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `conversation_id` bigint NOT NULL,
  `role` varchar(20) NOT NULL,
  `content` longtext,
  `tool_calls` text,
  `tool_call_id` varchar(80) DEFAULT NULL,
  `tool_name` varchar(64) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ai_messages_conv` (`conversation_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `ai_runs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `user_name` varchar(50) DEFAULT NULL,
  `skill_key` varchar(60) DEFAULT NULL,
  `skill_version` int DEFAULT NULL,
  `conversation_id` bigint DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `input` text,
  `output` longtext,
  `tool_rounds` int NOT NULL,
  `prompt_tokens` int NOT NULL,
  `completion_tokens` int NOT NULL,
  `latency_ms` int NOT NULL,
  `error` varchar(500) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ai_runs_user_time` (`user_id`, `created_at`),
  KEY `idx_ai_runs_time` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `ai_tool_invocations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `run_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `tool_name` varchar(64) NOT NULL,
  `arguments` text,
  `result_summary` text,
  `ok` bit(1) NOT NULL,
  `confirmed` bit(1) NOT NULL,
  `latency_ms` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ai_tool_invocations_run` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `ai_tool_policies` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tool_name` varchar(64) NOT NULL,
  `enabled` bit(1) NOT NULL,
  `min_role` varchar(20) DEFAULT NULL,
  `requires_confirmation` bit(1) NOT NULL,
  `daily_limit` int DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_tool_policies_name` (`tool_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `ai_usage_daily` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `day` date NOT NULL,
  `runs` int NOT NULL,
  `prompt_tokens` int NOT NULL,
  `completion_tokens` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_usage_daily` (`user_id`, `day`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
