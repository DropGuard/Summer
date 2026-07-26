-- Summer Issue Tracker schema (PostgreSQL)
-- Single source of truth for the demo's storage model. Loaded by both the
-- application (via flyway-less manual init in DatabaseConfig) and the IT tests
-- (AbstractIssueTrackerIT.applySql).

CREATE TABLE organizations (
    id          BIGINT       PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    slug        VARCHAR(60)  NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id            BIGINT       PRIMARY KEY,
    org_id        BIGINT       NOT NULL REFERENCES organizations(id),
    username      VARCHAR(60)  NOT NULL UNIQUE,
    display_name  VARCHAR(120) NOT NULL,
    email         VARCHAR(200) NOT NULL,
    password_hash VARCHAR(200) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'MEMBER',  -- ADMIN | MANAGER | MEMBER | GUEST
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE projects (
    id           BIGINT       PRIMARY KEY,
    org_id       BIGINT       NOT NULL REFERENCES organizations(id),
    project_key  VARCHAR(10)  NOT NULL,
    name         VARCHAR(160) NOT NULL,
    lead_user_id BIGINT       NOT NULL REFERENCES users(id),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (org_id, project_key)
);

CREATE TABLE project_members (
    project_id BIGINT NOT NULL REFERENCES projects(id),
    user_id    BIGINT NOT NULL REFERENCES users(id),
    role       VARCHAR(20) NOT NULL DEFAULT 'MEMBER',  -- MANAGER | MEMBER | VIEWER
    PRIMARY KEY (project_id, user_id)
);

-- Monotonic per-project issue sequence. Allocated atomically (UPDATE ... RETURNING)
-- so concurrent issue creation never collides on issue_key. Not decremented on
-- delete, so keys are never reused — matching Jira/GitHub numbering.
CREATE TABLE project_counters (
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    issue_seq  BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (project_id)
);

CREATE TABLE issues (
    id           BIGINT       PRIMARY KEY,
    project_id   BIGINT       NOT NULL REFERENCES projects(id),
    issue_key    VARCHAR(20)  NOT NULL,                 -- e.g. PROJ-12
    title        VARCHAR(300) NOT NULL,
    description  TEXT,
    status       VARCHAR(20)  NOT NULL DEFAULT 'OPEN', -- OPEN | IN_PROGRESS | BLOCKED | DONE | CLOSED
    priority     VARCHAR(20)  NOT NULL DEFAULT 'MEDIUM',-- LOW | MEDIUM | HIGH | CRITICAL
    assignee_id  BIGINT       REFERENCES users(id),
    reporter_id  BIGINT       NOT NULL REFERENCES users(id),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (project_id, issue_key)
);

CREATE TABLE comments (
    id         BIGINT       PRIMARY KEY,
    issue_id   BIGINT       NOT NULL REFERENCES issues(id),
    author_id  BIGINT       NOT NULL REFERENCES users(id),
    body       TEXT         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE tags (
    id      BIGINT       PRIMARY KEY,
    org_id  BIGINT       NOT NULL REFERENCES organizations(id),
    name    VARCHAR(60)  NOT NULL,
    color   VARCHAR(7)   NOT NULL DEFAULT '#888888',
    UNIQUE (org_id, name)
);

CREATE TABLE issue_tags (
    issue_id BIGINT NOT NULL REFERENCES issues(id),
    tag_id   BIGINT NOT NULL REFERENCES tags(id),
    PRIMARY KEY (issue_id, tag_id)
);

-- Issue change history: a child resource of the issue (NOT a system audit log).
-- Scoped to the issue's lifetime — removed with the issue via ON DELETE CASCADE.
CREATE TABLE issue_history (
    id         BIGINT       PRIMARY KEY,
    issue_id   BIGINT       NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    actor_id   BIGINT       NOT NULL REFERENCES users(id),
    action     VARCHAR(40)  NOT NULL,   -- STATUS_CHANGED | ASSIGNEE_CHANGED | PRIORITY_CHANGED | CREATED | TITLE_CHANGED
    from_value VARCHAR(200),
    to_value   VARCHAR(200),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- System audit log: independent of any business entity (Jira-style). Records
-- "who did what to which object, when" for compliance. Deliberately has NO
-- foreign keys to live rows — it is self-contained (target identity is frozen
-- into target_type/target_id/target_key) so it survives deletion of the issue,
-- project, or member it describes. Never cascade-deleted.
CREATE TABLE audit_events (
    id          BIGINT       PRIMARY KEY,
    org_id      BIGINT       NOT NULL REFERENCES organizations(id),
    actor_id    BIGINT       NOT NULL REFERENCES users(id),
    action      VARCHAR(60)  NOT NULL,   -- ISSUE_CREATED | ISSUE_DELETED | MEMBER_ADDED | MEMBER_REMOVED | ...
    target_type VARCHAR(20)  NOT NULL,   -- ISSUE | PROJECT | USER
    target_id   BIGINT       NOT NULL,
    target_key  VARCHAR(60),             -- human-readable identity snapshot (e.g. PROJ-12), independent of live row
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_events_org           ON audit_events(org_id, occurred_at);
CREATE INDEX idx_audit_events_target        ON audit_events(target_type, target_id);

CREATE INDEX idx_issues_project_status      ON issues(project_id, status);
CREATE INDEX idx_issues_assignee_status     ON issues(assignee_id, status);
CREATE INDEX idx_issues_project_priority    ON issues(project_id, priority);
CREATE INDEX idx_comments_issue             ON comments(issue_id, created_at);
CREATE INDEX idx_issue_tags_tag              ON issue_tags(tag_id);
CREATE INDEX idx_issue_history_issue          ON issue_history(issue_id, created_at);
CREATE INDEX idx_project_members_user       ON project_members(user_id);
