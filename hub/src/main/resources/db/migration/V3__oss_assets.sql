SET @asset_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'hub_assets' AND column_name = 'object_key') = 0, 'ALTER TABLE hub_assets ADD COLUMN object_key VARCHAR(512) NULL', 'SELECT 1');
PREPARE asset_stmt FROM @asset_ddl;
EXECUTE asset_stmt;
DEALLOCATE PREPARE asset_stmt;
SET @asset_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'hub_assets' AND column_name = 'bucket') = 0, 'ALTER TABLE hub_assets ADD COLUMN bucket VARCHAR(64) NULL', 'SELECT 1');
PREPARE asset_stmt FROM @asset_ddl;
EXECUTE asset_stmt;
DEALLOCATE PREPARE asset_stmt;
