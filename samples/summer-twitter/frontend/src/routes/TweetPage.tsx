import { useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import {
  useTweet,
  useReplies,
  useCreateTweet,
  useLike,
  useUnlike,
  useDeleteTweet,
  useRetweet,
  useQuoteTweet,
} from '@/api/tweets';
import { useLikeStore } from '@/stores/likeStore';
import TweetCard from '@/components/TweetCard';
import ConfirmDialog from '@/components/ConfirmDialog';
import { useAuthStore } from '@/stores/authStore';

export default function TweetPage() {
  const { id } = useParams<{ id: string }>();
  const tweetId = Number(id);
  const { data: tweet, isLoading, isError } = useTweet(tweetId);
  const { data: replies, isLoading: repliesLoading } = useReplies(tweetId);
  const [replyContent, setReplyContent] = useState('');
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const createReply = useCreateTweet();
  const likeApi = useLike();
  const unlikeApi = useUnlike();
  const retweetApi = useRetweet();
  const quoteApi = useQuoteTweet();
  const deleteTweet = useDeleteTweet();
  const navigate = useNavigate();
  const {
    isLiked,
    isPending,
    add: addLiked,
    remove: removeLiked,
    addPending,
    removePending,
  } = useLikeStore();
  const [showQuoteInput, setShowQuoteInput] = useState(false);
  const [quoteContent, setQuoteContent] = useState('');
  const currentUserId = useAuthStore((s) => s.currentUserId);
  const currentUsername = useAuthStore((s) => s.currentUsername);

  if (isLoading) {
    return <div className="text-twitter-gray p-8 text-center">Loading...</div>;
  }

  if (isError || !tweet) {
    return (
      <div className="text-twitter-gray p-8 text-center">
        <p className="text-lg font-bold">Tweet not found</p>
        <Link to="/" className="text-twitter-blue mt-4 inline-block hover:underline">
          Go home
        </Link>
      </div>
    );
  }

  const handleReply = (e: React.FormEvent) => {
    e.preventDefault();
    if (!replyContent.trim() || createReply.isPending) return;
    createReply.mutate(
      { content: replyContent.trim(), parentId: tweetId },
      { onSuccess: () => setReplyContent('') },
    );
  };

  const handleLike = () => {
    if (isPending(tweetId)) return;
    addPending(tweetId);
    if (isLiked(tweetId)) {
      unlikeApi.mutate(tweetId, {
        onSuccess: () => {
          removeLiked(tweetId);
          removePending(tweetId);
        },
        onError: () => removePending(tweetId),
      });
    } else {
      likeApi.mutate(tweetId, {
        onSuccess: () => {
          addLiked(tweetId);
          removePending(tweetId);
        },
        onError: () => removePending(tweetId),
      });
    }
  };

  const handleDelete = () => {
    if (deleteTweet.isPending) return;
    deleteTweet.mutate(tweetId, { onSuccess: () => navigate('/', { replace: true }) });
  };

  const handleRetweet = () => {
    if (retweetApi.isPending) return;
    retweetApi.mutate(tweetId);
  };

  const handleQuote = () => {
    setShowQuoteInput(true);
  };

  const handleQuoteSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!quoteContent.trim() || quoteApi.isPending) return;
    quoteApi.mutate(
      { tweetId, content: quoteContent.trim() },
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

  const timeAgo = new Date(tweet.createdAt).toLocaleDateString('en-US', {
    hour: '2-digit',
    minute: '2-digit',
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });

  return (
    <div>
      {/* Back header */}
      <div className="border-twitter-border sticky top-0 border-b bg-white/80 px-4 py-3 backdrop-blur">
        <Link to="/" className="flex items-center gap-4 text-lg">
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth={2}
            className="h-5 w-5"
          >
            <path d="M15 19l-7-7 7-7" />
          </svg>
          <span className="font-bold">Post</span>
        </Link>
      </div>

      {/* Main tweet */}
      <div className="border-twitter-border border-b px-4 py-3">
        <div className="flex gap-3">
          <Link
            to={`/${tweet.authorUsername ?? tweet.authorId}`}
            className="bg-twitter-blue flex h-10 w-10 shrink-0 items-center justify-center rounded-full font-bold text-white"
          >
            {(tweet.authorDisplayName?.[0] ?? '?').toUpperCase()}
          </Link>
          <div>
            <Link
              to={`/${tweet.authorUsername ?? tweet.authorId}`}
              className="font-bold hover:underline"
            >
              {tweet.authorDisplayName ?? 'Unknown'}
            </Link>
            <span className="text-twitter-gray ml-1">@{tweet.authorUsername ?? '?'}</span>
          </div>
        </div>

        <p className="mt-3 text-[15px] leading-5 break-words whitespace-pre-wrap">
          {tweet.content}
        </p>

        <p className="text-twitter-gray mt-3 text-sm">{timeAgo}</p>

        {/* Stats */}
        <div className="border-twitter-border mt-3 flex gap-6 border-y py-3 text-sm">
          <span>
            <strong>{tweet.replyCount}</strong> <span className="text-twitter-gray">Replies</span>
          </span>
          <span>
            <strong>{tweet.retweetCount}</strong>{' '}
            <span className="text-twitter-gray">Retweets</span>
          </span>
          <span>
            <strong>{tweet.likeCount}</strong> <span className="text-twitter-gray">Likes</span>
          </span>
        </div>

        {/* Action buttons */}
        <div className="relative mt-1 flex max-w-[400px] items-center justify-between py-1">
          <button
            onClick={handleRetweet}
            disabled={retweetApi.isPending}
            className={`flex items-center gap-2 text-sm transition-colors hover:text-twitter-retweet ${
              retweetApi.isPending ? 'opacity-60' : 'text-twitter-gray'
            }`}
          >
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth={2}
              className="h-5 w-5"
            >
              <path d="M17 2l4 4-4 4" />
              <path d="M3 11v-1a4 4 0 014-4h14" />
              <path d="M7 22l-4-4 4-4" />
              <path d="M21 13v1a4 4 0 01-4 4H3" />
            </svg>
            Retweet
          </button>
          <div className="relative">
            <button
              onClick={handleQuote}
              className="text-twitter-gray hover:text-twitter-blue flex items-center gap-2 text-sm transition-colors"
            >
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth={2}
                className="h-5 w-5"
              >
                <path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z" />
                <path d="M12 8v8" />
                <path d="M8 12h8" />
              </svg>
              Quote
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
          <button
            onClick={handleLike}
            disabled={isPending(tweetId)}
            className={`hover:text-twitter-like flex items-center gap-2 text-sm transition-colors ${isPending(tweetId) ? 'opacity-60' : ''} ${isLiked(tweetId) ? 'text-twitter-like' : 'text-twitter-gray'}`}
          >
            <svg
              viewBox="0 0 24 24"
              fill={isLiked(tweetId) ? 'currentColor' : 'none'}
              stroke="currentColor"
              strokeWidth={2}
              className="h-5 w-5"
            >
              <path d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
            </svg>
            Like
          </button>
          {tweet.authorId === currentUserId && (
            <button
              onClick={() => setShowDeleteConfirm(true)}
              className="text-twitter-gray flex items-center gap-2 text-sm transition-colors hover:text-red-500"
            >
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth={2}
                className="h-5 w-5"
              >
                <path d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
              </svg>
              Delete
            </button>
          )}
        </div>
      </div>

      {/* Reply compose */}
      <div className="border-twitter-border border-b px-4 py-3">
        <div className="flex gap-3">
          <div className="bg-twitter-blue flex h-10 w-10 shrink-0 items-center justify-center rounded-full font-bold text-white">
            {(currentUsername?.[0] ?? '?').toUpperCase()}
          </div>
          <form onSubmit={handleReply} className="flex-1">
            <textarea
              placeholder="Post your reply"
              value={replyContent}
              onChange={(e) => setReplyContent(e.target.value)}
              maxLength={280}
              rows={2}
              className="placeholder:text-twitter-gray w-full resize-none text-lg outline-none"
            />
            <div className="flex justify-end">
              <button
                type="submit"
                disabled={!replyContent.trim() || createReply.isPending}
                className="bg-twitter-blue hover:bg-twitter-blue-hover rounded-full px-5 py-2 font-bold text-white transition-colors disabled:opacity-50"
              >
                {createReply.isPending ? 'Replying...' : 'Reply'}
              </button>
            </div>
          </form>
        </div>
      </div>

      {/* Replies */}
      {repliesLoading ? (
        <div className="text-twitter-gray p-4 text-center">Loading replies...</div>
      ) : replies && replies.length > 0 ? (
        replies.map((reply) => <TweetCard key={reply.id} tweet={reply} showReplyBtn={false} />)
      ) : (
        <div className="text-twitter-gray p-8 text-center">No replies yet.</div>
      )}

      <ConfirmDialog
        open={showDeleteConfirm}
        title="Delete post?"
        message="This can't be undone and it will be removed from your profile, the timeline of any accounts that follow you, and from search results."
        confirmLabel="Delete"
        cancelLabel="Cancel"
        destructive
        onConfirm={() => {
          setShowDeleteConfirm(false);
          handleDelete();
        }}
        onCancel={() => setShowDeleteConfirm(false)}
      />
    </div>
  );
}
