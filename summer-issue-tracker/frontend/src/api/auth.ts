import { apiFetch } from '@/lib/fetch';
import { useAuthStore } from '@/stores/authStore';
import type { TokenResponse } from '@/lib/types';

/**
 * React 19 form-action state for {@link useActionState}. The action reads the
 * submitted {@link FormData}, performs the request, and writes the session into
 * the auth store on success. It returns a plain state object (never throws) so
 * the component can drive navigation from {@code state.ok} inside an effect.
 */
export interface AuthFormState {
  ok: boolean;
  error?: string;
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
    useAuthStore.getState().setAuth(res.token, res.userId, res.username);
    return { ok: true };
  } catch {
    return { ok: false, error: 'Invalid username or password.' };
  }
}

export async function registerAction(
  _prev: AuthFormState,
  formData: FormData,
): Promise<AuthFormState> {
  try {
    const res = await apiFetch<TokenResponse>('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({
        username: str(formData.get('username')),
        displayName: str(formData.get('displayName')),
        email: str(formData.get('email')),
        password: str(formData.get('password')),
        orgName: str(formData.get('orgName')),
        orgSlug: str(formData.get('orgSlug')),
      }),
    });
    useAuthStore.getState().setAuth(res.token, res.userId, res.username);
    return { ok: true };
  } catch {
    return { ok: false, error: 'Registration failed. Try a different username.' };
  }
}
