-- 设备借阅审核开关：旧设备默认需要审核，保持原有行为。
SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'equipment' AND column_name = 'approval_required') = 0,
  'ALTER TABLE equipment ADD COLUMN approval_required TINYINT(1) NOT NULL DEFAULT 1',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
