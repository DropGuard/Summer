import { useState } from 'react';
import { Link } from 'react-router-dom';
import type { Tweet } from '@/lib/types';
import { useLike, useUnlike, useRetweet, useQuoteTweet } from '@/api/tweets';
import { useLikeStore } from '@/stores/likeStore';
import { useBookmarkStore } from '@/stores/bookmarkStore';

function renderContent(content: string) {
  if (!content) return null;
  const parts = content.split(/(@\w+)/g);
  return (
    <>
      {parts.map((part, i) => {
        const match = part.match(/^@(\w+)$/);
        if (match) {
          return (
            <Link key={i} to={`/${match[1]}`} className="text-twitter-blue hover:underline">
              {part}
            </Link>
          );
        }
        return <span key={i}>{part}</span>;
      })}
    </>
  );
}

interface TweetCardProps {
  tweet: Tweet;
  showReplyBtn?: boolean;
}

export default function TweetCard({ tweet, showReplyBtn = true }: TweetCardProps) {
  const likeApi = useLike();
  const unlikeApi = useUnlike();
  const {
    isLiked,
    isPending,
    add: addLiked,
    remove: removeLiked,
    addPending,
    removePending,
  } = useLikeStore();
  const { isBookmarked, add: addBookmark, remove: removeBookmark } = useBookmarkStore();
  const liked = isLiked(tweet.id);
  const pending = isPending(tweet.id);
  const bookmarked = isBookmarked(tweet.id);
  const [showQuoteInput, setShowQuoteInput] = useState(false);
  const [quoteContent, setQuoteContent] = useState('');
  const retweetApi = useRetweet();
  const quoteApi = useQuoteTweet();

  const displayName = tweet.authorDisplayName ?? `User ${tweet.authorId}`;
  const username = tweet.authorUsername ?? String(tweet.authorId);
  const initial = (tweet.authorDisplayName ?? '?')[0]?.toUpperCase() ?? '?';

  const timeAgo = formatTimeAgo(tweet.createdAt);

  const handleLike = () => {
    if (pending) return;
    addPending(tweet.id);

    if (liked) {
      unlikeApi.mutate(tweet.id, {
        onSuccess: () => {
          removeLiked(tweet.id);
          removePending(tweet.id);
        },
        onError: () => removePending(tweet.id),
      });
    } else {
      likeApi.mutate(tweet.id, {
        onSuccess: () => {
          addLiked(tweet.id);
          removePending(tweet.id);
        },
        onError: () => removePending(tweet.id),
      });
    }
  };

  const handleRetweet = () => {
    if (retweetApi.isPending) return;
    retweetApi.mutate(tweet.id);
  };

  const handleQuote = () => {
    setShowQuoteInput(true);
  };

  const handleQuoteSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!quoteContent.trim() || quoteApi.isPending) return;
    quoteApi.mutate(
      { tweetId: tweet.id, content: quoteContent.trim() },
      {
        onSuccess: () => {
          setShowQuoteInput(false);
          setQuoteContent('');
        },
      },
    );
  };

  const handleQuoteCancel = () => {
    setShowQuoteInput(false);
    setQuoteContent('');
  };

  const handleBookmark = () => {
    if (bookmarked) {
      removeBookmark(tweet.id);
    } else {
      addBookmark({
        tweetId: tweet.id,
        authorName: displayName,
        content: tweet.content,
        createdAt: tweet.createdAt,
        savedAt: Date.now(),
      });
    }
  };

  return (
    <article className="border-twitter-border border-b px-4 py-3 transition-colors hover:bg-gray-50/80">
      <div className="flex gap-3">
        {/* Avatar */}
        <Link
          to={`/${username}`}
          className="bg-twitter-blue flex h-10 w-10 shrink-0 items-center justify-center rounded-full font-bold text-white"
        >
          {initial}
        </Link>

        <div className="min-w-0 flex-1">
          {/* Header */}
          <div className="flex items-center gap-1 text-sm">
            <Link to={`/${username}`} className="font-bold hover:underline">
              {displayName}
            </Link>
            <span className="text-twitter-gray truncate">
              @{username} · {timeAgo}
            </span>
          </div>

          {/* Type label */}
          {tweet.type === 'RETWEET' && (
            <div className="text-twitter-gray mt-1 flex items-center gap-1 text-xs">
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth={2}
                className="h-4 w-4"
              >
                <path d="M17 2l4 4-4 4" />
                <path d="M3 11v-1a4 4 0 014-4h14" />
                <path d="M7 22l-4-4 4-4" />
                <path d="M21 13v1a4 4 0 01-4 4H3" />
              </svg>
              <span>Retweeted</span>
            </div>
          )}
          {tweet.type === 'QUOTE' && (
            <div className="text-twitter-gray mt-1 flex items-center gap-1 text-xs">
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth={2}
                className="h-4 w-4"
              >
                <path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z" />
              </svg>
              <span>Quoted</span>
            </div>
          )}

          {/* Content */}
          <Link to={`/tweet/${tweet.id}`} className="block">
            <div className="mt-1 text-[15px] leading-5 break-words whitespace-pre-wrap">
              {renderContent(tweet.content)}
            </div>
            {/* Media placeholder — gray rectangle simulating image/video for ~30% of tweets */}
            {tweet.id % 3 === 0 && <div className="mt-3 h-48 rounded-2xl bg-gray-200" />}
          </Link>

          {/* Actions */}
          <div className="mt-3 flex max-w-[400px] items-center justify-between">
            {showReplyBtn && (
              <Link
                to={`/tweet/${tweet.id}`}
                className="group text-twitter-gray hover:text-twitter-blue flex items-center gap-1 text-sm transition-colors"
              >
                <div className="rounded-full p-2 transition-colors group-hover:bg-blue-50">
                  <svg
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth={2}
                    className="h-4 w-4"
                  >
                    <path d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
                  </svg>
                </div>
                <span>{tweet.replyCount > 0 ? tweet.replyCount : ''}</span>
              </Link>
            )}

            {/* Retweet */}
            <button
              onClick={handleRetweet}
              disabled={retweetApi.isPending}
              className={`group flex items-center gap-1 text-sm transition-colors hover:text-twitter-retweet ${
                retweetApi.isPending ? 'opacity-60' : 'text-twitter-gray'
              }`}
            >
              <div className="rounded-full p-2 transition-colors group-hover:bg-green-50">
                <svg
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth={2}
                  className="h-4 w-4"
                >
                  <path d="M17 2l4 4-4 4" />
                  <path d="M3 11v-1a4 4 0 014-4h14" />
                  <path d="M7 22l-4-4 4-4" />
                  <path d="M21 13v1a4 4 0 01-4 4H3" />
                </svg>
              </div>
              <span>{tweet.retweetCount > 0 ? tweet.retweetCount : ''}</span>
            </button>

            {/* Quote */}
            <div className="relative">
              <button
                onClick={handleQuote}
                aria-label="Quote tweet"
                className="group text-twitter-gray hover:text-twitter-blue flex items-center gap-1 text-sm transition-colors"
              >
                <div className="rounded-full p-2 transition-colors group-hover:bg-blue-50">
                  <svg
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth={2}
                    className="h-4 w-4"
                  >
                    <path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z" />
                    <path d="M12 8v8" />
                    <path d="M8 12h8" />
                  </svg>
                </div>
              </button>
              {showQuoteInput && (
                <div className="border-twitter-border absolute bottom-full left-0 mb-2 w-80 rounded-2xl border bg-white p-3 shadow-xl">
                  <form onSubmit={handleQuoteSubmit} className="flex items-center gap-2">
                    <input
                      type="text"
                      value={quoteContent}
                      onChange={(e) => setQuoteContent(e.target.value)}
                      placeholder="Add a comment"
                      maxLength={280}
                      autoFocus
                      className="border-twitter-border focus:border-twitter-blue min-w-0 flex-1 rounded-full border px-4 py-2 text-sm outline-none"
                    />
                    <button
                      type="submit"
                      disabled={!quoteContent.trim() || quoteApi.isPending}
                      className="bg-twitter-blue hover:bg-twitter-blue-hover rounded-full px-4 py-1.5 text-sm font-bold text-white transition-colors disabled:opacity-50"
                    >
                      {quoteApi.isPending ? '...' : 'Quote'}
                    </button>
                    <button
                      type="button"
                      onClick={handleQuoteCancel}
                      className="text-twitter-gray rounded-full px-3 py-1.5 text-sm transition-colors hover:bg-gray-100"
                    >
                      Cancel
                    </button>
                  </form>
                </div>
              )}
            </div>

            {/* Like */}
            <button
              onClick={handleLike}
              disabled={pending}
              className={`group hover:text-twitter-like relative flex items-center gap-1 text-sm transition-colors ${pending ? 'opacity-60' : ''}`}
            >
              <div
                className={`rounded-full p-2 transition-colors group-hover:bg-pink-50 ${liked ? 'bg-pink-50' : ''}`}
              >
                <svg
                  viewBox="0 0 24 24"
                  fill={liked ? 'currentColor' : 'none'}
                  stroke="currentColor"
                  strokeWidth={liked ? 0 : 2}
                  className={`h-4 w-4 transition-transform active:scale-125 ${liked ? 'scale-110' : ''}`}
                >
                  <path d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
                </svg>
              </div>
              <span className={liked ? 'text-twitter-like font-medium' : ''}>
                {tweet.likeCount > 0 ? tweet.likeCount : ''}
              </span>
            </button>

            {/* Bookmark */}
            <button
              onClick={handleBookmark}
              aria-label={bookmarked ? 'Remove bookmark' : 'Bookmark'}
              className="group text-twitter-gray hover:text-twitter-blue flex items-center gap-1 text-sm transition-colors"
            >
              <div className="rounded-full p-2 transition-colors group-hover:bg-blue-50">
                <svg
                  viewBox="0 0 24 24"
                  fill={bookmarked ? 'currentColor' : 'none'}
                  stroke="currentColor"
                  strokeWidth={bookmarked ? 0 : 2}
                  className="h-4 w-4"
                >
                  <path d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z" />
                </svg>
              </div>
            </button>
          </div>
        </div>
      </div>
    </article>
  );
}

function formatTimeAgo(iso: string): string {
  const now = Date.now();
  const then = new Date(iso).getTime();
  const diffMs = now - then;
  const diffMin = Math.floor(diffMs / 60000);

  if (diffMin < 1) return 'now';
  if (diffMin < 60) return `${diffMin}m`;

  const diffHr = Math.floor(diffMin / 60);
  if (diffHr < 24) return `${diffHr}h`;

  const diffDay = Math.floor(diffHr / 24);
  if (diffDay < 7) return `${diffDay}d`;

  return new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}
