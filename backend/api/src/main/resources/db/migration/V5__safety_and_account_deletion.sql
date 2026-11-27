-- Users can block each other: they stop seeing each other's books and cannot start or resume exchanges
CREATE TABLE user_blocks (
    blocker_uid VARCHAR(128) NOT NULL REFERENCES users (uid) ON DELETE CASCADE,
    blocked_uid VARCHAR(128) NOT NULL REFERENCES users (uid) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_uid, blocked_uid),
    CONSTRAINT chk_blocks_distinct_users CHECK (blocker_uid <> blocked_uid)
);
CREATE INDEX idx_user_blocks_blocked ON user_blocks (blocked_uid);

-- Reports are reviewed by a person; the reporter is kept only while their account exists
CREATE TABLE user_reports (
    id           BIGSERIAL PRIMARY KEY,
    reporter_uid VARCHAR(128) REFERENCES users (uid) ON DELETE SET NULL,
    reported_uid VARCHAR(128) NOT NULL REFERENCES users (uid) ON DELETE CASCADE,
    exchange_id  BIGINT REFERENCES exchanges (id) ON DELETE SET NULL,
    reason       VARCHAR(30)  NOT NULL,
    comment      VARCHAR(1000),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Deleting an account removes the exchanges it took part in
ALTER TABLE exchanges DROP CONSTRAINT exchanges_owner_uid_fkey;
ALTER TABLE exchanges ADD CONSTRAINT exchanges_owner_uid_fkey
    FOREIGN KEY (owner_uid) REFERENCES users (uid) ON DELETE CASCADE;
ALTER TABLE exchanges DROP CONSTRAINT exchanges_requester_uid_fkey;
ALTER TABLE exchanges ADD CONSTRAINT exchanges_requester_uid_fkey
    FOREIGN KEY (requester_uid) REFERENCES users (uid) ON DELETE CASCADE;
