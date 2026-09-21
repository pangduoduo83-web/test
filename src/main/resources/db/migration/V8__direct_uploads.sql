CREATE TABLE IF NOT EXISTS storage_quota_lock (
 id INT NOT NULL PRIMARY KEY
) ENGINE=InnoDB;
INSERT IGNORE INTO storage_quota_lock (id) VALUES (1);

CREATE TABLE IF NOT EXISTS direct_uploads (
 id VARCHAR(32) NOT NULL PRIMARY KEY,
 user_id BIGINT NOT NULL,
 kind VARCHAR(16) NOT NULL,
 name VARCHAR(255) NOT NULL,
 relative_path VARCHAR(190) NOT NULL,
 bucket VARCHAR(64) NOT NULL,
 staging_key VARCHAR(512) NOT NULL,
 size_bytes BIGINT NOT NULL,
 expires_at DATETIME NOT NULL,
 completed BIT NOT NULL DEFAULT 0,
 INDEX idx_direct_upload_owner (user_id, completed, expires_at),
 INDEX idx_direct_upload_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
