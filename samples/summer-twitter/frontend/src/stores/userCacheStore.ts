import { create } from 'zustand';

interface UserInfo {
  username: string;
  displayName: string;
}

interface UserCacheState {
  cache: Record<number, UserInfo>;
  get: (id: number) => UserInfo | undefined;
  set: (id: number, info: UserInfo) => void;
  enrichTweet: (tweet: {
    authorId: number;
    authorUsername?: string;
    authorDisplayName?: string;
  }) => void;
}

// Pre-populated with seed data users so demo works out of the box
const SEED_USERS: Record<number, UserInfo> = {
  1001: { username: 'elonmusk', displayName: 'Elon Musk' },
  1002: { username: 'zuck', displayName: 'Mark Zuckerberg' },
  1003: { username: 'billgates', displayName: 'Bill Gates' },
  1004: { username: 'karpathy', displayName: 'Andrej Karpathy' },
};

export const useUserCacheStore = create<UserCacheState>((_, get) => ({
  cache: { ...SEED_USERS },
  get: (id) => get().cache[id],
  set: (id, info) => {
    useUserCacheStore.setState({ cache: { ...get().cache, [id]: info } });
  },
  enrichTweet: <T extends { authorId: number; authorUsername?: string; authorDisplayName?: string }>(
    tweet: T,
  ): T => {
    const info = get().cache[tweet.authorId];
    if (!info) return tweet;
    return { ...tweet, authorUsername: info.username, authorDisplayName: info.displayName };
  },
}));
