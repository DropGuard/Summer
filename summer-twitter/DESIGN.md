# Summer Twitter — 设计蓝图

> 本文档是 `summer-twitter` 模块的完整架构设计，基于 23 条经过逐一审定的决策。实施阶段以本文档为唯一权威参考。

## 1. 项目定位

Summer 框架的终极 Showcase 应用。一个类 Twitter 微型社交平台，完整展示框架的 HTTP、WebSocket、Redis、JDBC、AOP、DI、AOT 双引擎能力。

**约束：**
- 纯文字内容，280 字符限制，不做媒体托管
- 不做转发（Retweet）功能

## 2. 基础设施

| 组件 | 选型 | 说明 |
|------|------|------|
| 关系型数据库 | PostgreSQL | Docker 容器，真实生产级 |
| 缓存/Timeline | Redis | Docker 容器，Sorted Set 承载 Timeline |
| 容器编排 | Docker Compose | `summer-twitter/docker/docker-compose.yml` |
| ID 生成 | Snowflake (Long) | 64-bit，时间有序，全局唯一 |

## 3. 信息流架构：混合推拉模型（Hybrid Fan-out）

### 写入路径（发推）

```
用户发推
  → 存入 PostgreSQL (tweets 表)
  → 判断作者 followerCount
     ├── < 5000（普通用户）→ Fan-out-on-write
     │     Thread.startVirtualThread(() -> {
     │         查询所有粉丝 ID
     │         批量 ZADD timeline:{followerId} <timestamp> <tweetId>
     │     })
     └── >= 5000（大 V）→ 跳过扇出，推文仅存在作者的发帖列表中
  → 写入时正则解析 @mention（@(\w+)），不存在的用户静默忽略
  → 通过 WebSocket /ws/events 推送 new_tweet / mentioned 通知给在线用户
```

- Fan-out 使用 `Thread.startVirtualThread()`，fire-and-forget，不做重试
- 大 V 阈值：5000 粉丝（硬编码常量）

### 读取路径（刷 Timeline）

```
GET /api/timeline?cursor=xxx&limit=20
  → 从 Redis timeline:{userId} 取候选推文 ID（ZREVRANGEBYSCORE，按时间戳倒序）
  → 查出"我关注的大 V 列表"
  → 分别拉取每个大 V 的最近 N 条推文 ID
  → 合并去重
  → 批量查 PostgreSQL 获取推文详情（WHERE id IN (...)）
  → 应用层 HN 公式算分排序
  → 返回 Top 20
```

### 排名算法：Hacker News 公式

```
score = (points - 1) / (T + 2) ^ gravity

points  = like_count + reply_count
T       = 发布至今的小时数
gravity = 1.8
```

在应用层实时计算，不缓存分数。

## 4. Redis 数据结构

```
timeline:{userId}       → Sorted Set (member=tweetId, score=createdAt timestamp)
user:{userId}:tweets    → Sorted Set (member=tweetId, score=createdAt timestamp)
```

## 5. 实体设计（PostgreSQL）

所有实体使用 `@RowModel` record，ID 为 Snowflake Long。

### users
| 字段 | 类型 | 约束 |
|------|------|------|
| id | BIGINT | PK, Snowflake |
| username | VARCHAR(32) | UNIQUE, NOT NULL |
| display_name | VARCHAR(64) | NOT NULL |
| email | VARCHAR(255) | UNIQUE, NOT NULL |
| password_hash | VARCHAR(255) | NOT NULL |
| bio | VARCHAR(280) | NULLABLE |
| follower_count | INT | DEFAULT 0 |
| following_count | INT | DEFAULT 0 |
| created_at | TIMESTAMPTZ | NOT NULL |

### tweets
| 字段 | 类型 | 约束 |
|------|------|------|
| id | BIGINT | PK, Snowflake |
| author_id | BIGINT | FK → users, NOT NULL |
| content | VARCHAR(280) | NOT NULL |
| parent_id | BIGINT | FK → tweets, NULLABLE（永远指向顶层原推）|
| like_count | INT | DEFAULT 0 |
| reply_count | INT | DEFAULT 0（仅顶层推文有意义）|
| created_at | TIMESTAMPTZ | NOT NULL |

### follows
| 字段 | 类型 | 约束 |
|------|------|------|
| id | BIGINT | PK, Snowflake |
| follower_id | BIGINT | FK → users, NOT NULL |
| following_id | BIGINT | FK → users, NOT NULL |
| created_at | TIMESTAMPTZ | NOT NULL |
| | | UNIQUE(follower_id, following_id) |

### likes
| 字段 | 类型 | 约束 |
|------|------|------|
| id | BIGINT | PK, Snowflake |
| user_id | BIGINT | FK → users, NOT NULL |
| tweet_id | BIGINT | FK → tweets, NOT NULL |
| created_at | TIMESTAMPTZ | NOT NULL |
| | | UNIQUE(user_id, tweet_id) |

### direct_messages
| 字段 | 类型 | 约束 |
|------|------|------|
| id | BIGINT | PK, Snowflake |
| sender_id | BIGINT | FK → users, NOT NULL |
| receiver_id | BIGINT | FK → users, NOT NULL |
| text | VARCHAR(1000) | NOT NULL |
| read_at | TIMESTAMPTZ | NULLABLE（null = 未读）|
| created_at | TIMESTAMPTZ | NOT NULL |

### conversations
| 字段 | 类型 | 约束 |
|------|------|------|
| id | BIGINT | PK, Snowflake |
| user_one_id | BIGINT | FK → users, NOT NULL（较小 ID）|
| user_two_id | BIGINT | FK → users, NOT NULL（较大 ID）|
| last_message_at | TIMESTAMPTZ | NOT NULL |
| created_at | TIMESTAMPTZ | NOT NULL |
| | | UNIQUE(user_one_id, user_two_id) |

未读数通过查询实时计算：`SELECT COUNT(*) FROM direct_messages WHERE receiver_id=? AND sender_id=? AND read_at IS NULL`

## 6. 认证

- **HTTP**：`Authorization: Bearer <JWT>`
- **WebSocket**：握手阶段通过 `Sec-WebSocket-Protocol` 头传递 JWT Token

## 7. REST API

### 认证
```
POST   /api/auth/register              注册
POST   /api/auth/login                 登录 → 返回 JWT
```

### 用户
```
GET    /api/users/:username            查看用户资料
PUT    /api/users/me                   修改自己的资料
```

### 推文
```
POST   /api/tweets                     发推（body: content, parentId?）
GET    /api/tweets/:id                 查看单条推文
DELETE /api/tweets/:id                 删除自己的推文
GET    /api/tweets/:id/replies         获取推文的回复列表（cursor 分页）
```

### Timeline
```
GET    /api/timeline                   我的信息流（混合推拉 + HN 排序）
GET    /api/users/:username/tweets     某用户的推文列表（cursor 分页）
```

### 社交
```
POST   /api/users/:username/follow     关注
DELETE /api/users/:username/follow     取消关注
GET    /api/users/:username/followers  粉丝列表（cursor 分页）
GET    /api/users/:username/following  关注列表（cursor 分页）
```

### 互动
```
POST   /api/tweets/:id/like           点赞
DELETE /api/tweets/:id/like           取消点赞
```

### 私信
```
GET    /api/dm/conversations                  会话列表（含最后一条消息、未读数）
GET    /api/dm/conversations/:username        与某用户的历史消息（cursor 分页）
```

所有列表接口使用 cursor-based 分页：`?cursor=<lastId>&limit=20`

## 8. WebSocket 协议

### `/ws/events` — 事件通知（服务端 → 客户端，单向）

```json
{"type": "new_tweet",    "tweetId": "1234567890", "authorUsername": "alice"}
{"type": "liked",        "tweetId": "1234567890", "byUsername": "bob"}
{"type": "mentioned",    "tweetId": "1234567890", "byUsername": "carol"}
{"type": "new_follower", "username": "dave"}
```

### `/ws/dm` — 私信通道（双向）

客户端 → 服务端：
```json
{"type": "send",      "to": "bob",   "text": "你好"}
{"type": "mark_read", "from": "alice"}
```

服务端 → 客户端：
```json
{"type": "receive",      "messageId": "9876543210", "from": "alice", "text": "你好", "timestamp": "2026-07-06T20:00:00Z"}
{"type": "read_receipt", "conversationWith": "alice", "readAt": "2026-07-06T20:01:00Z"}
```

## 9. 包结构（按功能切分）

```
summer.twitter
├── tweet/         TweetController, TweetService, TweetRepository, Tweet
├── user/          UserController, UserService, UserRepository, User
├── auth/          AuthController, AuthService, JwtUtil
├── timeline/      TimelineController, TimelineService
├── social/        FollowController, FollowService, FollowRepository, Follow, Like, LikeRepository
├── dm/            DmHandler, DmService, DmRepository, DirectMessage, Conversation
├── event/         EventsHandler, EventPublisher
├── config/        AppConfig, RedisConfig, DatabaseConfig
└── infra/         SnowflakeIdGenerator, HackerNewsScoring
```

## 10. 项目结构

```
summer-twitter/
├── docker/
│   ├── docker-compose.yml        PG + Redis
│   ├── init/
│   │   └── 01-schema.sql         DDL 建表（容器首次启动自动执行）
│   └── seed.sql                  种子数据（手动 make seed 灌入）
├── src/main/java/summer/twitter/ Java 源码
├── src/main/resources/
│   └── application.yml           配置（DB连接、Redis、JWT secret）
├── Makefile                      模块级命令（up/down/seed/run）
└── pom.xml
```

## 11. Makefile

```makefile
.PHONY: up down seed seed-clean run

up:
	docker-compose -f docker/docker-compose.yml up -d

down:
	docker-compose -f docker/docker-compose.yml down

seed:
	docker exec -i summer-pg psql -U summer summer < docker/seed.sql

seed-clean:
	docker exec -i summer-pg psql -U summer summer -c \
	  "TRUNCATE tweets, likes, follows, direct_messages, conversations, users CASCADE"

run:
	cd .. && mvn compile exec:java -pl summer-twitter -am
```

## 12. summer-twitter-admin

保留在仓库中作为参考代码，但从根 `pom.xml` 的 `<modules>` 中排除，不参与构建。待 `summer-twitter` 完工后可作为第二期演进。

## 13. 回复模型

- 回复是一条 `parent_id` 不为 null 的 Tweet
- `parent_id` **永远指向顶层原推**，不允许嵌套
- 对回复的回复，`parent_id` 仍指向顶层，通过 `@mention` 文本指代目标回复者
- `reply_count` 仅在顶层推文上递增
