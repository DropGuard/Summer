import { useMutation } from '@tanstack/react-query';
import { apiFetch } from '@/lib/fetch';
import { useAuthStore } from '@/stores/authStore';
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
  orgName: string;
  orgSlug: string;
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
    onSuccess: (data) => {
      setAuth(data.token, data.userId, data.username);
    },
  });
}

export function useRegister() {
  return useMutation({
    mutationFn: (input: RegisterInput) =>
      apiFetch<TokenResponse>('/api/auth/register', {
        method: 'POST',
        body: JSON.stringify(input),
      }),
    onSuccess: (data) => {
      useAuthStore.getState().setAuth(data.token, data.userId, data.username);
    },
  });
}
