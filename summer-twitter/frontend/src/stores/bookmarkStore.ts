import { create } from 'zustand';

export interface Bookmark {
  tweetId: number;
  authorName: string;
  content: string;
  createdAt: string;
  savedAt: number;
}

interface BookmarkState {
  bookmarks: Bookmark[];
  add: (b: Bookmark) => void;
  remove: (tweetId: number) => void;
  isBookmarked: (tweetId: number) => boolean;
}

function loadBookmarks(): Bookmark[] {
  try {
    const raw = localStorage.getItem('bookmarks');
    return raw ? (JSON.parse(raw) as Bookmark[]) : [];
  } catch {
    return [];
  }
}

function saveBookmarks(bs: Bookmark[]) {
  localStorage.setItem('bookmarks', JSON.stringify(bs));
}

export const useBookmarkStore = create<BookmarkState>((_, get) => ({
  bookmarks: loadBookmarks(),
  add: (b) => {
    const next = [b, ...get().bookmarks];
    saveBookmarks(next);
    useBookmarkStore.setState({ bookmarks: next });
  },
  remove: (tweetId) => {
    const next = get().bookmarks.filter((b) => b.tweetId !== tweetId);
    saveBookmarks(next);
    useBookmarkStore.setState({ bookmarks: next });
  },
  isBookmarked: (tweetId) => get().bookmarks.some((b) => b.tweetId === tweetId),
}));
