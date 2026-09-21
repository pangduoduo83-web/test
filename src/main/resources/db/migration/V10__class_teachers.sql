-- 多教师班级:保留 course_classes.teacher_id 作为负责人兼容字段,关系表承载协作教师。
CREATE TABLE IF NOT EXISTS `class_teachers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `class_id` bigint NOT NULL,
  `teacher_id` bigint NOT NULL,
  `role` varchar(20) NOT NULL DEFAULT 'TEACHER',
  `added_by` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_class_teachers` (`class_id`, `teacher_id`),
  KEY `idx_class_teachers_teacher` (`teacher_id`),
  KEY `idx_class_teachers_class` (`class_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `class_teachers` (`class_id`, `teacher_id`, `role`, `added_by`, `created_at`)
SELECT c.`id`, c.`teacher_id`, 'OWNER', c.`created_by`, COALESCE(c.`created_at`, NOW(6))
FROM `course_classes` c
WHERE c.`teacher_id` IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `class_teachers` ct
    WHERE ct.`class_id` = c.`id` AND ct.`teacher_id` = c.`teacher_id`
  );
