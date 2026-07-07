CREATE TABLE users (
    id BIGINT PRIMARY KEY,
    username VARCHAR(32) UNIQUE NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    bio VARCHAR(280),
    follower_count INT DEFAULT 0,
    following_count INT DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE tweets (
    id BIGINT PRIMARY KEY,
    author_id BIGINT NOT NULL REFERENCES users(id),
    content VARCHAR(280) NOT NULL,
    type VARCHAR(16) NOT NULL DEFAULT 'POST',
    parent_id BIGINT REFERENCES tweets(id),
    like_count INT DEFAULT 0,
    reply_count INT DEFAULT 0,
    retweet_count INT DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE follows (
    id BIGINT PRIMARY KEY,
    follower_id BIGINT NOT NULL REFERENCES users(id),
    following_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (follower_id, following_id)
);

CREATE TABLE likes (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    tweet_id BIGINT NOT NULL REFERENCES tweets(id),
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (user_id, tweet_id)
);

CREATE TABLE direct_messages (
    id BIGINT PRIMARY KEY,
    sender_id BIGINT NOT NULL REFERENCES users(id),
    receiver_id BIGINT NOT NULL REFERENCES users(id),
    text VARCHAR(1000) NOT NULL,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE conversations (
    id BIGINT PRIMARY KEY,
    user_one_id BIGINT NOT NULL REFERENCES users(id),
    user_two_id BIGINT NOT NULL REFERENCES users(id),
    last_message_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (user_one_id, user_two_id),
    CHECK (user_one_id < user_two_id)
);
