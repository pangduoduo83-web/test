-- 站点配额与套餐:用户数上限、上传存储上限、AI 月度 Token 预算、到期日;为空表示不限 / 永久
ALTER TABLE `tenants`
  ADD COLUMN `plan` varchar(30) DEFAULT NULL,
  ADD COLUMN `max_users` int DEFAULT NULL,
  ADD COLUMN `storage_limit_mb` int DEFAULT NULL,
  ADD COLUMN `ai_monthly_tokens` bigint DEFAULT NULL,
  ADD COLUMN `expires_at` date DEFAULT NULL;
