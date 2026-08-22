import { create } from 'zustand';

interface FollowState {
  followedSet: Set<string>;
  pendingSet: Set<string>;
  isFollowed: (username: string) => boolean;
  isPending: (username: string) => boolean;
  follow: (username: string) => void;
  unfollow: (username: string) => void;
  addPending: (username: string) => void;
  removePending: (username: string) => void;
}

function loadFollowed(): Set<string> {
  try {
    const raw = localStorage.getItem('followedSet');
    if (!raw) return new Set();
    return new Set(JSON.parse(raw) as string[]);
  } catch {
    return new Set();
  }
}

function saveFollowed(set: Set<string>) {
  localStorage.setItem('followedSet', JSON.stringify([...set]));
}

export const useFollowStore = create<FollowState>((_, get) => {
  const initial = loadFollowed();
  return {
    followedSet: initial,
    pendingSet: new Set(),
    isFollowed: (username) => get().followedSet.has(username),
    isPending: (username) => get().pendingSet.has(username),
    follow: (username) => {
      const next = new Set(get().followedSet);
      next.add(username);
      saveFollowed(next);
      useFollowStore.setState({ followedSet: next });
    },
    unfollow: (username) => {
      const next = new Set(get().followedSet);
      next.delete(username);
      saveFollowed(next);
      useFollowStore.setState({ followedSet: next });
    },
    addPending: (username) => {
      const next = new Set(get().pendingSet);
      next.add(username);
      useFollowStore.setState({ pendingSet: next });
    },
    removePending: (username) => {
      const next = new Set(get().pendingSet);
      next.delete(username);
      useFollowStore.setState({ pendingSet: next });
    },
  };
});
