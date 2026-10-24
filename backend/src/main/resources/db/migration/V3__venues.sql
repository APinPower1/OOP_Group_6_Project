-- Managed venue list: feeds the event form's venue dropdown. Admins add and
-- remove entries; events store venue as a plain string, so removal never
-- rewrites historical events.
CREATE TABLE venues (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO venues (name) VALUES
    ('Main Auditorium'),
    ('Seminar Hall A'),
    ('Seminar Hall B'),
    ('Open Air Theatre'),
    ('Sports Complex');
