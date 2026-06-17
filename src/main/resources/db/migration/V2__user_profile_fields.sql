ALTER TABLE users
    ADD COLUMN phone      VARCHAR(32),
    ADD COLUMN job_title  VARCHAR(128),
    ADD COLUMN bio        TEXT,
    ADD COLUMN timezone   VARCHAR(64) NOT NULL DEFAULT 'UTC',
    ADD COLUMN website_url VARCHAR(512),
    ADD COLUMN avatar_url VARCHAR(512);
