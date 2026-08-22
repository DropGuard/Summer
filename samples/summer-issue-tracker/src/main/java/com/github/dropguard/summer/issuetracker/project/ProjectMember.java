package com.github.dropguard.summer.issuetracker.project;

import com.github.dropguard.summer.data.jdbc.annotation.RowModel;

/**
 * Project-level membership — the backbone of the demo's RBAC. A user's effective permission on an
 * issue is resolved from their org role plus their {@code project_members.role} (MANAGER | MEMBER |
 * VIEWER).
 */
@RowModel(table = "project_members")
public record ProjectMember(Long projectId, Long userId, String role) {}
