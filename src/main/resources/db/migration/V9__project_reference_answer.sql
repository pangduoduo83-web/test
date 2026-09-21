-- Teacher-only answer guide used by AI review. It is intentionally excluded from public project JSON.
SET @reference_answer_ddl = IF((SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'projects' AND column_name = 'reference_answer') = 0,
    'ALTER TABLE projects ADD COLUMN reference_answer LONGTEXT NULL', 'SELECT 1');
PREPARE reference_answer_stmt FROM @reference_answer_ddl;
EXECUTE reference_answer_stmt;
DEALLOCATE PREPARE reference_answer_stmt;
