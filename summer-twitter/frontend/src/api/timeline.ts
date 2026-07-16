import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/lib/fetch';
import { useUserCacheStore } from '@/stores/userCacheStore';
import type { Tweet } from '@/lib/types';

function enrichTweets(tweets: Tweet[]): Tweet[] {
  const store = useUserCacheStore.getState();
  return tweets.map((t) => store.enrichTweet(t));
}

interface TimelineParams {
  cursor?: number | null;
  limit?: number;
}

export function useTimeline(params: TimelineParams) {
  const searchParams = new URLSearchParams();
  if (params.cursor) searchParams.set('cursor', String(params.cursor));
  if (params.limit) searchParams.set('limit', String(params.limit));

  return useQuery({
    queryKey: ['timeline', params],
    queryFn: () => apiFetch<Tweet[]>(`/api/timeline?${searchParams.toString()}`).then(enrichTweets),
  });
}
