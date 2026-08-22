import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/lib/fetch';
import type { Project, ProjectMember } from '@/lib/types';

export function useMyProjects() {
  return useQuery({
    queryKey: ['projects'],
    queryFn: () => apiFetch<Project[]>('/api/projects'),
  });
}

export function useProject(id: number) {
  return useQuery({
    queryKey: ['project', id],
    queryFn: () => apiFetch<Project>(`/api/projects/${id}`),
    enabled: !!id,
  });
}

export function useCreateProject() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { projectKey: string; name: string }) =>
      apiFetch<Project>('/api/projects', { method: 'POST', body: JSON.stringify(input) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['projects'] }),
  });
}

export function useProjectMembers(id: number) {
  return useQuery({
    queryKey: ['project-members', id],
    queryFn: () => apiFetch<ProjectMember[]>(`/api/projects/${id}/members`),
    enabled: !!id,
  });
}

export function useAddMember(projectId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { userId: number; role: string }) =>
      apiFetch<void>(`/api/projects/${projectId}/members`, {
        method: 'POST',
        body: JSON.stringify(input),
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['project-members', projectId] }),
  });
}
