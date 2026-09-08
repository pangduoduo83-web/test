-- 项目商店基线

CREATE TABLE `hub_tenants` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(32) NOT NULL,
  `name` varchar(100) NOT NULL,
  `api_key_hash` varchar(64) NOT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_hub_tenants_code` (`code`),
  UNIQUE KEY `uk_hub_tenants_key` (`api_key_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `hub_admins` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_hub_admins_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `hub_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `type` varchar(20) NOT NULL,
  `title` varchar(100) NOT NULL,
  `summary` varchar(300) DEFAULT NULL,
  `category` varchar(30) DEFAULT NULL,
  `tags` text,
  `cover_url` varchar(255) DEFAULT NULL,
  `publisher_tenant_id` bigint NOT NULL,
  `publisher_tenant_name` varchar(100) DEFAULT NULL,
  `publisher_user_name` varchar(50) DEFAULT NULL,
  `visibility` varchar(20) NOT NULL,
  `review_status` varchar(20) NOT NULL,
  `review_comment` varchar(500) DEFAULT NULL,
  `current_version_id` bigint DEFAULT NULL,
  `latest_version_no` int NOT NULL,
  `install_count` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_hub_items_status` (`review_status`, `visibility`),
  KEY `idx_hub_items_publisher` (`publisher_tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `hub_item_versions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `item_id` bigint NOT NULL,
  `version_no` int NOT NULL,
  `payload` longtext NOT NULL,
  `changelog` varchar(500) DEFAULT NULL,
  `created_by` varchar(50) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_hub_item_versions` (`item_id`, `version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `hub_item_grants` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `item_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `granted_by` varchar(50) DEFAULT NULL,
  `granted_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_hub_item_grants` (`item_id`, `tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `hub_review_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `item_id` bigint NOT NULL,
  `version_id` bigint DEFAULT NULL,
  `reviewer` varchar(50) NOT NULL,
  `decision` varchar(20) NOT NULL,
  `comment` varchar(500) DEFAULT NULL,
  `reviewed_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_hub_review_logs_item` (`item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `hub_installs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `item_id` bigint NOT NULL,
  `version_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `installed_by` varchar(50) DEFAULT NULL,
  `installed_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_hub_installs_item` (`item_id`),
  KEY `idx_hub_installs_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `hub_assets` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `sha256` varchar(64) NOT NULL,
  `ext` varchar(10) NOT NULL,
  `size` bigint NOT NULL,
  `mime` varchar(100) DEFAULT NULL,
  `uploaded_by_tenant_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_hub_assets_sha` (`sha256`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
