export interface TokenResponse {
  userId: number;
  username: string;
  token: string;
}

export interface Project {
  id: number;
  orgId: number;
  projectKey: string;
  name: string;
  leadUserId: number;
  createdAt: string;
}

export interface ProjectMember {
  projectId: number;
  userId: number;
  role: string;
}

export interface Issue {
  id: number;
  projectId: number;
  issueKey: string;
  title: string;
  description: string | null;
  status: string;
  priority: string;
  assigneeId: number | null;
  reporterId: number;
  createdAt: string;
  updatedAt: string;
}

export interface Tag {
  id: number;
  orgId: number;
  name: string;
  color: string;
}

export interface Comment {
  id: number;
  issueId: number;
  authorId: number;
  body: string;
  createdAt: string;
}

export interface AuditLog {
  id: number;
  issueId: number;
  actorId: number;
  action: string;
  fromValue: string | null;
  toValue: string | null;
  createdAt: string;
}

export interface IssueDetail {
  issue: Issue;
  tags: Tag[];
  comments: Comment[];
  history: AuditLog[];
  commentCount: number;
  assigneeName: string | null;
  reporterName: string | null;
}

export type IssueFilter = {
  assigneeId?: number;
  status?: string;
  priority?: string;
  reporterId?: number;
  title?: string;
  tagId?: number;
};

export const STATUSES = ['OPEN', 'IN_PROGRESS', 'BLOCKED', 'DONE', 'CLOSED'] as const;
export const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'] as const;

export const STATUS_COLORS: Record<string, string> = {
  OPEN: 'bg-slate-100 text-slate-700',
  IN_PROGRESS: 'bg-blue-100 text-blue-700',
  BLOCKED: 'bg-red-100 text-red-700',
  DONE: 'bg-green-100 text-green-700',
  CLOSED: 'bg-slate-200 text-slate-500',
};

export const PRIORITY_COLORS: Record<string, string> = {
  LOW: 'text-slate-500',
  MEDIUM: 'text-sky-600',
  HIGH: 'text-orange-600',
  CRITICAL: 'text-red-600',
};
