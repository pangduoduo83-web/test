-- 技能画像升级:
--   1. skill_dimensions:技能维度改为后台可配置(之前硬编码 6 个);
--   2. skill_scores 增加 self_score(自评,可空)与 evidence_count(已计入的实证次数),score 变为综合分;
--   3. skill_score_events:分数变动流水,驱动真实的成长曲线。
-- ALTER 采用与 V2 相同的幂等写法,baseline 标记为 V1 的老库与全新库都会执行本文件。
-- 排序规则:新表不指定 CHARSET(继承库默认),并显式对齐到 skill_scores 的排序规则,
-- 否则在 collation-server=utf8mb4_unicode_ci 的老库上,新表会落到 utf8mb4_0900_ai_ci,与 skill_scores 比较时报
-- "Illegal mix of collations"。比较语句再加 COLLATE utf8mb4_bin 双保险。

CREATE TABLE IF NOT EXISTS `skill_dimensions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(30) NOT NULL,
  `description` varchar(200) DEFAULT NULL,
  `sort_order` int NOT NULL,
  `enabled` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_skill_dimensions_name` (`name`)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS `skill_score_events` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `skill_name` varchar(30) NOT NULL,
  `source` varchar(20) NOT NULL,
  `before_score` int NOT NULL,
  `after_score` int NOT NULL,
  `overall_after` int NOT NULL,
  `ref_id` bigint DEFAULT NULL,
  `note` varchar(200) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_skill_score_events_user_time` (`user_id`,`created_at`)
) ENGINE=InnoDB;

-- 把两张新表的排序规则对齐到 skill_scores(兼容首次执行失败后已被建出来的表)
SET @target_coll := (
  SELECT TABLE_COLLATION FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'skill_scores'
);
SET @ddl := IF(@target_coll IS NOT NULL AND @target_coll LIKE 'utf8mb4%',
  CONCAT('ALTER TABLE `skill_dimensions` CONVERT TO CHARACTER SET utf8mb4 COLLATE ', @target_coll),
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl := IF(@target_coll IS NOT NULL AND @target_coll LIKE 'utf8mb4%',
  CONCAT('ALTER TABLE `skill_score_events` CONVERT TO CHARACTER SET utf8mb4 COLLATE ', @target_coll),
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_self_score := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'skill_scores' AND COLUMN_NAME = 'self_score'
);
SET @ddl := IF(@has_self_score = 0,
  'ALTER TABLE `skill_scores` ADD COLUMN `self_score` int DEFAULT NULL',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_evidence_count := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'skill_scores' AND COLUMN_NAME = 'evidence_count'
);
SET @ddl := IF(@has_evidence_count = 0,
  'ALTER TABLE `skill_scores` ADD COLUMN `evidence_count` int NOT NULL DEFAULT 0',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 老数据的分数全部来自自评,迁移时把它同时作为自评分保留,综合分不变。
UPDATE `skill_scores` SET `self_score` = `score` WHERE `self_score` IS NULL AND `evidence_count` = 0;

-- 默认 6 个维度(与原硬编码一致),已存在则跳过。
INSERT INTO `skill_dimensions` (`name`, `description`, `sort_order`, `enabled`, `created_at`, `updated_at`)
SELECT d.`name`, d.`description`, d.`sort_order`, b'1', NOW(6), NOW(6)
FROM (
  SELECT '嵌入式开发' AS `name`, '微控制器编程、外设驱动开发、实时操作系统等核心技能' AS `description`, 1 AS `sort_order`
  UNION ALL SELECT '编程能力', 'C/C++、Python 等编程语言,数据结构与算法基础', 2
  UNION ALL SELECT '通信技术', '有线/无线通信协议栈、组网与协议分析能力', 3
  UNION ALL SELECT 'PCB设计', '电路原理图设计、PCB 布局布线、信号完整性分析', 4
  UNION ALL SELECT '信号处理', '信号采集、数字滤波、频谱分析与算法实现', 5
  UNION ALL SELECT '硬件调试', '仪器仪表使用、电路故障定位与焊接工艺', 6
) d
WHERE NOT EXISTS (
  SELECT 1 FROM `skill_dimensions` x WHERE x.`name` COLLATE utf8mb4_bin = d.`name` COLLATE utf8mb4_bin
);

-- 老库里可能存在通过接口写入的非默认技能名,补录为维度以免历史数据在雷达图上消失。
INSERT INTO `skill_dimensions` (`name`, `description`, `sort_order`, `enabled`, `created_at`, `updated_at`)
SELECT s.`skill_name`, NULL, 100, b'1', NOW(6), NOW(6)
FROM (SELECT DISTINCT `skill_name` FROM `skill_scores`) s
WHERE NOT EXISTS (
  SELECT 1 FROM `skill_dimensions` x WHERE x.`name` COLLATE utf8mb4_bin = s.`skill_name` COLLATE utf8mb4_bin
);
