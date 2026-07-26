import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/lib/fetch';
import type { Issue, IssueDetail, IssueFilter } from '@/lib/types';

export function useIssues(projectId: number, filter: IssueFilter) {
  const params = new URLSearchParams();
  if (filter.assigneeId != null) params.set('assigneeId', String(filter.assigneeId));
  if (filter.status) params.set('status', filter.status);
  if (filter.priority) params.set('priority', filter.priority);
  if (filter.reporterId != null) params.set('reporterId', String(filter.reporterId));
  if (filter.title) params.set('title', filter.title);
  if (filter.tagId != null) params.set('tagId', String(filter.tagId));
  const qs = params.toString();
  return useQuery({
    queryKey: ['issues', projectId, filter],
    queryFn: () =>
      apiFetch<Issue[]>(`/api/projects/${projectId}/issues${qs ? `?${qs}` : ''}`),
    enabled: !!projectId,
  });
}

export function useIssueDetail(issueId: number) {
  return useQuery({
    queryKey: ['issue', issueId],
    queryFn: () => apiFetch<IssueDetail>(`/api/issues/${issueId}`),
    enabled: !!issueId,
  });
}

export function useCreateIssue(projectId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: {
      title: string;
      description: string;
      status: string;
      priority: string;
      assigneeId: number | null;
    }) =>
      apiFetch<Issue>(`/api/projects/${projectId}/issues`, {
        method: 'POST',
        body: JSON.stringify(input),
      }),
    onSuccess: (_data, variables) =>
      qc.invalidateQueries({ queryKey: ['issues', projectId, variables] }),
  });
}

export function useUpdateStatus() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, status }: { id: number; status: string }) =>
      apiFetch<Issue>(`/api/issues/${id}/status`, {
        method: 'PUT',
        body: JSON.stringify({ status }),
      }),
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: ['issues', data.projectId] });
      qc.invalidateQueries({ queryKey: ['issue', data.id] });
    },
  });
}

export function useAssign() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, assigneeId }: { id: number; assigneeId: number | null }) =>
      apiFetch<Issue>(`/api/issues/${id}/assign`, {
        method: 'PUT',
        body: JSON.stringify({ assigneeId }),
      }),
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: ['issues', data.projectId] });
      qc.invalidateQueries({ queryKey: ['issue', data.id] });
    },
  });
}

export function useChangePriority() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, priority }: { id: number; priority: string }) =>
      apiFetch<Issue>(`/api/issues/${id}/priority`, {
        method: 'PUT',
        body: JSON.stringify({ priority }),
      }),
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: ['issues', data.projectId] });
      qc.invalidateQueries({ queryKey: ['issue', data.id] });
    },
  });
}

export function useDeleteIssue() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) =>
      apiFetch<void>(`/api/issues/${id}`, { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['issues'] }),
  });
}

export function useAddComment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: string }) =>
      apiFetch<void>(`/api/issues/${id}/comments`, {
        method: 'POST',
        body: JSON.stringify({ body }),
      }),
    onSuccess: (_data, variables) =>
      qc.invalidateQueries({ queryKey: ['issue', variables.id] }),
  });
}

export function useIssueTags(id: number) {
  return useQuery({
    queryKey: ['issue-tags', id],
    queryFn: () => apiFetch<{ id: number; orgId: number; name: string; color: string }[]>(
      `/api/issues/${id}/tags`,
    ),
    enabled: !!id,
  });
}

export function useAttachTag(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (tagId: number) =>
      apiFetch<void>(`/api/issues/${id}/tags/${tagId}`, { method: 'POST' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['issue-tags', id] }),
  });
}

export function useDetachTag(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (tagId: number) =>
      apiFetch<void>(`/api/issues/${id}/tags/${tagId}`, { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['issue-tags', id] }),
  });
}

export function useIssueHistory(id: number) {
  return useQuery({
    queryKey: ['issue-history', id],
    queryFn: () =>
      apiFetch<
        {
          id: number;
          issueId: number;
          actorId: number;
          action: string;
          fromValue: string | null;
          toValue: string | null;
          createdAt: string;
        }[]
      >(`/api/issues/${id}/history`),
    enabled: !!id,
  });
}
