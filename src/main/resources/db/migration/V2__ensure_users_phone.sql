-- 老库对齐:ddl-auto=update 时代部署的库可能缺少 users.phone(手机号登录功能晚于建库)。
-- 被 baseline 标记为 V1 的老库不会执行 V1,因此用幂等方式补列;新库执行到这里是空操作。
SET @has_phone := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'phone'
);
SET @ddl := IF(@has_phone = 0,
  'ALTER TABLE `users` ADD COLUMN `phone` varchar(20) DEFAULT NULL, ADD UNIQUE KEY `uk_users_phone` (`phone`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
