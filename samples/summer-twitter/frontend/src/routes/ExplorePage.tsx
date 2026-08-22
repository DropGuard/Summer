import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useSearchUsers, useFollow, useUnfollow } from '@/api/users';
import { useTimeline } from '@/api/timeline';
import { useAuthStore } from '@/stores/authStore';
import { useFollowStore } from '@/stores/followStore';
import TweetCard from '@/components/TweetCard';
import type { User } from '@/lib/types';

export default function ExplorePage() {
  const [query, setQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [searchTab, setSearchTab] = useState<'users' | 'tweets'>('users');

  const currentUsername = useAuthStore((s) => s.currentUsername);
  const {
    data: user,
    isLoading,
    isError,
  } = useSearchUsers(searchTab === 'users' ? debouncedQuery : '');
  const timelineQuery = useTimeline({ limit: 100 });
  const filteredTweets =
    timelineQuery.data?.filter((tweet) =>
      tweet.content.toLowerCase().includes(debouncedQuery.toLowerCase()),
    ) ?? [];
  const follow = useFollow();
  const unfollow = useUnfollow();
  const {
    isFollowed,
    isPending,
    follow: addFollowed,
    unfollow: removeFollowed,
    addPending,
    removePending,
  } = useFollowStore();

  // Debounce 300ms
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedQuery(query.trim());
    }, 300);
    return () => clearTimeout(timer);
  }, [query]);

  const handleTabChange = (tab: 'users' | 'tweets') => {
    setSearchTab(tab);
    setQuery('');
    setDebouncedQuery('');
  };

  const isSearching = query.trim().length > 0 && isLoading;
  const hasResults = !!user && debouncedQuery.length > 0;
  const notFound = debouncedQuery.length > 0 && !isLoading && !user && !isError;

  const handleFollowToggle = (targetUser: User) => {
    const username = targetUser.username;
    if (isPending(username)) return;
    if (isFollowed(username)) {
      removeFollowed(username);
      addPending(username);
      unfollow.mutate(username, {
        onSuccess: () => removePending(username),
        onError: () => {
          addFollowed(username);
          removePending(username);
        },
      });
    } else {
      addFollowed(username);
      addPending(username);
      follow.mutate(username, {
        onSuccess: () => removePending(username),
        onError: () => {
          removeFollowed(username);
          removePending(username);
        },
      });
    }
  };

  const isUserFollowing = (username: string) => isFollowed(username);

  return (
    <div>
      {/* Header */}
      <div className="border-twitter-border sticky top-0 border-b bg-white/80 px-4 py-3 backdrop-blur">
        <h1 className="text-xl font-bold">Explore</h1>
      </div>

      {/* Search input */}
      <div className="border-twitter-border border-b px-4 py-3">
        <div className="bg-twitter-light-gray flex items-center gap-3 rounded-full px-4 py-2.5">
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth={2}
            className="text-twitter-gray h-5 w-5 shrink-0"
          >
            <circle cx="11" cy="11" r="8" />
            <path d="M21 21l-4.35-4.35" />
          </svg>
          <input
            type="text"
            placeholder={searchTab === 'users' ? 'Search users...' : 'Search tweets...'}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            className="text-twitter-dark placeholder:text-twitter-gray w-full bg-transparent outline-none"
            autoFocus
          />
          {query.length > 0 && (
            <button
              onClick={() => {
                setQuery('');
                setDebouncedQuery('');
              }}
              className="bg-twitter-gray flex h-5 w-5 items-center justify-center rounded-full text-white"
            >
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth={3}
                className="h-3 w-3"
              >
                <path d="M18 6L6 18M6 6l12 12" />
              </svg>
            </button>
          )}
        </div>

        {/* Tab bar */}
        <div className="border-twitter-border mt-3 flex border-b">
          <button
            onClick={() => handleTabChange('users')}
            className={`flex-1 px-4 py-3 text-sm font-medium transition-colors ${
              searchTab === 'users'
                ? 'border-twitter-blue text-twitter-dark border-b-2'
                : 'text-twitter-gray hover:bg-twitter-light-gray/50 hover:text-twitter-dark'
            }`}
          >
            Users
          </button>
          <button
            onClick={() => handleTabChange('tweets')}
            className={`flex-1 px-4 py-3 text-sm font-medium transition-colors ${
              searchTab === 'tweets'
                ? 'border-twitter-blue text-twitter-dark border-b-2'
                : 'text-twitter-gray hover:bg-twitter-light-gray/50 hover:text-twitter-dark'
            }`}
          >
            Tweets
          </button>
        </div>
      </div>

      {/* Content area */}
      <div className="px-4 py-8">
        {/* ── USER SEARCH ── */}
        {searchTab === 'users' && (
          <>
            {/* Loading spinner */}
            {isSearching && (
              <div className="flex justify-center py-12">
                <div className="border-twitter-border border-t-twitter-blue h-8 w-8 animate-spin rounded-full border-4" />
              </div>
            )}

            {/* Search results */}
            {hasResults && user && (
              <UserCard
                user={user}
                isOwnProfile={currentUsername === user.username}
                isFollowing={isUserFollowing(user.username)}
                isPending={isPending(user.username)}
                onFollowToggle={handleFollowToggle}
                followPending={follow.isPending || unfollow.isPending}
              />
            )}

            {/* No results */}
            {notFound && (
              <div className="py-12 text-center">
                <p className="text-twitter-dark text-lg font-bold">No users found</p>
                <p className="text-twitter-gray mt-1">
                  We couldn't find anyone with the username &quot;{debouncedQuery}&quot;
                </p>
              </div>
            )}

            {/* Empty state */}
            {debouncedQuery.length === 0 && !isSearching && (
              <div className="py-12 text-center">
                <div className="bg-twitter-light-gray mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full">
                  <svg
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth={1.5}
                    className="text-twitter-gray h-8 w-8"
                  >
                    <circle cx="11" cy="11" r="8" />
                    <path d="M21 21l-4.35-4.35" />
                  </svg>
                </div>
                <p className="text-twitter-dark text-lg font-bold">Search for users to follow</p>
                <p className="text-twitter-gray mt-1">Find people by their exact username</p>
              </div>
            )}

            {/* Error state */}
            {isError && !isLoading && (
              <div className="py-12 text-center">
                <p className="text-twitter-dark text-lg font-bold">Something went wrong</p>
                <p className="text-twitter-gray mt-1">Please try again later</p>
              </div>
            )}
          </>
        )}

        {/* ── TWEET SEARCH ── */}
        {searchTab === 'tweets' && (
          <>
            {/* Loading spinner */}
            {query.trim().length > 0 && timelineQuery.isLoading && (
              <div className="flex justify-center py-12">
                <div className="border-twitter-border border-t-twitter-blue h-8 w-8 animate-spin rounded-full border-4" />
              </div>
            )}

            {/* Results */}
            {debouncedQuery.length > 0 && filteredTweets.length > 0 && !timelineQuery.isLoading && (
              <div className="divide-twitter-border divide-y">
                {filteredTweets.map((tweet) => (
                  <TweetCard key={tweet.id} tweet={tweet} showReplyBtn={false} />
                ))}
              </div>
            )}

            {/* No tweets found */}
            {debouncedQuery.length > 0 &&
              filteredTweets.length === 0 &&
              !timelineQuery.isLoading &&
              !timelineQuery.isError && (
                <div className="py-12 text-center">
                  <p className="text-twitter-dark text-lg font-bold">No tweets found</p>
                  <p className="text-twitter-gray mt-1">
                    No tweets matching &quot;{debouncedQuery}&quot;
                  </p>
                </div>
              )}

            {/* Empty state — no query yet */}
            {debouncedQuery.length === 0 &&
              !query.trim().length &&
              !timelineQuery.isLoading &&
              !timelineQuery.isError && (
                <div className="py-12 text-center">
                  <div className="bg-twitter-light-gray mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full">
                    <svg
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth={1.5}
                      className="text-twitter-gray h-8 w-8"
                    >
                      <circle cx="11" cy="11" r="8" />
                      <path d="M21 21l-4.35-4.35" />
                    </svg>
                  </div>
                  <p className="text-twitter-dark text-lg font-bold">Search for tweets</p>
                  <p className="text-twitter-gray mt-1">Find tweets by their content</p>
                </div>
              )}

            {/* Timeline error */}
            {timelineQuery.isError && !timelineQuery.isLoading && (
              <div className="py-12 text-center">
                <p className="text-twitter-dark text-lg font-bold">Something went wrong</p>
                <p className="text-twitter-gray mt-1">Failed to load tweets</p>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}

/* ── UserCard component ── */

interface UserCardProps {
  user: User;
  isOwnProfile: boolean;
  isFollowing: boolean;
  isPending: boolean;
  onFollowToggle: (user: User) => void;
  followPending: boolean;
}

function UserCard({
  user,
  isOwnProfile,
  isFollowing,
  isPending: pending,
  onFollowToggle,
  followPending,
}: UserCardProps) {
  return (
    <div className="border-twitter-border hover:bg-twitter-light-gray/50 flex items-start gap-3 rounded-xl border p-4 transition-colors">
      {/* Avatar */}
      <Link
        to={`/${user.username}`}
        className="bg-twitter-blue flex h-12 w-12 shrink-0 items-center justify-center rounded-full text-lg font-bold text-white"
      >
        {user.displayName[0]?.toUpperCase()}
      </Link>

      {/* User info */}
      <div className="min-w-0 flex-1">
        <Link to={`/${user.username}`} className="hover:underline">
          <div className="text-twitter-dark truncate font-bold">{user.displayName}</div>
          <div className="text-twitter-gray truncate text-sm">@{user.username}</div>
        </Link>
        {user.bio && <p className="text-twitter-dark mt-1 line-clamp-2 text-sm">{user.bio}</p>}
        <div className="text-twitter-gray mt-1 flex gap-4 text-sm">
          <span>
            <strong className="text-twitter-dark">{user.followingCount}</strong> Following
          </span>
          <span>
            <strong className="text-twitter-dark">{user.followerCount}</strong> Followers
          </span>
        </div>
      </div>

      {/* Follow / Following button */}
      {!isOwnProfile && (
        <button
          onClick={() => onFollowToggle(user)}
          disabled={followPending || pending}
          className={`shrink-0 rounded-full border px-4 py-1.5 text-sm font-bold transition-colors ${
            isFollowing
              ? 'border-twitter-border text-twitter-dark hover:border-red-500 hover:text-red-500'
              : 'bg-twitter-dark text-white hover:bg-gray-800'
          } ${pending ? 'opacity-60' : ''}`}
        >
          {pending ? '...' : isFollowing ? 'Following' : 'Follow'}
        </button>
      )}
    </div>
  );
}
