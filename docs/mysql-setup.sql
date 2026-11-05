-- Run this script as a MySQL administrator, for example root, on 127.0.0.1:3306.
-- Applied locally on 2026-10-21; keep for setup on a new environment.
-- This script does not change the account password or other databases.
CREATE DATABASE IF NOT EXISTS campus_sharing
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
    ON campus_sharing.* TO 'yanhuo'@'localhost';

-- Next, connect as yanhuo, select campus_sharing, and run:
-- src/main/resources/db/schema.sql
-- src/main/resources/db/data.sql
-- No FLUSH PRIVILEGES is needed after GRANT.
