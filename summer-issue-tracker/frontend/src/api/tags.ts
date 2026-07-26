import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/lib/fetch';
import type { Tag } from '@/lib/types';

export function useOrgTags(orgId: number) {
  return useQuery({
    queryKey: ['org-tags', orgId],
    queryFn: () => apiFetch<Tag[]>(`/api/orgs/${orgId}/tags`),
    enabled: !!orgId,
  });
}

export function useCreateTag(orgId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { name: string; color: string }) =>
      apiFetch<Tag>(`/api/orgs/${orgId}/tags`, {
        method: 'POST',
        body: JSON.stringify(input),
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['org-tags', orgId] }),
  });
}
