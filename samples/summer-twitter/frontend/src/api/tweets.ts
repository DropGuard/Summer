import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/lib/fetch';
import { useToastStore } from '@/stores/toastStore';
import { useUserCacheStore } from '@/stores/userCacheStore';
import type { Tweet } from '@/lib/types';

function enrich(t: Tweet): Tweet {
  return useUserCacheStore.getState().enrichTweet(t);
}

function enrichAll(ts: Tweet[]): Tweet[] {
  const store = useUserCacheStore.getState();
  return ts.map((t) => store.enrichTweet(t));
}

// ---- Reads ----

export function useTweet(id: number) {
  return useQuery({
    queryKey: ['tweet', id],
    queryFn: () => apiFetch<Tweet>(`/api/tweets/${id}`).then(enrich),
    enabled: !!id,
  });
}

export function useReplies(id: number, cursor?: number | null) {
  const searchParams = new URLSearchParams();
  if (cursor) searchParams.set('cursor', String(cursor));
  if (!cursor) searchParams.set('limit', '20');

  return useQuery({
    queryKey: ['tweet', id, 'replies', cursor],
    queryFn: () =>
      apiFetch<Tweet[]>(`/api/tweets/${id}/replies?${searchParams.toString()}`).then(enrichAll),
    enabled: !!id,
  });
}

// ---- Mutations ----

interface CreateTweetInput {
  content: string;
  parentId?: number | null;
}

export function useCreateTweet() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: CreateTweetInput) =>
      apiFetch<Tweet>('/api/tweets', {
        method: 'POST',
        body: JSON.stringify(input),
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['timeline'] });
      useToastStore.getState().show('Tweet posted!', 'success');
    },
    onError: () => {
      useToastStore.getState().show('Failed to post', 'error');
    },
  });
}

export function useDeleteTweet() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => apiFetch<void>(`/api/tweets/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['timeline'] });
      useToastStore.getState().show('Tweet deleted', 'success');
    },
    onError: () => {
      useToastStore.getState().show('Failed to delete', 'error');
    },
  });
}

export function useLike() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (tweetId: number) =>
      apiFetch<void>(`/api/tweets/${tweetId}/like`, { method: 'POST' }),
    onSuccess: (_data, tweetId) => {
      qc.invalidateQueries({ queryKey: ['tweet', tweetId] });
      qc.invalidateQueries({ queryKey: ['timeline'] });
    },
    onError: () => {
      useToastStore.getState().show('Failed to like', 'error');
    },
  });
}

export function useUnlike() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (tweetId: number) =>
      apiFetch<void>(`/api/tweets/${tweetId}/like`, { method: 'DELETE' }),
    onSuccess: (_data, tweetId) => {
      qc.invalidateQueries({ queryKey: ['tweet', tweetId] });
      qc.invalidateQueries({ queryKey: ['timeline'] });
    },
    onError: () => {
      useToastStore.getState().show('Failed to unlike', 'error');
    },
  });
}

export function useRetweet() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (tweetId: number) =>
      apiFetch<Tweet>(`/api/tweets/${tweetId}/retweet`, { method: 'POST' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['timeline'] });
      useToastStore.getState().show('Retweeted!', 'success');
    },
    onError: () => {
      useToastStore.getState().show('Failed to retweet', 'error');
    },
  });
}

export function useQuoteTweet() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ tweetId, content }: { tweetId: number; content: string }) =>
      apiFetch<Tweet>(`/api/tweets/${tweetId}/quote`, {
        method: 'POST',
        body: JSON.stringify({ content }),
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['timeline'] });
      useToastStore.getState().show('Quoted!', 'success');
    },
    onError: () => {
      useToastStore.getState().show('Failed to quote', 'error');
    },
  });
}
