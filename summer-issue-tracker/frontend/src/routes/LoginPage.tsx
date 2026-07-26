import { useEffect, useActionState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { loginAction, type AuthFormState } from '@/api/auth';
import { useAuthStore } from '@/stores/authStore';

const initialState: AuthFormState = { ok: false };

export default function LoginPage() {
  const [state, formAction, isPending] = useActionState(loginAction, initialState);
  const navigate = useNavigate();

  // Redirect after a successful submit (state-driven, not during render).
  useEffect(() => {
    if (state.ok) navigate('/', { replace: true });
  }, [state.ok, navigate]);

  // Already signed in? Bounce to the board.
  useEffect(() => {
    if (useAuthStore.getState().isAuthenticated()) navigate('/', { replace: true });
  }, [navigate]);

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50">
      <form
        action={formAction}
        className="bg-white p-8 rounded-lg shadow w-96 space-y-4"
      >
        <h1 className="text-xl font-bold text-slate-800">Sign in</h1>
        <input
          className="w-full border border-slate-300 rounded px-3 py-2"
          name="username"
          placeholder="Username"
          defaultValue=""
        />
        <input
          className="w-full border border-slate-300 rounded px-3 py-2"
          type="password"
          name="password"
          placeholder="Password"
          defaultValue=""
        />
        <button
          className="w-full bg-slate-800 text-white rounded py-2 disabled:opacity-50"
          type="submit"
          disabled={isPending}
        >
          {isPending ? 'Signing in…' : 'Sign in'}
        </button>
        {state.error && (
          <p className="text-red-600 text-sm">{state.error}</p>
        )}
        <p className="text-sm text-slate-500">
          No account?{' '}
          <Link to="/register" className="text-blue-600">
            Register
          </Link>
        </p>
      </form>
    </div>
  );
}
