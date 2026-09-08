-- 平台库:租户注册表。每个租户独占一个 MySQL 库(db_name),业务表结构由 db/migration 下的脚本维护。
CREATE TABLE `tenants` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(32) NOT NULL,
  `name` varchar(100) NOT NULL,
  `db_name` varchar(64) NOT NULL,
  `custom_domain` varchar(200) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenants_code` (`code`),
  UNIQUE KEY `uk_tenants_db_name` (`db_name`),
  UNIQUE KEY `uk_tenants_custom_domain` (`custom_domain`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
