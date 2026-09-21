-- Per-tenant, rerunnable after a partially applied MySQL migration.
SET @review_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'submissions' AND column_name = 'attachments') = 0, 'ALTER TABLE submissions ADD COLUMN attachments LONGTEXT NULL', 'SELECT 1');
PREPARE review_stmt FROM @review_ddl;
EXECUTE review_stmt;
DEALLOCATE PREPARE review_stmt;
SET @review_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'projects' AND column_name = 'review_rubric') = 0, 'ALTER TABLE projects ADD COLUMN review_rubric TEXT NULL', 'SELECT 1');
PREPARE review_stmt FROM @review_ddl;
EXECUTE review_stmt;
DEALLOCATE PREPARE review_stmt;
SET @review_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'projects' AND column_name = 'submission_requirements') = 0, 'ALTER TABLE projects ADD COLUMN submission_requirements TEXT NULL', 'SELECT 1');
PREPARE review_stmt FROM @review_ddl;
EXECUTE review_stmt;
DEALLOCATE PREPARE review_stmt;
CREATE TABLE IF NOT EXISTS submission_assets (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL, url VARCHAR(255) NOT NULL,
 name VARCHAR(255) NOT NULL, size BIGINT NOT NULL,
 UNIQUE KEY uk_submission_asset_url (url)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS review_jobs (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 submission_id BIGINT NOT NULL, fingerprint VARCHAR(64) NOT NULL,
 status VARCHAR(24) NOT NULL, progress INT NOT NULL DEFAULT 0,
 message VARCHAR(500), materials LONGTEXT, result LONGTEXT,
 created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
 INDEX idx_review_submission (submission_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
