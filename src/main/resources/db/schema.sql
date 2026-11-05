-- Run once against a NEW campus_sharing database. This script never drops tables.
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    phone VARCHAR(11) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    nickname VARCHAR(40) NOT NULL,
    contact_info VARCHAR(200) NOT NULL,
    role VARCHAR(16) NOT NULL DEFAULT 'STUDENT',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_user_role CHECK (role IN ('STUDENT', 'ADMIN')),
    CONSTRAINT ck_user_status CHECK (status IN ('ACTIVE', 'BANNED'))
);
CREATE TABLE categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(30) NOT NULL UNIQUE,
    sort_order INT NOT NULL
);
CREATE TABLE items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    contact_info VARCHAR(200) NOT NULL,
    tool_condition VARCHAR(24),
    review_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    review_reason VARCHAR(500),
    status VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE',
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (owner_id) REFERENCES users(id),
    FOREIGN KEY (category_id) REFERENCES categories(id),
    CONSTRAINT ck_item_review CHECK (review_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_item_status CHECK (status IN ('AVAILABLE', 'RESERVED', 'COMPLETED')),
    CONSTRAINT ck_tool_condition CHECK (tool_condition IS NULL OR tool_condition IN ('NORMAL', 'MINOR_ERROR', 'UNUSABLE'))
);
CREATE INDEX idx_items_search ON items(category_id, review_status, status, created_at);
CREATE INDEX idx_items_owner ON items(owner_id, created_at);
CREATE TABLE item_images (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_id BIGINT NOT NULL,
    file_path VARCHAR(255) NOT NULL,
    sort_order INT NOT NULL,
    FOREIGN KEY (item_id) REFERENCES items(id),
    CONSTRAINT uq_image_slot UNIQUE (item_id, sort_order),
    CONSTRAINT ck_image_slot CHECK (sort_order IN (1, 2))
);
CREATE TABLE reservations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_id BIGINT NOT NULL,
    requester_id BIGINT NOT NULL,
    message VARCHAR(500) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (item_id) REFERENCES items(id),
    FOREIGN KEY (requester_id) REFERENCES users(id),
    CONSTRAINT ck_reservation_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'COMPLETED', 'CANCELLED', 'EXPIRED'))
);
CREATE INDEX idx_reservations_item ON reservations(item_id, status);
CREATE INDEX idx_reservations_requester ON reservations(requester_id, created_at);
CREATE TABLE handover_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id BIGINT NOT NULL UNIQUE,
    code_hash VARCHAR(100) NOT NULL,
    expires_at DATETIME NOT NULL,
    completed_at DATETIME,
    attempts INT NOT NULL DEFAULT 0,
    FOREIGN KEY (reservation_id) REFERENCES reservations(id),
    CONSTRAINT ck_handover_attempts CHECK (attempts >= 0)
);
CREATE TABLE admin_audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_id BIGINT NOT NULL,
    action VARCHAR(40) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (admin_id) REFERENCES users(id)
);
CREATE INDEX idx_audit_admin ON admin_audit_logs(admin_id, created_at);
CREATE INDEX idx_audit_target ON admin_audit_logs(target_type, target_id);
