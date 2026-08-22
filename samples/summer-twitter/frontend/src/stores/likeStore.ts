import { create } from 'zustand';

interface LikeState {
  likedSet: Set<number>;
  pendingSet: Set<number>;
  isLiked: (tweetId: number) => boolean;
  isPending: (tweetId: number) => boolean;
  add: (tweetId: number) => void;
  remove: (tweetId: number) => void;
  addPending: (tweetId: number) => void;
  removePending: (tweetId: number) => void;
}

function loadLiked(): Set<number> {
  try {
    const raw = localStorage.getItem('likedSet');
    if (!raw) return new Set();
    return new Set(JSON.parse(raw) as number[]);
  } catch {
    return new Set();
  }
}

function saveLiked(set: Set<number>) {
  localStorage.setItem('likedSet', JSON.stringify([...set]));
}

export const useLikeStore = create<LikeState>((_, get) => {
  const initial = loadLiked();
  return {
    likedSet: initial,
    pendingSet: new Set(),
    isLiked: (tweetId) => get().likedSet.has(tweetId),
    isPending: (tweetId) => get().pendingSet.has(tweetId),
    add: (tweetId) => {
      const next = new Set(get().likedSet);
      next.add(tweetId);
      saveLiked(next);
      useLikeStore.setState({ likedSet: next });
    },
    remove: (tweetId) => {
      const next = new Set(get().likedSet);
      next.delete(tweetId);
      saveLiked(next);
      useLikeStore.setState({ likedSet: next });
    },
    addPending: (tweetId) => {
      const next = new Set(get().pendingSet);
      next.add(tweetId);
      useLikeStore.setState({ pendingSet: next });
    },
    removePending: (tweetId) => {
      const next = new Set(get().pendingSet);
      next.delete(tweetId);
      useLikeStore.setState({ pendingSet: next });
    },
  };
});
