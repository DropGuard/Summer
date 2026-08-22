import { Link } from 'react-router-dom';
import { useNotificationStore, type Notification } from '@/stores/notificationStore';

function notificationText(n: Notification): string {
  switch (n.type) {
    case 'new_tweet':
      return `${n.authorUsername ?? 'Someone'} posted a new tweet`;
    case 'liked':
      return `${n.byUsername ?? 'Someone'} liked your tweet`;
    case 'mentioned':
      return `${n.byUsername ?? 'Someone'} mentioned you in a tweet`;
    case 'new_follower':
      return `${n.username ?? 'Someone'} followed you`;
  }
}

function notificationLink(n: Notification): string {
  if (n.type === 'new_follower') {
    return `/${n.username ?? ''}`;
  }
  return n.tweetId ? `/tweet/${n.tweetId}` : '/';
}

export default function NotificationsPage() {
  const notifications = useNotificationStore((s) => s.notifications);
  const unreadCount = useNotificationStore((s) => s.unreadCount);
  const markAllRead = useNotificationStore((s) => s.markAllRead);

  return (
    <div>
      {/* Header */}
      <div className="border-twitter-border sticky top-0 flex items-center justify-between border-b bg-white/80 px-4 py-3 backdrop-blur">
        <h1 className="text-xl font-bold">Notifications</h1>
        {unreadCount > 0 && (
          <button
            onClick={markAllRead}
            className="text-twitter-blue hover:bg-twitter-blue/10 rounded-full px-4 py-1.5 text-sm font-bold transition-colors"
          >
            Mark all read
          </button>
        )}
      </div>

      {/* List */}
      {notifications.length === 0 ? (
        <div className="text-twitter-gray flex flex-col items-center justify-center py-20">
          <BellIcon />
          <p className="mt-4 text-lg font-bold">No notifications yet</p>
          <p className="mt-1 text-sm">When you get notifications, they'll show up here.</p>
        </div>
      ) : (
        <div>
          {notifications.map((n) => (
            <Link
              key={n.id}
              to={notificationLink(n)}
              className={`border-twitter-border hover:bg-twitter-light-gray flex items-start gap-3 border-b px-4 py-3 transition-colors ${
                n.read ? 'opacity-60' : ''
              }`}
            >
              {/* Unread dot */}
              <div className="flex shrink-0 pt-1">
                {!n.read && <div className="bg-twitter-blue h-2.5 w-2.5 rounded-full" />}
                {n.read && <div className="h-2.5 w-2.5" />}
              </div>

              {/* Icon */}
              <div className="bg-twitter-light-gray flex h-10 w-10 shrink-0 items-center justify-center rounded-full">
                <NotificationIcon type={n.type} />
              </div>

              {/* Content */}
              <div className="min-w-0 flex-1">
                <p className="text-sm">{notificationText(n)}</p>
                <p className="text-twitter-gray mt-0.5 text-xs">{formatTimestamp(n.timestamp)}</p>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}

function formatTimestamp(ts: number): string {
  const diff = Date.now() - ts;
  if (diff < 60_000) return 'Just now';
  if (diff < 3600_000) return `${Math.floor(diff / 60_000)}m ago`;
  if (diff < 86_400_000) return `${Math.floor(diff / 3600_000)}h ago`;
  return new Date(ts).toLocaleDateString();
}

function NotificationIcon({ type }: { type: Notification['type'] }) {
  if (type === 'new_follower') {
    return (
      <svg viewBox="0 0 24 24" fill="currentColor" className="text-twitter-blue h-5 w-5">
        <path d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
      </svg>
    );
  }
  // new_tweet, liked, mentioned — heart icon
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className="h-5 w-5 text-red-500">
      <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z" />
    </svg>
  );
}

function BellIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.5}
      className="h-12 w-12"
    >
      <path d="M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9" />
      <path d="M13.73 21a2 2 0 01-3.46 0" />
    </svg>
  );
}
