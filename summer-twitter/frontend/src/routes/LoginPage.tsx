import { useEffect, useActionState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { loginAction, type AuthFormState } from '@/api/auth';
import { useAuthStore } from '@/stores/authStore';

const initialState: AuthFormState = { ok: false };

export default function LoginPage() {
  const [state, formAction, isPending] = useActionState(loginAction, initialState);
  const navigate = useNavigate();
  const token = useAuthStore((s) => s.token);

  // Navigate after a successful submit (state-driven, not during render).
  useEffect(() => {
    if (state.ok) navigate('/', { replace: true });
  }, [state.ok, navigate]);

  if (token) return <Navigate to="/" replace />;

  return (
    <div className="flex min-h-screen items-center justify-center">
      <div className="w-full max-w-sm px-6">
        {/* Logo */}
        <svg viewBox="0 0 24 24" fill="#1d9bf0" className="mx-auto mb-8 h-10 w-10">
          <path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z" />
        </svg>

        <h1 className="mb-6 text-2xl font-bold">Sign in to Summer</h1>

        <form action={formAction} className="flex flex-col gap-4">
          <input
            type="text"
            name="username"
            placeholder="Username"
            defaultValue=""
            className="border-twitter-border focus:border-twitter-blue w-full rounded-md border px-3 py-3 text-lg outline-none"
            required
          />
          <input
            type="password"
            name="password"
            placeholder="Password"
            defaultValue=""
            className="border-twitter-border focus:border-twitter-blue w-full rounded-md border px-3 py-3 text-lg outline-none"
            required
          />

          <button
            type="submit"
            disabled={isPending}
            className="bg-twitter-blue hover:bg-twitter-blue-hover w-full rounded-full py-3 font-bold text-white transition-colors disabled:opacity-50"
          >
            {isPending ? 'Signing in...' : 'Sign in'}
          </button>
        </form>

        <p className="text-twitter-gray mt-6 text-center">
          Don't have an account?{' '}
          <Link to="/register" className="text-twitter-blue hover:underline">
            Sign up
          </Link>
        </p>
      </div>
    </div>
  );
}
