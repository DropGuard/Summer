import { create } from 'zustand';

export interface Notification {
  id: string;
  type: 'new_tweet' | 'liked' | 'mentioned' | 'new_follower';
  tweetId?: string;
  byUsername?: string;
  authorUsername?: string;
  username?: string;
  timestamp: number;
  read: boolean;
}

interface NotificationState {
  notifications: Notification[];
  unreadCount: number;
  add: (n: Omit<Notification, 'id' | 'timestamp' | 'read'>) => void;
  markAllRead: () => void;
}

const MAX_NOTIFICATIONS = 50;

export const useNotificationStore = create<NotificationState>((set, get) => ({
  notifications: [],
  unreadCount: 0,
  add: (n) => {
    const notification: Notification = {
      ...n,
      id: `${Date.now()}_${Math.random().toString(36).slice(2, 9)}`,
      timestamp: Date.now(),
      read: false,
    };
    const notifications = [notification, ...get().notifications].slice(0, MAX_NOTIFICATIONS);
    set({ notifications, unreadCount: notifications.filter((x) => !x.read).length });
  },
  markAllRead: () => {
    const notifications = get().notifications.map((n) => ({ ...n, read: true }));
    set({ notifications, unreadCount: 0 });
  },
}));
