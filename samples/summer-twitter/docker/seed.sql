-- 种子数据：演示推文、用户、关注、点赞、私信

-- 1. 创建用户 (密码统一为 "password", Hash: $2a$10$vI8aWBnW3fID.021/sOWa.VD5.MccD5QTE.Y3B2bU6KzZ1x.Y317O)
INSERT INTO users (id, username, display_name, email, password_hash, bio, follower_count, following_count, created_at) VALUES
(1001, 'elonmusk', 'Elon Musk', 'elon@example.com', '$2a$10$vI8aWBnW3fID.021/sOWa.VD5.MccD5QTE.Y3B2bU6KzZ1x.Y317O', 'Mars & Cars', 6000, 1, NOW() - INTERVAL '10 days'),
(1002, 'zuck', 'Mark Zuckerberg', 'zuck@example.com', '$2a$10$vI8aWBnW3fID.021/sOWa.VD5.MccD5QTE.Y3B2bU6KzZ1x.Y317O', 'Building the Metaverse', 3, 2, NOW() - INTERVAL '9 days'),
(1003, 'billgates', 'Bill Gates', 'bill@example.com', '$2a$10$vI8aWBnW3fID.021/sOWa.VD5.MccD5QTE.Y3B2bU6KzZ1x.Y317O', 'Philanthropist', 2, 2, NOW() - INTERVAL '8 days'),
(1004, 'karpathy', 'Andrej Karpathy', 'andrej@example.com', '$2a$10$vI8aWBnW3fID.021/sOWa.VD5.MccD5QTE.Y3B2bU6KzZ1x.Y317O', 'AI', 1, 3, NOW() - INTERVAL '7 days');

-- 2. 创建关注关系 (Follow)
-- follower_count 是冗余计数、手填的；Elon 故意设 6000 以触发大V阈值(5000)分支。
INSERT INTO follows (id, follower_id, following_id, created_at) VALUES
(2001, 1002, 1001, NOW() - INTERVAL '6 days'), -- Zuck 关注 Elon
(2002, 1003, 1001, NOW() - INTERVAL '5 days'), -- Bill 关注 Elon
(2003, 1004, 1001, NOW() - INTERVAL '5 days'), -- Andrej 关注 Elon
(2004, 1001, 1004, NOW() - INTERVAL '4 days'), -- Elon 关注 Andrej
(2005, 1004, 1002, NOW() - INTERVAL '4 days'), -- Andrej 关注 Zuck
(2006, 1003, 1002, NOW() - INTERVAL '3 days'), -- Bill 关注 Zuck
(2007, 1002, 1003, NOW() - INTERVAL '2 days'), -- Zuck 关注 Bill
(2008, 1004, 1003, NOW() - INTERVAL '2 days'); -- Andrej 关注 Bill

-- 3. 创建推文与回复 (Tweet)
-- 推文 1: Bill Gates 的名场面
INSERT INTO tweets (id, author_id, content, parent_id, like_count, reply_count, created_at) VALUES
(3001, 1003, 'What does the "vibe" in vibe coding actually mean? Keep seeing it everywhere.', NULL, 0, 1, NOW() - INTERVAL '5 hours');

-- 推文 2: Andrej 的神回复 (parent_id = 3001)
INSERT INTO tweets (id, author_id, content, parent_id, like_count, reply_count, created_at) VALUES
(3002, 1004, '@billgates very inefficient but entertaining', 3001, 3, 0, NOW() - INTERVAL '4 hours');

-- 推文 3: Elon 搞事情
INSERT INTO tweets (id, author_id, content, parent_id, like_count, reply_count, created_at) VALUES
(3003, 1001, 'Just rewrote Twitter in Summer Framework. Compile time is 0.01s. Let that sink in.', NULL, 3, 1, NOW() - INTERVAL '3 hours');

-- 推文 4: Zuck 怼 Elon (parent_id = 3003)
INSERT INTO tweets (id, author_id, content, parent_id, like_count, reply_count, created_at) VALUES
(3004, 1002, '@elonmusk But does it scale? We are building Threads on it right now.', 3003, 1, 0, NOW() - INTERVAL '2 hours');

-- 4. 创建点赞 (Like)
INSERT INTO likes (id, user_id, tweet_id, created_at) VALUES
(4001, 1001, 3002, NOW() - INTERVAL '3 hours'), -- Elon likes Andrej's reply
(4002, 1002, 3002, NOW() - INTERVAL '2 hours'), -- Zuck likes Andrej's reply
(4003, 1003, 3002, NOW() - INTERVAL '1 hours'), -- Bill likes Andrej's reply
(4004, 1002, 3003, NOW() - INTERVAL '2 hours'), -- Zuck likes Elon's tweet
(4005, 1004, 3003, NOW() - INTERVAL '2 hours'), -- Andrej likes Elon's tweet
(4006, 1003, 3003, NOW() - INTERVAL '1 hours'); -- Bill likes Elon's tweet

-- 5. 创建会话 (Conversation)
-- Elon (1001) 和 Zuck (1002) 的秘密私信
INSERT INTO conversations (id, user_one_id, user_two_id, last_message_at, created_at) VALUES
(5001, 1001, 1002, NOW() - INTERVAL '1 hour', NOW() - INTERVAL '2 days');

-- 6. 创建私信消息 (DirectMessage)
INSERT INTO direct_messages (id, sender_id, receiver_id, text, read_at, created_at) VALUES
(6001, 1001, 1002, 'Cage match?', NOW() - INTERVAL '1.5 hours', NOW() - INTERVAL '2 hours'),
(6002, 1002, 1001, 'Send me location.', NULL, NOW() - INTERVAL '1 hour');
