-- 项目商店:记录本地项目来自商店的哪个条目/版本(上游指针),用于显示「可更新」与再次发布时追加版本
ALTER TABLE `projects`
  ADD COLUMN `hub_item_id` bigint DEFAULT NULL,
  ADD COLUMN `hub_version_no` int DEFAULT NULL;
