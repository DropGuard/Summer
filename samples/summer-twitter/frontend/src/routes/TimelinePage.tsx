import { useState, useRef, useEffect, useReducer } from 'react';
import type { Tweet } from '@/lib/types';
import { useTimeline } from '@/api/timeline';
import TweetCard from '@/components/TweetCard';
import ComposeTweet from '@/components/ComposeTweet';
import { TimelineSkeleton } from '@/components/Skeleton';

type Action = { type: 'append'; page: Tweet[]; cursor: number | null };

function tweetReducer(state: Tweet[], action: Action): Tweet[] {
  if (action.cursor === null) return action.page;
  const ids = new Set(state.map((t) => t.id));
  return [...state, ...action.page.filter((t) => !ids.has(t.id))];
}

export default function TimelinePage() {
  const [cursor, setCursor] = useState<number | null>(null);
  const { data, isLoading, isError, error, refetch } = useTimeline({ cursor, limit: 20 });
  const [allTweets, dispatch] = useReducer(tweetReducer, []);
  const sentinelRef = useRef<HTMLDivElement>(null);
  const allTweetsRef = useRef(allTweets);

  // Derive hasMore from data — avoids setState in effects (React 19 lint)
  const hasMore = data !== undefined && data.length >= 20;

  // Keep ref in sync for observer closure
  useEffect(() => {
    allTweetsRef.current = allTweets;
  }, [allTweets]);

  // Append page when data arrives — useReducer dispatch is stable and not flagged by lint
  useEffect(() => {
    if (data) dispatch({ type: 'append', page: data, cursor });
  }, [data, cursor]);

  // IntersectionObserver for infinite scroll
  useEffect(() => {
    if (!hasMore || isLoading) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting && hasMore && !isLoading) {
          const current = allTweetsRef.current;
          const last = current[current.length - 1];
          if (last) setCursor(last.id);
        }
      },
      { threshold: 0.1 },
    );
    if (sentinelRef.current) observer.observe(sentinelRef.current);
    return () => observer.disconnect();
  }, [hasMore, isLoading, allTweets.length]);

  if (isError) {
    return (
      <div>
        <div className="border-twitter-border sticky top-0 border-b bg-white/80 px-4 py-3 backdrop-blur">
          <h1 className="text-xl font-bold">Home</h1>
        </div>
        <div className="flex flex-col items-center gap-4 p-8 text-center">
          <p className="text-lg font-bold text-red-500">Something went wrong</p>
          <p className="text-twitter-gray text-sm">{error?.message}</p>
          <button
            onClick={() => refetch()}
            className="bg-twitter-blue hover:bg-twitter-blue-hover rounded-full px-6 py-2 font-bold text-white transition-colors"
          >
            Try again
          </button>
        </div>
      </div>
    );
  }

  return (
    <div>
      {/* Header */}
      <div className="border-twitter-border sticky top-0 border-b bg-white/80 px-4 py-3 backdrop-blur">
        <h1 className="text-xl font-bold">Home</h1>
      </div>

      <ComposeTweet />

      {isLoading && cursor === null ? (
        <TimelineSkeleton />
      ) : allTweets.length > 0 ? (
        <>
          {allTweets.map((tweet) => (
            <TweetCard key={tweet.id} tweet={tweet} />
          ))}
          {/* Loading spinner for more pages */}
          {isLoading && cursor !== null && (
            <div className="text-twitter-gray flex items-center justify-center gap-2 p-6 text-sm">
              <div className="border-twitter-blue h-5 w-5 animate-spin rounded-full border-2 border-t-transparent" />
              Loading more posts...
            </div>
          )}
          {/* Sentinel for IntersectionObserver */}
          {hasMore && <div ref={sentinelRef} className="h-4" />}
        </>
      ) : (
        <div className="text-twitter-gray p-8 text-center">
          <p className="text-lg font-bold">Welcome to Summer Twitter!</p>
          <p className="mt-2">Follow some users to see their tweets here.</p>
        </div>
      )}
    </div>
  );
}
