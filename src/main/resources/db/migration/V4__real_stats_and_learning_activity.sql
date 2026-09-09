-- 1) 统计口径回归真实数据:
--    报名数/收藏数/完成率按 enrollments、favorites 重算;浏览/下载/Fork 的种子基数清零(下载与 Fork 不再展示);
--    设备累计借出次数按已批准过的借阅记录重算。
UPDATE projects p SET
  enrolled_count = (SELECT COUNT(*) FROM enrollments e WHERE e.project_id = p.id),
  favorite_count = (SELECT COUNT(*) FROM favorites f WHERE f.project_id = p.id),
  completion_rate = IFNULL(ROUND(100 * (SELECT COUNT(*) FROM enrollments e WHERE e.project_id = p.id AND e.status = 'COMPLETED')
                                   / NULLIF((SELECT COUNT(*) FROM enrollments e WHERE e.project_id = p.id), 0)), 0),
  views = 0,
  downloads = 0,
  forks = 0;

UPDATE equipment q SET
  borrow_count = (SELECT COUNT(*) FROM borrow_requests b WHERE b.equipment_id = q.id
                  AND b.status IN ('APPROVED', 'RETURN_REQUESTED', 'RETURNED'));

-- 2) 学习活动日志:个人中心趋势图与"本周学习"统计的真实数据来源
CREATE TABLE IF NOT EXISTS `learning_activities` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `type` varchar(20) NOT NULL,
  `ref_id` bigint DEFAULT NULL,
  `title` varchar(200) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_learning_activities_user_time` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3) AI 会话可携带上下文(如正在学习的项目),后续消息沿用
ALTER TABLE `ai_conversations` ADD COLUMN `context` text DEFAULT NULL;
