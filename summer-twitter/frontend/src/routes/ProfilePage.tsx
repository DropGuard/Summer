import { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useUser, useFollow, useUnfollow, useUpdateProfile } from '@/api/users';
import { useTimeline } from '@/api/timeline';
import { useAuthStore } from '@/stores/authStore';
import { useFollowStore } from '@/stores/followStore';
import { useUserCacheStore } from '@/stores/userCacheStore';
import TweetCard from '@/components/TweetCard';

export default function ProfilePage() {
  const { username } = useParams<{ username: string }>();
  const { data: user, isLoading, isError } = useUser(username ?? '');
  const follow = useFollow();
  const unfollow = useUnfollow();
  const currentUsername = useAuthStore((s) => s.currentUsername);
  // Show user's tweets via timeline (backend doesn't have a dedicated user tweets endpoint yet)
  const { data: tweets } = useTimeline({ limit: 50 });

  const isOwnProfile = currentUsername === username;
  const {
    isFollowed,
    isPending,
    follow: addFollowed,
    unfollow: removeFollowed,
    addPending,
    removePending,
  } = useFollowStore();
  const isFollowing = isFollowed(username ?? '');
  const [editing, setEditing] = useState(false);
  const [editName, setEditName] = useState('');
  const [editBio, setEditBio] = useState('');
  const updateProfile = useUpdateProfile();
  const cacheUser = useUserCacheStore((s) => s.set);

  // Cache user info so tweet cards show correct name
  useEffect(() => {
    if (user) cacheUser(user.id, { username: user.username, displayName: user.displayName });
  }, [user, cacheUser]);

  const startEditing = () => {
    setEditName(user.displayName);
    setEditBio(user.bio ?? '');
    setEditing(true);
  };

  const cancelEditing = () => setEditing(false);

  const handleSave = async () => {
    await updateProfile.mutateAsync({ displayName: editName, bio: editBio });
    setEditing(false);
  };

  if (isLoading) {
    return <div className="text-twitter-gray p-8 text-center">Loading...</div>;
  }

  if (isError || !user) {
    return (
      <div className="text-twitter-gray p-8 text-center">
        <p className="text-lg font-bold">User not found</p>
        <Link to="/" className="text-twitter-blue mt-4 inline-block hover:underline">
          Go home
        </Link>
      </div>
    );
  }

  const handleFollow = () => {
    if (!username || isPending(username)) return;
    if (isFollowing) {
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

  const joinedDate = new Date(user.createdAt).toLocaleDateString('en-US', {
    month: 'long',
    year: 'numeric',
  });

  // Filter current user's tweets by authorId (authorUsername is optional client-enriched)
  const authorId = user?.id;
  const userTweets = tweets?.filter((t) => authorId && t.authorId === authorId) ?? [];

  return (
    <div>
      {/* Back header */}
      <div className="border-twitter-border sticky top-0 border-b bg-white/80 px-4 py-3 backdrop-blur">
        <Link to="/" className="flex items-center gap-4">
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth={2}
            className="h-5 w-5"
          >
            <path d="M15 19l-7-7 7-7" />
          </svg>
          <div>
            <div className="font-bold">{user.displayName}</div>
            <div className="text-twitter-gray text-sm">@{user.username}</div>
          </div>
        </Link>
      </div>

      {/* Banner */}
      <div className="from-twitter-blue h-32 bg-gradient-to-r to-blue-300" />

      {/* Avatar + actions */}
      <div className="px-4">
        <div className="-mt-12 flex items-end justify-between">
          <div className="bg-twitter-blue flex h-24 w-24 items-center justify-center rounded-full border-4 border-white text-3xl font-bold text-white">
            {user.displayName[0]?.toUpperCase()}
          </div>

          {isOwnProfile && !editing && (
            <button
              onClick={startEditing}
              className="border-twitter-border rounded-full border px-5 py-2 font-bold transition-colors hover:bg-gray-50"
            >
              Edit profile
            </button>
          )}
          {isOwnProfile && editing && (
            <div className="flex gap-2">
              <button
                onClick={cancelEditing}
                className="border-twitter-border rounded-full border px-5 py-2 font-bold transition-colors hover:bg-gray-50"
              >
                Cancel
              </button>
              <button
                onClick={handleSave}
                disabled={updateProfile.isPending}
                className="bg-twitter-dark rounded-full px-5 py-2 font-bold text-white transition-colors hover:bg-gray-800 disabled:opacity-50"
              >
                {updateProfile.isPending ? 'Saving...' : 'Save'}
              </button>
            </div>
          )}
          {!isOwnProfile && (
            <button
              onClick={handleFollow}
              disabled={follow.isPending || unfollow.isPending || isPending(username ?? '')}
              className={`rounded-full border px-5 py-2 font-bold transition-colors ${
                isFollowing
                  ? 'border-twitter-border text-twitter-dark hover:border-red-500 hover:text-red-500'
                  : 'bg-twitter-dark text-white hover:bg-gray-800'
              } ${isPending(username ?? '') ? 'opacity-60' : ''}`}
            >
              {isPending(username ?? '') ? '...' : isFollowing ? 'Following' : 'Follow'}
            </button>
          )}
        </div>

        {/* Profile info */}
        <div className="mt-3">
          <div className="text-xl font-bold">{user.displayName}</div>
          <div className="text-twitter-gray">@{user.username}</div>
          {user.bio && <p className="mt-2">{user.bio}</p>}
          <div className="text-twitter-gray mt-2 text-sm">Joined {joinedDate}</div>
          <div className="mt-2 flex gap-4 text-sm">
            <span>
              <strong>{user.followingCount}</strong>{' '}
              <span className="text-twitter-gray">Following</span>
            </span>
            <span>
              <strong>{user.followerCount}</strong>{' '}
              <span className="text-twitter-gray">Followers</span>
            </span>
          </div>
        </div>

        {editing && (
          <div className="border-twitter-border mt-4 space-y-3 rounded-lg border p-4">
            <input
              type="text"
              value={editName}
              onChange={(e) => setEditName(e.target.value)}
              placeholder="Display name"
              maxLength={64}
              className="border-twitter-border focus:border-twitter-blue w-full rounded-md border px-3 py-2 text-sm outline-none"
            />
            <textarea
              value={editBio}
              onChange={(e) => setEditBio(e.target.value)}
              placeholder="Bio"
              maxLength={280}
              rows={3}
              className="border-twitter-border focus:border-twitter-blue w-full resize-none rounded-md border px-3 py-2 text-sm outline-none"
            />
          </div>
        )}
      </div>

      {/* Tweets tab */}
      <div className="border-twitter-border mt-4 border-b">
        <div className="flex">
          <div className="border-twitter-blue text-twitter-blue flex-1 border-b-2 py-3 text-center font-bold">
            Posts
          </div>
        </div>
      </div>

      {/* User's tweets */}
      {userTweets.length > 0 ? (
        userTweets.map((tweet) => <TweetCard key={tweet.id} tweet={tweet} />)
      ) : (
        <div className="text-twitter-gray p-8 text-center">
          <p className="text-lg font-bold">No posts yet</p>
        </div>
      )}
    </div>
  );
}
