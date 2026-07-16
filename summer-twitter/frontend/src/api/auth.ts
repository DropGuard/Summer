import { useMutation } from '@tanstack/react-query';
import { apiFetch } from '@/lib/fetch';
import { useAuthStore } from '@/stores/authStore';
import { useToastStore } from '@/stores/toastStore';
import type { TokenResponse } from '@/lib/types';

interface LoginInput {
  username: string;
  password: string;
}

interface RegisterInput {
  username: string;
  displayName: string;
  email: string;
  password: string;
}

export function useLogin() {
  const setAuth = useAuthStore((s) => s.setAuth);
  return useMutation({
    mutationFn: async (input: LoginInput) => {
      const res = await apiFetch<TokenResponse>('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify(input),
      });
      return res;
    },
    onSuccess: (_data) => {
      // Token doesn't carry userId/username — backend returns just { token }.
      // Decode JWT payload to extract sub/username.
      try {
        const payload = JSON.parse(atob(_data.token.split('.')[1]!));
        setAuth(_data.token, Number(payload.sub), payload.username);
      } catch (e) {
        console.warn('[Auth] JWT decode failed, logging out:', e);
        useAuthStore.getState().logout();
      }
    },
    onError: () => {
      useToastStore.getState().show('Invalid username or password', 'error');
    },
  });
}

export function useRegister() {
  return useMutation({
    mutationFn: (input: RegisterInput) =>
      apiFetch<void>('/api/auth/register', {
        method: 'POST',
        body: JSON.stringify(input),
      }),
    onSuccess: () => {
      useToastStore.getState().show('Account created! You can now sign in.', 'success');
    },
    onError: () => {
      useToastStore
        .getState()
        .show('Registration failed. Username or email may be taken.', 'error');
    },
  });
}
