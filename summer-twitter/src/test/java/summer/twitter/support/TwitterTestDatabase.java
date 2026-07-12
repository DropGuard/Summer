package summer.twitter.support;

import java.sql.Connection;
import java.sql.Statement;

/**
 * Holds the schema DDL and seed DML used by integration tests.
 * Mirrors docker/init/01-schema.sql and docker/seed.sql with
 * ON CONFLICT DO NOTHING for idempotent re-runs.
 */
public final class TwitterTestDatabase {

    private TwitterTestDatabase() {}

    /**
     * Initializes schema from the inline SQL.
     * Idempotent: uses CREATE TABLE IF NOT EXISTS.
     */
    public static void initSchema(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(SCHEMA_SQL);
        }
    }

    /**
     * Resets all application tables to seed state.
     * Uses TRUNCATE ... CASCADE followed by seed INSERTs.
     */
    public static void resetToSeed(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE direct_messages, conversations, likes, follows, tweets, users CASCADE");
            stmt.execute(SEED_SQL);
        }
    }

    // ── Schema DDL (mirrors docker/init/01-schema.sql) ───────────────

    public static final String SCHEMA_SQL = """
        CREATE TABLE IF NOT EXISTS users (
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

        CREATE TABLE IF NOT EXISTS tweets (
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

        CREATE TABLE IF NOT EXISTS follows (
            id BIGINT PRIMARY KEY,
            follower_id BIGINT NOT NULL REFERENCES users(id),
            following_id BIGINT NOT NULL REFERENCES users(id),
            created_at TIMESTAMPTZ NOT NULL,
            UNIQUE (follower_id, following_id)
        );

        CREATE TABLE IF NOT EXISTS likes (
            id BIGINT PRIMARY KEY,
            user_id BIGINT NOT NULL REFERENCES users(id),
            tweet_id BIGINT NOT NULL REFERENCES tweets(id),
            created_at TIMESTAMPTZ NOT NULL,
            UNIQUE (user_id, tweet_id)
        );

        CREATE TABLE IF NOT EXISTS direct_messages (
            id BIGINT PRIMARY KEY,
            sender_id BIGINT NOT NULL REFERENCES users(id),
            receiver_id BIGINT NOT NULL REFERENCES users(id),
            text VARCHAR(1000) NOT NULL,
            read_at TIMESTAMPTZ,
            created_at TIMESTAMPTZ NOT NULL
        );

        CREATE TABLE IF NOT EXISTS conversations (
            id BIGINT PRIMARY KEY,
            user_one_id BIGINT NOT NULL REFERENCES users(id),
            user_two_id BIGINT NOT NULL REFERENCES users(id),
            last_message_at TIMESTAMPTZ NOT NULL,
            created_at TIMESTAMPTZ NOT NULL,
            UNIQUE (user_one_id, user_two_id),
            CHECK (user_one_id < user_two_id)
        );
        """;

    // ── Seed DML (mirrors docker/seed.sql, with ON CONFLICT DO NOTHING) ──

    public static final String SEED_SQL = """
        INSERT INTO users (id, username, display_name, email, password_hash, bio, follower_count, following_count, created_at) VALUES
        (1001, 'elonmusk', 'Elon Musk', 'elon@example.com', '$2a$10$vI8aWBnW3fID.021/sOWa.VD5.MccD5QTE.Y3B2bU6KzZ1x.Y317O', 'Mars & Cars', 6000, 1, NOW() - INTERVAL '10 days'),
        (1002, 'zuck', 'Mark Zuckerberg', 'zuck@example.com', '$2a$10$vI8aWBnW3fID.021/sOWa.VD5.MccD5QTE.Y3B2bU6KzZ1x.Y317O', 'Building the Metaverse', 3, 2, NOW() - INTERVAL '9 days'),
        (1003, 'billgates', 'Bill Gates', 'bill@example.com', '$2a$10$vI8aWBnW3fID.021/sOWa.VD5.MccD5QTE.Y3B2bU6KzZ1x.Y317O', 'Philanthropist', 2, 2, NOW() - INTERVAL '8 days'),
        (1004, 'karpathy', 'Andrej Karpathy', 'andrej@example.com', '$2a$10$vI8aWBnW3fID.021/sOWa.VD5.MccD5QTE.Y3B2bU6KzZ1x.Y317O', 'AI', 1, 3, NOW() - INTERVAL '7 days')
        ON CONFLICT (id) DO NOTHING;

        INSERT INTO tweets (id, author_id, content, parent_id, like_count, reply_count, created_at) VALUES
        (3001, 1003, 'What does the "vibe" in vibe coding actually mean? Keep seeing it everywhere.', NULL, 2, 1, NOW() - INTERVAL '5 hours'),
        (3002, 1004, '@billgates very inefficient but entertaining', 3001, 3, 0, NOW() - INTERVAL '4 hours'),
        (3003, 1001, 'Just rewrote Twitter in Summer Framework. Compile time is 0.01s. Let that sink in.', NULL, 4, 1, NOW() - INTERVAL '3 hours'),
        (3004, 1002, '@elonmusk But does it scale? We are building Threads on it right now.', 3003, 1, 0, NOW() - INTERVAL '2 hours')
        ON CONFLICT (id) DO NOTHING;

        INSERT INTO likes (id, user_id, tweet_id, created_at) VALUES
        (4001, 1001, 3002, NOW() - INTERVAL '3 hours'),
        (4002, 1002, 3002, NOW() - INTERVAL '2 hours'),
        (4003, 1003, 3002, NOW() - INTERVAL '1 hours'),
        (4004, 1002, 3003, NOW() - INTERVAL '2 hours'),
        (4005, 1004, 3003, NOW() - INTERVAL '2 hours'),
        (4006, 1003, 3003, NOW() - INTERVAL '1 hours')
        ON CONFLICT (id) DO NOTHING;

        INSERT INTO follows (id, follower_id, following_id, created_at) VALUES
        (2001, 1002, 1001, NOW() - INTERVAL '6 days'),
        (2002, 1003, 1001, NOW() - INTERVAL '5 days'),
        (2003, 1004, 1001, NOW() - INTERVAL '5 days'),
        (2004, 1001, 1004, NOW() - INTERVAL '4 days'),
        (2005, 1004, 1002, NOW() - INTERVAL '4 days'),
        (2006, 1003, 1002, NOW() - INTERVAL '3 days'),
        (2007, 1002, 1003, NOW() - INTERVAL '2 days'),
        (2008, 1004, 1003, NOW() - INTERVAL '2 days')
        ON CONFLICT (id) DO NOTHING;

        INSERT INTO conversations (id, user_one_id, user_two_id, last_message_at, created_at) VALUES
        (5001, 1001, 1002, NOW() - INTERVAL '1 hour', NOW() - INTERVAL '2 days')
        ON CONFLICT (id) DO NOTHING;

        INSERT INTO direct_messages (id, sender_id, receiver_id, text, read_at, created_at) VALUES
        (6001, 1001, 1002, 'Cage match?', NOW() - INTERVAL '1.5 hours', NOW() - INTERVAL '2 hours'),
        (6002, 1002, 1001, 'Send me location.', NULL, NOW() - INTERVAL '1 hour')
        ON CONFLICT (id) DO NOTHING;
        """;
}
