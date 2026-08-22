import { describe, it, expect, beforeEach } from 'vitest';
import { useAuthStore } from '@/stores/authStore';

beforeEach(() => {
  localStorage.clear();
  useAuthStore.setState({ token: null, currentUserId: null, currentUsername: null });
});

describe('authStore', () => {
  it('starts unauthenticated', () => {
    expect(useAuthStore.getState().token).toBeNull();
    expect(useAuthStore.getState().isAuthenticated()).toBe(false);
  });

  it('stores auth state on setAuth', () => {
    useAuthStore.getState().setAuth('jwt123', 1, 'alice');
    expect(useAuthStore.getState().token).toBe('jwt123');
    expect(useAuthStore.getState().currentUserId).toBe(1);
    expect(useAuthStore.getState().currentUsername).toBe('alice');
    expect(useAuthStore.getState().isAuthenticated()).toBe(true);
  });

  it('persists token to localStorage', () => {
    useAuthStore.getState().setAuth('jwt456', 2, 'bob');
    const raw = localStorage.getItem('summer_auth');
    expect(raw).not.toBeNull();
    const parsed = JSON.parse(raw!);
    expect(parsed.token).toBe('jwt456');
    expect(parsed.userId).toBe(2);
    expect(parsed.username).toBe('bob');
  });

  it('clears state on logout', () => {
    useAuthStore.getState().setAuth('jwt123', 1, 'alice');
    useAuthStore.getState().logout();
    expect(useAuthStore.getState().token).toBeNull();
    expect(useAuthStore.getState().isAuthenticated()).toBe(false);
    expect(localStorage.getItem('summer_auth')).toBeNull();
  });
});
