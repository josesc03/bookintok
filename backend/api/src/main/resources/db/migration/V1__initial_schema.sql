CREATE TABLE users (
    uid            VARCHAR(128) PRIMARY KEY,
    nickname       VARCHAR(50)  NOT NULL UNIQUE,
    display_name   VARCHAR(100),
    email          VARCHAR(254) NOT NULL UNIQUE,
    image_url      VARCHAR(500),
    latitude       DOUBLE PRECISION,
    longitude      DOUBLE PRECISION,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_users_location CHECK ((latitude IS NULL) = (longitude IS NULL))
);

CREATE TABLE books (
    id                 BIGSERIAL PRIMARY KEY,
    owner_uid          VARCHAR(128) NOT NULL REFERENCES users (uid) ON DELETE CASCADE,
    title              VARCHAR(255) NOT NULL,
    author             VARCHAR(255) NOT NULL,
    description        VARCHAR(2000),
    main_category      VARCHAR(100) NOT NULL,
    secondary_category VARCHAR(100),
    language           VARCHAR(50)  NOT NULL,
    cover_type         VARCHAR(20)  NOT NULL,
    condition          VARCHAR(20)  NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',
    image_url          VARCHAR(500),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version            BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_books_owner ON books (owner_uid);
CREATE INDEX idx_books_status_created ON books (status, created_at DESC);

CREATE TABLE exchanges (
    id                  BIGSERIAL PRIMARY KEY,
    book_id             BIGINT       NOT NULL REFERENCES books (id) ON DELETE CASCADE,
    owner_uid           VARCHAR(128) NOT NULL REFERENCES users (uid),
    requester_uid       VARCHAR(128) NOT NULL REFERENCES users (uid),
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    owner_confirmed     BOOLEAN      NOT NULL DEFAULT FALSE,
    requester_confirmed BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_exchanges_distinct_users CHECK (owner_uid <> requester_uid)
);

-- A user can only have one open exchange per book
CREATE UNIQUE INDEX uq_exchanges_open_request
    ON exchanges (book_id, requester_uid)
    WHERE status = 'PENDING';
CREATE INDEX idx_exchanges_owner ON exchanges (owner_uid);
CREATE INDEX idx_exchanges_requester ON exchanges (requester_uid);

CREATE TABLE ratings (
    id          BIGSERIAL PRIMARY KEY,
    rated_uid   VARCHAR(128) NOT NULL REFERENCES users (uid) ON DELETE CASCADE,
    rater_uid   VARCHAR(128) NOT NULL REFERENCES users (uid) ON DELETE CASCADE,
    score       SMALLINT     NOT NULL CHECK (score BETWEEN 1 AND 5),
    comment     VARCHAR(1000),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_ratings_pair UNIQUE (rated_uid, rater_uid),
    CONSTRAINT chk_ratings_distinct_users CHECK (rated_uid <> rater_uid)
);
