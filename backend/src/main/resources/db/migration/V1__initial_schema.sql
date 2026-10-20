-- Flyway V1 initial schema for Student Portal with Event Management System.
-- Requires the student_portal database to exist first; create it using database/create_database.sql.
-- MySQL 8.4 / InnoDB. Flyway executes this migration once; do not run manually after migration.

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    department VARCHAR(120) NOT NULL,
    role VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('STUDENT','ORGANIZER','FACULTY','ADMIN'))
) ENGINE=InnoDB;

CREATE TABLE student_profiles (
    user_id BIGINT NOT NULL,
    roll_number VARCHAR(40) NOT NULL,
    semester INT NOT NULL,
    batch VARCHAR(40) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT uq_student_roll_number UNIQUE (roll_number),
    CONSTRAINT ck_student_semester CHECK (semester >= 1),
    CONSTRAINT fk_student_profile_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE organizer_profiles (
    user_id BIGINT NOT NULL,
    designation VARCHAR(100) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_organizer_profile_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE faculty_profiles (
    user_id BIGINT NOT NULL,
    employee_id VARCHAR(40) NOT NULL,
    designation VARCHAR(100) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT uq_faculty_employee_id UNIQUE (employee_id),
    CONSTRAINT fk_faculty_profile_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE admin_profiles (
    user_id BIGINT NOT NULL,
    admin_level INT NOT NULL DEFAULT 1,
    PRIMARY KEY (user_id),
    CONSTRAINT ck_admin_level CHECK (admin_level >= 1),
    CONSTRAINT fk_admin_profile_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(160) NOT NULL,
    description TEXT NOT NULL,
    venue VARCHAR(160) NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    capacity INT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING_APPROVAL',
    created_by BIGINT NOT NULL,
    reviewed_by BIGINT NULL,
    review_reason VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_events_status_start (status, start_time),
    KEY idx_events_venue_time (venue, start_time, end_time),
    KEY idx_events_creator (created_by),
    CONSTRAINT ck_events_capacity CHECK (capacity > 0),
    CONSTRAINT ck_events_time CHECK (end_time > start_time),
    CONSTRAINT ck_events_status CHECK (status IN ('PENDING_APPROVAL','APPROVED','REJECTED','CANCELLED')),
    CONSTRAINT fk_events_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_events_reviewer FOREIGN KEY (reviewed_by) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE registrations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    registered_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(16) NOT NULL DEFAULT 'CONFIRMED',
    ticket_no VARCHAR(80) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_registration_student_event UNIQUE (student_id, event_id),
    CONSTRAINT uq_registration_ticket UNIQUE (ticket_no),
    KEY idx_registrations_event_status (event_id, status),
    CONSTRAINT ck_registration_status CHECK (status IN ('CONFIRMED','CANCELLED')),
    CONSTRAINT fk_registration_student FOREIGN KEY (student_id) REFERENCES student_profiles(user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_registration_event FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE clubs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(140) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(100) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_clubs_name UNIQUE (name)
) ENGINE=InnoDB;

CREATE TABLE club_members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    club_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(12) NOT NULL DEFAULT 'ACTIVE',
    PRIMARY KEY (id),
    CONSTRAINT uq_club_membership UNIQUE (club_id, student_id),
    KEY idx_club_members_student_status (student_id, status),
    CONSTRAINT ck_club_membership_status CHECK (status IN ('ACTIVE','LEFT')),
    CONSTRAINT fk_club_membership_club FOREIGN KEY (club_id) REFERENCES clubs(id) ON DELETE RESTRICT,
    CONSTRAINT fk_club_membership_student FOREIGN KEY (student_id) REFERENCES student_profiles(user_id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE announcements (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(160) NOT NULL,
    content TEXT NOT NULL,
    created_by BIGINT NOT NULL,
    audience_role VARCHAR(20) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at DATETIME NULL,
    archived_at DATETIME NULL,
    PRIMARY KEY (id),
    KEY idx_announcements_status_published (status, published_at),
    CONSTRAINT ck_announcement_audience CHECK (audience_role IS NULL OR audience_role IN ('STUDENT','ORGANIZER','FACULTY','ADMIN')),
    CONSTRAINT ck_announcement_status CHECK (status IN ('PUBLISHED','ARCHIVED')),
    CONSTRAINT fk_announcement_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recipient_user_id BIGINT NOT NULL,
    announcement_id BIGINT NULL,
    event_id BIGINT NULL,
    notification_type VARCHAR(24) NOT NULL,
    title VARCHAR(160) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_notification_recipient_announcement UNIQUE (recipient_user_id, announcement_id),
    CONSTRAINT uq_notification_recipient_event_type UNIQUE (recipient_user_id, event_id, notification_type),
    KEY idx_notifications_recipient_read (recipient_user_id, read_at, created_at),
    CONSTRAINT ck_notification_type CHECK (notification_type IN ('ANNOUNCEMENT','EVENT_CANCELLED')),
    CONSTRAINT ck_notification_target CHECK (
        (notification_type = 'ANNOUNCEMENT' AND announcement_id IS NOT NULL AND event_id IS NULL)
        OR
        (notification_type = 'EVENT_CANCELLED' AND announcement_id IS NULL AND event_id IS NOT NULL)
    ),
    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_notification_announcement FOREIGN KEY (announcement_id) REFERENCES announcements(id) ON DELETE RESTRICT,
    CONSTRAINT fk_notification_event FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE feedback (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    rating INT NOT NULL,
    comments VARCHAR(2000) NULL,
    submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_feedback_student_event UNIQUE (student_id, event_id),
    KEY idx_feedback_event (event_id),
    CONSTRAINT ck_feedback_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT fk_feedback_student FOREIGN KEY (student_id) REFERENCES student_profiles(user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_feedback_event FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- Notes for implementers:
-- 1. The service layer must enforce that role-specific profile rows match users.role.
-- 2. Do not create real passwords in SQL. Seed demo accounts using an application helper so password_hash is a BCrypt hash.
-- 3. Do not persist registered_count; count CONFIRMED rows for event roster/capacity displays.
-- 4. Transactional registration uses SELECT id, status, start_time, capacity FROM events WHERE id=? FOR UPDATE.
-- 5. For cancellation of an event, update event status, update confirmed registrations to CANCELLED,
--    and create notification records in a single transaction.
