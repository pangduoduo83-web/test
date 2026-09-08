-- 推荐置顶:featured 为真的条目在各客户商店里排在最前
ALTER TABLE `hub_items`
  ADD COLUMN `featured` tinyint(1) NOT NULL DEFAULT 0 AFTER `install_count`,
  ADD COLUMN `featured_at` datetime(6) DEFAULT NULL AFTER `featured`;

CREATE INDEX `idx_hub_items_featured` ON `hub_items` (`featured`, `featured_at`);
