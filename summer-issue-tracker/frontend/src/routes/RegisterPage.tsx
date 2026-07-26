import { useEffect, useActionState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { registerAction, type AuthFormState } from '@/api/auth';
import { useAuthStore } from '@/stores/authStore';

const initialState: AuthFormState = { ok: false };

export default function RegisterPage() {
  const [state, formAction, isPending] = useActionState(registerAction, initialState);
  const navigate = useNavigate();

  useEffect(() => {
    if (state.ok) navigate('/', { replace: true });
  }, [state.ok, navigate]);

  useEffect(() => {
    if (useAuthStore.getState().isAuthenticated()) navigate('/', { replace: true });
  }, [navigate]);

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50">
      <form
        action={formAction}
        className="bg-white p-8 rounded-lg shadow w-96 space-y-3"
      >
        <h1 className="text-xl font-bold text-slate-800">Create account</h1>
        <p className="text-xs text-slate-500">
          Registration creates a new organization; you become its admin.
        </p>
        <input
          className="w-full border border-slate-300 rounded px-3 py-2"
          name="username"
          placeholder="Username"
          defaultValue=""
        />
        <input
          className="w-full border border-slate-300 rounded px-3 py-2"
          name="displayName"
          placeholder="Display name"
          defaultValue=""
        />
        <input
          className="w-full border border-slate-300 rounded px-3 py-2"
          name="email"
          placeholder="Email"
          defaultValue=""
        />
        <input
          className="w-full border border-slate-300 rounded px-3 py-2"
          type="password"
          name="password"
          placeholder="Password"
          defaultValue=""
        />
        <input
          className="w-full border border-slate-300 rounded px-3 py-2"
          name="orgName"
          placeholder="Organization name"
          defaultValue=""
        />
        <input
          className="w-full border border-slate-300 rounded px-3 py-2"
          name="orgSlug"
          placeholder="Organization slug"
          defaultValue=""
        />
        <button
          className="w-full bg-slate-800 text-white rounded py-2 disabled:opacity-50"
          type="submit"
          disabled={isPending}
        >
          {isPending ? 'Creating…' : 'Register'}
        </button>
        {state.error && (
          <p className="text-red-600 text-sm">{state.error}</p>
        )}
        <p className="text-sm text-slate-500">
          Have an account?{' '}
          <Link to="/login" className="text-blue-600">
            Sign in
          </Link>
        </p>
      </form>
    </div>
  );
}
