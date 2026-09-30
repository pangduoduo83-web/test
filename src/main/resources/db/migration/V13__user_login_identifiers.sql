-- 登录标识补充:教师工号独立于学生学号,并为两个标识建立查询索引。
-- 不对历史学号加唯一约束,避免老库中已有重复数据导致迁移失败;
-- 应用层会阻止新增/修改重复标识,登录遇到历史重复时会要求管理员处理。
SET @has_teacher_no := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'teacher_no'
);
SET @ddl := IF(@has_teacher_no = 0,
  'ALTER TABLE `users` ADD COLUMN `teacher_no` varchar(30) DEFAULT NULL',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 兼容旧数据:历史教师账号曾把工号放在 student_no,迁移到教师工号字段。
UPDATE `users`
SET `teacher_no` = `student_no`
WHERE `teacher_no` IS NULL AND `role` = 'TEACHER' AND `student_no` IS NOT NULL AND TRIM(`student_no`) <> '';
UPDATE `users` SET `student_no` = NULL
WHERE `role` = 'TEACHER' AND `teacher_no` = `student_no`;

SET @has_student_idx := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND INDEX_NAME = 'idx_users_student_no_login'
);
SET @ddl := IF(@has_student_idx = 0,
  'CREATE INDEX `idx_users_student_no_login` ON `users` (`student_no`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_teacher_idx := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND INDEX_NAME = 'idx_users_teacher_no_login'
);
SET @ddl := IF(@has_teacher_idx = 0,
  'CREATE INDEX `idx_users_teacher_no_login` ON `users` (`teacher_no`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
