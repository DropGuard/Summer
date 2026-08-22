import { apiFetch } from '@/lib/fetch';
import { useAuthStore } from '@/stores/authStore';
import { useToastStore } from '@/stores/toastStore';
import type { TokenResponse } from '@/lib/types';

/**
 * React 19 form-action state for {@link useActionState}. Actions read the
 * submitted {@link FormData}, perform the request, and write the session into
 * the auth store on success. They never throw: server errors surface via the
 * toast store and a {@code { ok: false }} return, so the component drives
 * navigation from {@code state.ok} inside an effect.
 */
export interface AuthFormState {
  ok: boolean;
}

const str = (v: FormDataEntryValue | null): string =>
  v == null ? '' : typeof v === 'string' ? v : '';

export async function loginAction(
  _prev: AuthFormState,
  formData: FormData,
): Promise<AuthFormState> {
  try {
    const res = await apiFetch<TokenResponse>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({
        username: str(formData.get('username')),
        password: str(formData.get('password')),
      }),
    });
    // Token carries no userId/username — decode the JWT payload for sub/username.
    try {
      const payload = JSON.parse(atob(res.token.split('.')[1]!));
      useAuthStore.getState().setAuth(res.token, Number(payload.sub), payload.username);
    } catch (e) {
      console.warn('[Auth] JWT decode failed, logging out:', e);
      useAuthStore.getState().logout();
      return { ok: false };
    }
    return { ok: true };
  } catch {
    useToastStore.getState().show('Invalid username or password', 'error');
    return { ok: false };
  }
}

export async function registerAction(
  _prev: AuthFormState,
  formData: FormData,
): Promise<AuthFormState> {
  try {
    await apiFetch<void>('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({
        username: str(formData.get('username')),
        displayName: str(formData.get('displayName')),
        email: str(formData.get('email')),
        password: str(formData.get('password')),
      }),
    });
    useToastStore.getState().show('Account created! You can now sign in.', 'success');
    return { ok: true };
  } catch {
    useToastStore
      .getState()
      .show('Registration failed. Username or email may be taken.', 'error');
    return { ok: false };
  }
}
