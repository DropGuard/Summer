import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, ApiError } from '@/lib/fetch';
import { useToastStore } from '@/stores/toastStore';
import type { User } from '@/lib/types';

export function useUser(username: string) {
  return useQuery({
    queryKey: ['user', username],
    queryFn: () => apiFetch<User>(`/api/users/${username}`),
    enabled: !!username,
  });
}

export function useSearchUsers(query: string) {
  return useQuery({
    queryKey: ['userSearch', query],
    queryFn: async () => {
      try {
        return await apiFetch<User>(`/api/users/${encodeURIComponent(query)}`);
      } catch (e) {
        if (e instanceof ApiError && e.status === 404) {
          return null;
        }
        throw e;
      }
    },
    enabled: query.length > 0,
  });
}

interface UpdateProfileInput {
  displayName: string;
  bio: string;
}

export function useUpdateProfile() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: UpdateProfileInput) =>
      apiFetch<User>('/api/users/me', {
        method: 'PUT',
        body: JSON.stringify(input),
      }),
    onSuccess: (user) => {
      qc.invalidateQueries({ queryKey: ['user', user.username] });
      useToastStore.getState().show('Profile updated', 'success');
    },
    onError: () => {
      useToastStore.getState().show('Failed to update profile', 'error');
    },
  });
}

export function useFollow() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (username: string) =>
      apiFetch<void>(`/api/users/${username}/follow`, { method: 'POST' }),
    onSuccess: (_data, username) => {
      qc.invalidateQueries({ queryKey: ['user', username] });
      useToastStore.getState().show('Followed', 'success');
    },
    onError: () => {
      useToastStore.getState().show('Failed to follow', 'error');
    },
  });
}

export function useUnfollow() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (username: string) =>
      apiFetch<void>(`/api/users/${username}/follow`, { method: 'DELETE' }),
    onSuccess: (_data, username) => {
      qc.invalidateQueries({ queryKey: ['user', username] });
      useToastStore.getState().show('Unfollowed', 'success');
    },
    onError: () => {
      useToastStore.getState().show('Failed to unfollow', 'error');
    },
  });
}
