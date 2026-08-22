import { create } from 'zustand';

interface AuthState {
  token: string | null;
  currentUserId: number | null;
  currentUsername: string | null;
  setAuth: (token: string, userId: number, username: string) => void;
  logout: () => void;
  isAuthenticated: () => boolean;
}

interface AuthStorage {
  token: string;
  userId: number;
  username: string;
}

const STORAGE_KEY = 'issue_tracker_auth';

function loadFromStorage(): {
  token: string | null;
  currentUserId: number | null;
  currentUsername: string | null;
} {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return { token: null, currentUserId: null, currentUsername: null };
    const data = JSON.parse(raw) as AuthStorage;
    return {
      token: data.token,
      currentUserId: data.userId,
      currentUsername: data.username,
    };
  } catch {
    return { token: null, currentUserId: null, currentUsername: null };
  }
}

function saveToStorage(token: string, userId: number, username: string) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify({ token, userId, username }));
}

function clearStorage() {
  localStorage.removeItem(STORAGE_KEY);
}

const initial = loadFromStorage();

export const useAuthStore = create<AuthState>((_, get) => ({
  ...initial,
  setAuth: (token: string, userId: number, username: string) => {
    saveToStorage(token, userId, username);
    useAuthStore.setState({ token, currentUserId: userId, currentUsername: username });
  },
  logout: () => {
    clearStorage();
    useAuthStore.setState({ token: null, currentUserId: null, currentUsername: null });
  },
  isAuthenticated: () => get().token !== null,
}));
